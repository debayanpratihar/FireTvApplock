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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fliptofocus.domain.model.SessionStatus
import com.fliptofocus.domain.repository.AppConfigRepository
import com.fliptofocus.domain.repository.FocusSessionRepository
import com.fliptofocus.ui.theme.FlipToFocusTheme
import com.fliptofocus.ui.theme.IosBackground
import com.fliptofocus.util.PinSecurity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Full-screen lock shown on top of a locked app (launched by the accessibility service) and also
 * used for the in-app "Preview lock" demo (launched with [EXTRA_PREVIEW]).
 *
 * On a real lock, a correct credential grants the app in [LockController] and logs UNLOCKED, then
 * finishes so the app appears; backing out logs DENIED and returns to the device home. In preview
 * mode nothing is granted or logged - it just shows the success confirmation so a parent (or an
 * app reviewer) can verify the lock in-app without needing any special permission.
 */
@AndroidEntryPoint
class LockActivity : ComponentActivity() {

    @Inject lateinit var appConfigRepository: AppConfigRepository
    @Inject lateinit var focusSessionRepository: FocusSessionRepository
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
                val scope = rememberCoroutineScope()

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
                            onUnlocked = {
                                if (preview) {
                                    showSuccess = true
                                } else {
                                    val pkg = targetPackage
                                    if (pkg.isNotBlank()) {
                                        lockController.grantForeground(pkg)
                                        scope.launch {
                                            runCatching {
                                                focusSessionRepository.logEvent(pkg, SessionStatus.UNLOCKED)
                                            }
                                        }
                                    }
                                    finish()
                                }
                            },
                            onGoBack = {
                                if (!preview) {
                                    val pkg = targetPackage
                                    if (pkg.isNotBlank()) {
                                        scope.launch {
                                            runCatching {
                                                focusSessionRepository.logEvent(pkg, SessionStatus.DENIED)
                                            }
                                        }
                                    }
                                    goHome()
                                }
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
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
        if (pkg.isNotBlank()) lockController.grantForeground(pkg)
        finish()
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
