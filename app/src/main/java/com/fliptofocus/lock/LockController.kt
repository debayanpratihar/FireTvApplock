package com.fliptofocus.lock

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for which locked apps are currently UNLOCKED, shared between the
 * [com.fliptofocus.service.FocusAccessibilityService] (which decides whether to show the lock) and
 * [LockActivity] (which grants an unlock). Also holds the global brute-force cooldown.
 *
 * Grant model:
 *  - "keep unlocked for" = 0  -> a foreground grant: valid while the app is on screen. When the app
 *    leaves it gets a short RELOCK grace instead of dropping instantly, so a soft keyboard, a
 *    dialog, an in-app browser, or the brief window flip when pressing Back does NOT re-lock; the
 *    grant is re-armed the moment the app returns. Only a genuine multi-second switch-away re-locks.
 *  - "keep unlocked for" > 0  -> a timed window (e.g. 30 min) that survives app switches until it
 *    expires, then re-locks.
 */
@Singleton
class LockController @Inject constructor() {

    private data class Grant(val timed: Boolean, val expiryElapsed: Long)

    private val grants = ConcurrentHashMap<String, Grant>()

    @Volatile private var failedAttempts = 0
    @Volatile private var cooldownUntil = 0L

    /** True while [pkg] may be opened without the lock screen. */
    fun isUnlocked(pkg: String): Boolean {
        val g = grants[pkg] ?: return false
        if (SystemClock.elapsedRealtime() <= g.expiryElapsed) return true
        grants.remove(pkg)
        return false
    }

    /**
     * Grants [pkg] after a correct unlock. [durationMillis] <= 0 grants only while the app stays in
     * the foreground; a positive value grants a timed window that persists across app switches.
     */
    fun grant(pkg: String, durationMillis: Long) {
        grants[pkg] = if (durationMillis <= 0L) {
            Grant(timed = false, expiryElapsed = Long.MAX_VALUE)
        } else {
            Grant(timed = true, expiryElapsed = SystemClock.elapsedRealtime() + durationMillis)
        }
    }

    /**
     * Called when the locked app [pkg] is in the foreground. If it is a foreground grant that was
     * put into the relock grace by [onLeftApp], re-arm it (the user came back in time) or drop it
     * (the grace lapsed). No-op for a still-armed or timed grant.
     */
    fun onEnterApp(pkg: String) {
        val g = grants[pkg] ?: return
        if (g.timed || g.expiryElapsed == Long.MAX_VALUE) return
        if (SystemClock.elapsedRealtime() <= g.expiryElapsed) {
            grants[pkg] = Grant(timed = false, expiryElapsed = Long.MAX_VALUE)
        } else {
            grants.remove(pkg)
        }
    }

    /**
     * Called when the foreground moves away from [pkg]. A foreground grant is given a short relock
     * grace so transient windows / quick returns don't re-lock; timed windows keep counting.
     */
    fun onLeftApp(pkg: String) {
        val g = grants[pkg] ?: return
        if (!g.timed) {
            grants[pkg] = Grant(timed = false, expiryElapsed = SystemClock.elapsedRealtime() + RELOCK_GRACE_MS)
        }
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
        // Grace after leaving a foreground-granted app before it re-locks. Long enough to absorb
        // the soft keyboard, dialogs, in-app browsers, and the Back-button window flip.
        const val RELOCK_GRACE_MS = 5_000L
        const val MAX_ATTEMPTS = 5
        const val COOLDOWN_MS = 30_000L
    }
}
