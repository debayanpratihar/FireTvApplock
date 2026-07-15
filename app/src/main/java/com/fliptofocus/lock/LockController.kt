package com.fliptofocus.lock

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for which locked apps are currently UNLOCKED, shared between the
 * [com.fliptofocus.service.FocusAccessibilityService] (which decides whether to show the lock) and
 * [LockActivity] (which grants an unlock after the correct PIN / sequence). Also holds the global
 * brute-force cooldown so mashing wrong PINs can't be used to wear the lock down.
 *
 * Grant model:
 *  - duration 0  -> valid only while the app stays in the foreground (leaving re-locks it).
 *  - duration >0 -> a timed access window (e.g. "unlocked for 30 min"); it survives leaving and
 *    returning until it expires. This backs the Settings "keep unlocked for" slider.
 */
@Singleton
class LockController @Inject constructor() {

    // packageName -> grant expiry in SystemClock.elapsedRealtime() millis, or [WHILE_FOREGROUND].
    private val grants = ConcurrentHashMap<String, Long>()

    @Volatile private var failedAttempts = 0
    @Volatile private var cooldownUntil = 0L

    /** True while [pkg] may be opened without the lock screen. */
    fun isUnlocked(pkg: String): Boolean {
        val expiry = grants[pkg] ?: return false
        if (expiry == WHILE_FOREGROUND) return true
        if (SystemClock.elapsedRealtime() <= expiry) return true
        grants.remove(pkg)
        return false
    }

    /**
     * Grants [pkg] after a correct unlock. [durationMillis] <= 0 grants only while the app stays in
     * the foreground; a positive value grants a timed window that persists across app switches.
     */
    fun grant(pkg: String, durationMillis: Long) {
        grants[pkg] = if (durationMillis <= 0L) {
            WHILE_FOREGROUND
        } else {
            SystemClock.elapsedRealtime() + durationMillis
        }
    }

    /**
     * Called when the foreground moves away from [pkg]. A "while foreground" grant is dropped so the
     * next open re-locks; a timed window is left to keep counting.
     */
    fun onLeftApp(pkg: String) {
        if (grants[pkg] == WHILE_FOREGROUND) grants.remove(pkg)
    }

    /** Drops every grant (e.g. when locking is turned off). */
    fun clearAll() {
        grants.clear()
    }

    // --- Brute-force cooldown -----------------------------------------------------------------

    /** Records a wrong attempt; after [MAX_ATTEMPTS] in a row, starts a [COOLDOWN_MS] lockout. */
    fun registerFailedAttempt() {
        failedAttempts++
        if (failedAttempts >= MAX_ATTEMPTS) {
            cooldownUntil = SystemClock.elapsedRealtime() + COOLDOWN_MS
            failedAttempts = 0
        }
    }

    /** Millis remaining in the current lockout, or 0 if input is allowed. */
    fun cooldownRemainingMillis(): Long =
        (cooldownUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    /** Clears the attempt counter and any lockout (called on a successful unlock). */
    fun resetAttempts() {
        failedAttempts = 0
        cooldownUntil = 0L
    }

    private companion object {
        const val WHILE_FOREGROUND = Long.MAX_VALUE
        const val MAX_ATTEMPTS = 5
        const val COOLDOWN_MS = 30_000L
    }
}
