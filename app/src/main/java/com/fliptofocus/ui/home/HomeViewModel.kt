package com.fliptofocus.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fliptofocus.domain.repository.AppConfigRepository
import com.fliptofocus.domain.repository.BlockedAppRepository
import com.fliptofocus.lock.LockCredentialsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLockingEnabled: Boolean = true,
    val lockedAppCount: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appConfigRepository: AppConfigRepository,
    private val blockedAppRepository: BlockedAppRepository,
    private val credentials: LockCredentialsManager
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        appConfigRepository.observeConfig(),
        blockedAppRepository.observeBlockedApps()
    ) { config, apps ->
        HomeUiState(
            isLockingEnabled = config.isLockingEnabled,
            lockedAppCount = apps.count { it.isEnabled }
        )
    }
        .catch { emit(HomeUiState()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HomeUiState()
        )

    fun setLockingEnabled(enabled: Boolean) {
        viewModelScope.launch { runCatching { credentials.setLockingEnabled(enabled) } }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
