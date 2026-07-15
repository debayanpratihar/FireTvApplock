package com.fliptofocus.ui.navigation

import android.app.Activity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fliptofocus.domain.model.AppConfig
import com.fliptofocus.domain.repository.AppConfigRepository
import com.fliptofocus.lock.Combo
import com.fliptofocus.lock.LockScreen
import com.fliptofocus.ui.blocklist.BlocklistScreen
import com.fliptofocus.ui.home.HomeScreen
import com.fliptofocus.ui.onboarding.OnboardingScreen
import com.fliptofocus.ui.settings.SettingsScreen
import com.fliptofocus.ui.theme.IosBackground
import com.fliptofocus.util.PinSecurity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Canonical navigation route names. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val BLOCKLIST = "blocklist"
    const val SETTINGS = "settings"
}

/** Exposes the on-device config (null while still loading) to the root. */
@HiltViewModel
class RootViewModel @Inject constructor(
    appConfigRepository: AppConfigRepository
) : ViewModel() {
    val config: StateFlow<AppConfig?> = appConfigRepository.observeConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * Top-level navigation graph.
 *
 * Start destination depends only on whether a PIN exists (no PIN -> setup, else -> home) - never on
 * a special-access permission, so the app always reaches a usable screen. When the app launched
 * already protected, an in-app PIN gate guards all management screens so a child cannot open
 * KidLock and disable it; the gate re-arms whenever the app goes to the background.
 */
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    rootViewModel: RootViewModel = hiltViewModel()
) {
    val cfg by rootViewModel.config.collectAsState()

    var startDestination by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(cfg) {
        if (startDestination == null && cfg != null) {
            startDestination = if (cfg!!.isPinSet) Routes.HOME else Routes.ONBOARDING
        }
    }

    val start = startDestination
    val config = cfg
    if (start == null || config == null) {
        // Brief splash while the on-device config loads (window stays black, no white flash).
        Box(modifier = Modifier.fillMaxSize().background(IosBackground))
        return
    }

    // In-app PIN gate (only when the app launched already protected).
    if (start == Routes.HOME) {
        var appUnlocked by remember { mutableStateOf(false) }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) appUnlocked = false
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        if (!appUnlocked) {
            val context = LocalContext.current
            LockScreen(
                appLabel = "KidLock TV",
                hasCombo = config.isComboSet,
                hasRecovery = config.isRecoverySet,
                onVerifyPin = { PinSecurity.verify(it, config.pinSalt, config.pinHash) },
                onVerifySequence = { seq ->
                    config.isComboSet &&
                        PinSecurity.verify(Combo.encode(seq), config.comboSalt, config.comboHash)
                },
                onVerifyRecovery = {
                    PinSecurity.verify(
                        PinSecurity.normalizeRecovery(it),
                        config.recoverySalt,
                        config.recoveryHash
                    )
                },
                onUnlocked = { appUnlocked = true },
                onGoBack = { (context as? Activity)?.moveTaskToBack(true) }
            )
            return
        }
    }

    NavHost(
        navController = navController,
        startDestination = start,
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(180)) }
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Routes.HOME) { HomeScreen(navController = navController) }
        composable(Routes.BLOCKLIST) { BlocklistScreen(navController = navController) }
        composable(Routes.SETTINGS) { SettingsScreen(navController = navController) }
    }
}
