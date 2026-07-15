package com.fliptofocus.lock

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for which locked apps are currently UNLOCKED, shared between the
 * [com.fliptofocus.service.FocusAccessibilityService] (which decides whether to show the lock) and
 * [LockActivity] (which grants an unlock after the correct PIN / sequence).
 *
 * Model: after a successful unlock an app is granted "while in the foreground". When the user then
 * leaves that app, the grant either drops immediately (grace = 0) or lingers for a short grace
 * period so briefly bouncing away and back does not demand the PIN again. Reopening after the grant
 * has cleared re-locks the app.
 */
@Singleton
class LockController @Inject constructor() {

    // packageName -> grant expiry in SystemClock.elapsedRealtime() millis, or [WHILE_FOREGROUND].
    private val grants = ConcurrentHashMap<String, Long>()

    /** True while [pkg] may be opened without the lock screen. */
    fun isUnlocked(pkg: String): Boolean {
        val expiry = grants[pkg] ?: return false
        if (expiry == WHILE_FOREGROUND) return true
        if (SystemClock.elapsedRealtime() <= expiry) return true
        grants.remove(pkg)
        return false
    }

    /** Grants [pkg] after a correct unlock; valid while it stays in the foreground. */
    fun grantForeground(pkg: String) {
        grants[pkg] = WHILE_FOREGROUND
    }

    /**
     * Called when the foreground moves away from [pkg]. With no grace the grant is dropped so the
     * next open re-locks; with a grace period the grant is converted to a short-lived expiry.
     */
    fun onLeftApp(pkg: String, graceMillis: Long) {
        val expiry = grants[pkg] ?: return
        if (expiry != WHILE_FOREGROUND) return
        if (graceMillis <= 0L) {
            grants.remove(pkg)
        } else {
            grants[pkg] = SystemClock.elapsedRealtime() + graceMillis
        }
    }

    /** Drops every grant (e.g. when locking is turned off). */
    fun clearAll() {
        grants.clear()
    }

    private companion object {
        const val WHILE_FOREGROUND = Long.MAX_VALUE
    }
}
