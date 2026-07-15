package com.fliptofocus.util

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager

/**
 * Device-type detection so the UI can adapt (a Fire TV is driven only by a D-pad remote and has no
 * touchscreen or motion sensors, whereas a Fire tablet / phone is touch-first).
 */
object DeviceUtils {

    private const val FEATURE_FIRE_TV = "amazon.hardware.fire_tv"

    /** True on Fire TV / Android TV (leanback, TV UI mode, or a device with no touchscreen). */
    fun isTelevision(context: Context): Boolean {
        val uiMode = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)
            ?.currentModeType
        if (uiMode == Configuration.UI_MODE_TYPE_TELEVISION) return true

        val pm = context.packageManager
        return pm.hasSystemFeature(FEATURE_FIRE_TV) ||
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }
}
