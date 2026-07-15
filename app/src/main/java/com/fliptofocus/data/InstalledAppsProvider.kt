package com.fliptofocus.data

import android.content.Context
import android.content.Intent
import com.fliptofocus.domain.model.BlockedApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Enumerates the launchable apps installed on the device.
 *
 * Uses [android.content.pm.PackageManager.queryIntentActivities] with BOTH the phone/tablet
 * MAIN/LAUNCHER intent and the Fire TV / Android TV MAIN/LEANBACK_LAUNCHER intent (matching the
 * manifest <queries> element) so the picker shows the apps a user can actually open on the current
 * device - this is the compliant alternative to the QUERY_ALL_PACKAGES permission, which this app
 * deliberately does not use.
 *
 * The query and label loading run on [Dispatchers.IO] because resolving and loading labels for
 * every launcher entry can be slow on cold caches.
 */
class InstalledAppsProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun getLaunchableApps(): List<BlockedApp> = withContext(Dispatchers.IO) {
        // Never throw: any PackageManager quirk falls back to an empty list so the UI degrades
        // gracefully (shows "no apps") instead of crashing the app.
        runCatching {
            val pm = context.packageManager
            val ownPackage = context.packageName

            val categories = listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER)
            categories
                .asSequence()
                .flatMap { category ->
                    val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
                    runCatching { pm.queryIntentActivities(intent, 0) }
                        .getOrDefault(emptyList())
                        .asSequence()
                }
                .mapNotNull { resolveInfo ->
                    val pkg = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == ownPackage) return@mapNotNull null
                    val label = runCatching {
                        resolveInfo.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                    }.getOrNull() ?: pkg
                    BlockedApp(packageName = pkg, appLabel = label, isEnabled = true)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.appLabel.lowercase() }
                .toList()
        }.getOrElse { emptyList() }
    }
}
