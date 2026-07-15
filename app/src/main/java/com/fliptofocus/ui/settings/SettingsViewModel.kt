package com.fliptofocus.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fliptofocus.data.InstalledAppsProvider
import com.fliptofocus.domain.repository.AppConfigRepository
import com.fliptofocus.domain.repository.BlockedAppRepository
import com.fliptofocus.lock.LockCredentialsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isLockingEnabled: Boolean = true,
    val isComboSet: Boolean = false,
    val isRecoverySet: Boolean = false,
    val relockGraceSeconds: Int = 0
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val credentials: LockCredentialsManager,
    private val installedAppsProvider: InstalledAppsProvider,
    private val blockedAppRepository: BlockedAppRepository,
    appConfigRepository: AppConfigRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = appConfigRepository.observeConfig()
        .map {
            SettingsUiState(
                isLockingEnabled = it.isLockingEnabled,
                isComboSet = it.isComboSet,
                isRecoverySet = it.isRecoverySet,
                relockGraceSeconds = it.relockGraceSeconds
            )
        }
        .catch { emit(SettingsUiState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    // A freshly generated recovery code to show ONCE, or null.
    private val _recoveryCode = MutableStateFlow<String?>(null)
    val recoveryCode: StateFlow<String?> = _recoveryCode.asStateFlow()

    fun changePin(pin: String, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { credentials.setPin(pin) }
            onDone()
        }
    }

    fun setCombo(sequence: List<Int>, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { credentials.setCombo(sequence) }
            onDone()
        }
    }

    fun clearCombo() {
        viewModelScope.launch { runCatching { credentials.clearCombo() } }
    }

    fun regenerateRecovery() {
        viewModelScope.launch {
            runCatching { credentials.regenerateRecovery() }.getOrNull()?.let { _recoveryCode.value = it }
        }
    }

    fun consumeRecoveryCode() {
        _recoveryCode.value = null
    }

    fun setLockingEnabled(enabled: Boolean) {
        viewModelScope.launch { runCatching { credentials.setLockingEnabled(enabled) } }
    }

    fun setRelockGrace(seconds: Int) {
        viewModelScope.launch { runCatching { credentials.setRelockGraceSeconds(seconds) } }
    }

    /** Bedtime lockdown: lock every installed app at once. */
    fun lockAllApps() {
        viewModelScope.launch {
            runCatching {
                installedAppsProvider.getLaunchableApps().forEach {
                    blockedAppRepository.addBlockedApp(it.copy(isEnabled = true))
                }
            }
        }
    }

    /** Unlock everything (disables the lock for all apps, keeping them in the list). */
    fun unlockAllApps() {
        viewModelScope.launch { runCatching { blockedAppRepository.setAllEnabled(false) } }
    }
}
