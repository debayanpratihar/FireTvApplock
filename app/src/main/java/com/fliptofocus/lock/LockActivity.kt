package com.fliptofocus.lock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fliptofocus.domain.repository.AppConfigRepository
import com.fliptofocus.ui.theme.FlipToFocusTheme
import com.fliptofocus.ui.theme.IosBackground
import com.fliptofocus.util.PinSecurity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Full-screen lock shown on top of a locked app (launched by the accessibility service) and also
 * used for the in-app "Preview lock" demo (launched with [EXTRA_PREVIEW]).
 *
 * On a real lock, a correct credential grants the app in [LockController] and relaunches it so it
 * comes to the front; backing out returns to the device home. In preview mode nothing is granted -
 * it just shows the success confirmation so a parent (or an app reviewer) can verify the lock
 * in-app without needing any special permission. Nothing is ever logged, recorded, or transmitted.
 */
@AndroidEntryPoint
class LockActivity : ComponentActivity() {

    @Inject lateinit var appConfigRepository: AppConfigRepository
    @Inject lateinit var lockController: LockController

    private var targetPackage by mutableStateOf("")
    private var appLabel by mutableStateOf("")
    private var preview by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readIntent(intent)

        setContent {
            FlipToFocusTheme {
                val configFlow = remember { appConfigRepository.observeConfig() }
                val config by configFlow.collectAsState(initial = null)
                var showSuccess by remember { mutableStateOf(false) }

                when {
                    showSuccess -> {
                        UnlockSuccess()
                        LaunchedEffect(Unit) {
                            delay(1400)
                            finish()
                        }
                    }

                    config == null -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(IosBackground)
                    )

                    // Fail-open: if no PIN has been set, never trap the user behind a lock.
                    !config!!.isPinSet -> LaunchedEffect(Unit) {
                        if (!preview) grantAndFinish(targetPackage) else finish()
                    }

                    else -> {
                        val cfg = config!!
                        LockScreen(
                            appLabel = appLabel.ifBlank { "this app" },
                            hasCombo = cfg.isComboSet,
                            hasRecovery = cfg.isRecoverySet,
                            onVerifyPin = { entered ->
                                PinSecurity.verify(entered, cfg.pinSalt, cfg.pinHash)
                            },
                            onVerifySequence = { seq ->
                                cfg.isComboSet &&
                                    PinSecurity.verify(Combo.encode(seq), cfg.comboSalt, cfg.comboHash)
                            },
                            onVerifyRecovery = { entered ->
                                PinSecurity.verify(
                                    PinSecurity.normalizeRecovery(entered),
                                    cfg.recoverySalt,
                                    cfg.recoveryHash
                                )
                            },
                            onWrongAttempt = { lockController.registerFailedAttempt() },
                            cooldownRemainingMillis = { lockController.cooldownRemainingMillis() },
                            onUnlocked = {
                                lockController.resetAttempts()
                                if (preview) {
                                    showSuccess = true
                                } else {
                                    val pkg = targetPackage
                                    if (pkg.isNotBlank()) {
                                        lockController.grant(pkg, cfg.relockGraceSeconds * 1000L)
                                        launchLockedApp(pkg)
                                    }
                                    finish()
                                }
                            },
                            onGoBack = {
                                if (!preview) goHome()
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        preview = intent?.getBooleanExtra(EXTRA_PREVIEW, false) ?: false
        targetPackage = intent?.getStringExtra(EXTRA_PACKAGE).orEmpty()
        appLabel = intent?.getStringExtra(EXTRA_APP_LABEL).orEmpty()
    }

    private fun grantAndFinish(pkg: String) {
        if (pkg.isNotBlank()) {
            lockController.grant(pkg, 0L)
            launchLockedApp(pkg)
        }
        finish()
    }

    /**
     * Brings the just-unlocked app to the foreground explicitly. Relying on the back stack is
     * unreliable when the lock was launched from a service, so we relaunch the app's own entry point
     * (normal launcher, or the Fire TV leanback launcher). The grant prevents an immediate re-lock.
     */
    private fun launchLockedApp(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
            ?: packageManager.getLeanbackLaunchIntentForPackage(pkg)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(intent) }
        }
    }

    private fun goHome() {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_APP_LABEL = "extra_app_label"
        const val EXTRA_PREVIEW = "extra_preview"
    }
}
