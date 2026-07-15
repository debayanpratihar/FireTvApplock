package com.fliptofocus.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.fliptofocus.domain.model.AppConfig
import com.fliptofocus.domain.repository.AppConfigRepository
import com.fliptofocus.domain.repository.BlockedAppRepository
import com.fliptofocus.lock.LockActivity
import com.fliptofocus.lock.LockController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground-app detector for the parental lock, implemented as an [AccessibilityService].
 *
 * When an app the parent chose to lock comes to the foreground and is not currently unlocked, the
 * service brings up [LockActivity] on top of it. Detection is BOTH event-driven and self-healing:
 *  - [onAccessibilityEvent] reacts immediately to window-state changes.
 *  - A short polling loop re-reads the current foreground package via [rootInActiveWindow] so a
 *    locked app that was already open, or re-entered from Recents, is still caught.
 *
 * Privacy: only the foreground package NAME is ever used; on-screen content is never inspected or
 * stored. The lock is only ever shown over an app the user explicitly added to their lock list -
 * never over the launcher/home or over KidLock itself - so the user can always leave.
 */
@AndroidEntryPoint
class FocusAccessibilityService : AccessibilityService() {

    @Inject lateinit var blockedAppRepository: BlockedAppRepository
    @Inject lateinit var appConfigRepository: AppConfigRepository
    @Inject lateinit var lockController: LockController

    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(job + Dispatchers.Main.immediate)

    @Volatile private var cachedConfig: AppConfig = AppConfig()
    @Volatile private var lockedPackages: Set<String> = emptySet()
    @Volatile private var cachedLabels: Map<String, String> = emptyMap()
    @Volatile private var homePackages: Set<String> = emptySet()

    @Volatile private var lastForegroundPkg: String? = null

    // Debounce so we do not fire startActivity() repeatedly in the brief window before the lock
    // screen actually reaches the foreground.
    private var lastLaunchPkg: String? = null
    private var lastLaunchAt: Long = 0L

    private var pollJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        runCatching { applyServiceInfo() }
        runCatching { resolveHomePackages() }
        observeRepositories()
        startForegroundPolling()
    }

    private fun applyServiceInfo() {
        val info = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    private fun resolveHomePackages() {
        val pm = packageManager ?: return
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        homePackages = runCatching {
            pm.queryIntentActivities(homeIntent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        }.getOrDefault(emptySet())
    }

    private fun observeRepositories() {
        appConfigRepository.observeConfig()
            .onEach { config ->
                cachedConfig = config
                if (!config.isLockingEnabled) lockController.clearAll()
                evaluate(lastForegroundPkg)
            }
            .catch { }
            .launchIn(serviceScope)

        blockedAppRepository.observeBlockedApps()
            .onEach { apps ->
                cachedLabels = apps.associate { it.packageName to it.appLabel }
                lockedPackages = apps.asSequence()
                    .filter { it.isEnabled }
                    .map { it.packageName }
                    .toSet()
                evaluate(lastForegroundPkg)
            }
            .catch { }
            .launchIn(serviceScope)
    }

    private fun startForegroundPolling() {
        pollJob?.cancel()
        pollJob = serviceScope.launch {
            while (isActive) {
                delay(POLL_MS)
                val pkg = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
                    ?: lastForegroundPkg
                if (pkg != null) runCatching { evaluate(pkg) }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val pkg = event.packageName?.toString()
                if (!pkg.isNullOrBlank()) evaluate(pkg)
            }
        } catch (t: Throwable) {
            // A single bad event must never take down the detector.
        }
    }

    /**
     * Central self-healing check. Safe to call repeatedly from events and the poller.
     */
    private fun evaluate(pkg: String?) {
        if (pkg.isNullOrBlank()) return
        // Ignore ourselves (including the lock screen) so we never lock KidLock or loop.
        if (pkg == packageName) {
            lastForegroundPkg = pkg
            return
        }

        // Leaving the previous app drops a "while foreground" grant (timed windows keep counting).
        val previous = lastForegroundPkg
        if (previous != null && previous != pkg) {
            lockController.onLeftApp(previous)
        }
        lastForegroundPkg = pkg

        if (pkg in homePackages) return
        if (!cachedConfig.isLockingEnabled) return
        if (pkg !in lockedPackages) return

        // Re-arm a grant if the user returned within the relock grace (absorbs transient windows and
        // the Back-button flip); this is what stops the lock popping up during normal in-app use.
        lockController.onEnterApp(pkg)
        if (lockController.isUnlocked(pkg)) return

        launchLock(pkg)
    }

    private fun launchLock(pkg: String) {
        val now = SystemClock.elapsedRealtime()
        if (pkg == lastLaunchPkg && now - lastLaunchAt < RELAUNCH_DEBOUNCE_MS) return
        lastLaunchPkg = pkg
        lastLaunchAt = now

        val intent = Intent(this, LockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(LockActivity.EXTRA_PACKAGE, pkg)
            putExtra(LockActivity.EXTRA_APP_LABEL, cachedLabels[pkg] ?: pkg)
        }
        runCatching { startActivity(intent) }
    }

    override fun onInterrupt() {
        // Required override; nothing to interrupt.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        pollJob?.cancel()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        pollJob?.cancel()
        runCatching { job.cancel() }
        super.onDestroy()
    }

    private companion object {
        const val POLL_MS = 350L
        const val RELAUNCH_DEBOUNCE_MS = 2_000L
    }
}
