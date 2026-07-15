package com.fliptofocus.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fliptofocus.lock.LockCredentialsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Steps of the KidLock TV setup flow. Only creating a PIN is required to use the app; enabling the
 * accessibility service (for automatic locking) is optional and explicitly skippable, so the app is
 * never gated behind a permission the user might not be able to grant.
 */
enum class OnbStep { WELCOME, PIN, RECOVERY, ACCESSIBILITY, DONE }

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val credentials: LockCredentialsManager
) : ViewModel() {

    private val order = OnbStep.entries

    private val _step = MutableStateFlow(OnbStep.WELCOME)
    val step: StateFlow<OnbStep> = _step.asStateFlow()

    private val _recoveryCode = MutableStateFlow<String?>(null)
    val recoveryCode: StateFlow<String?> = _recoveryCode.asStateFlow()

    fun next() {
        val i = order.indexOf(_step.value)
        if (i < order.lastIndex) _step.value = order[i + 1]
    }

    fun back() {
        val i = order.indexOf(_step.value)
        if (i > 0) _step.value = order[i - 1]
    }

    fun goTo(step: OnbStep) {
        _step.value = step
    }

    /** Persists the chosen PIN, keeps the one-time recovery code, then advances to the recovery step. */
    fun createPinAndContinue(pin: String) {
        viewModelScope.launch {
            runCatching {
                credentials.setPin(pin)?.let { _recoveryCode.value = it }
            }
            next()
        }
    }
}
