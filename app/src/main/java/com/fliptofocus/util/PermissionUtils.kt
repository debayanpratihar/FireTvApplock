package com.fliptofocus.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import com.fliptofocus.service.FocusAccessibilityService

/**
 * Stateless helpers for the ONE special-access permission KidLock TV uses: the Accessibility
 * service that detects the foreground app so the lock screen can be shown.
 *
 * KidLock deliberately does NOT use "Draw over other apps" (SYSTEM_ALERT_WINDOW). On Fire TV that
 * permission has no settings screen and cannot be granted with a remote, so relying on it would
 * make the app unusable there. The accessibility permission, by contrast, is reachable from
 * Settings > Accessibility on Fire TV, Fire tablets, and phones alike.
 *
 * Every OS call is wrapped defensively so a query can never crash the caller.
 */
object PermissionUtils {

    /** True when KidLock's [FocusAccessibilityService] is enabled by the user in system settings. */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, FocusAccessibilityService::class.java)
        val enabled = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        }.getOrNull()
        if (enabled.isNullOrEmpty()) return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            val component = ComponentName.unflattenFromString(splitter.next())
            if (component != null && component == expected) return true
        }
        // Fallback for OEM formatting differences.
        return enabled.contains(expected.flattenToString(), ignoreCase = true) ||
            enabled.contains(expected.flattenToShortString(), ignoreCase = true)
    }

    /** Deep-links to the system Accessibility settings list. */
    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
}
