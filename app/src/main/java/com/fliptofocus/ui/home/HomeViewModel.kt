package com.fliptofocus.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fliptofocus.domain.model.SessionStatus
import com.fliptofocus.domain.repository.AppConfigRepository
import com.fliptofocus.domain.repository.BlockedAppRepository
import com.fliptofocus.domain.repository.FocusSessionRepository
import com.fliptofocus.lock.LockCredentialsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** One entry in the parent-visible access log, ready to render. */
data class AccessLogRow(
    val id: Long,
    val label: String,
    val status: SessionStatus,
    val timestamp: Long
)

data class HomeUiState(
    val isLockingEnabled: Boolean = true,
    val lockedAppCount: Int = 0,
    val unlockedToday: Int = 0,
    val recentEvents: List<AccessLogRow> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appConfigRepository: AppConfigRepository,
    private val blockedAppRepository: BlockedAppRepository,
    private val focusSessionRepository: FocusSessionRepository,
    private val credentials: LockCredentialsManager
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        appConfigRepository.observeConfig(),
        blockedAppRepository.observeBlockedApps(),
        focusSessionRepository.observeSessions()
    ) { config, apps, sessions ->
        val labels = apps.associate { it.packageName to it.appLabel }
        val enabledCount = apps.count { it.isEnabled }
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
        val unlockedToday = sessions.count {
            it.status == SessionStatus.UNLOCKED &&
                Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() == today
        }

        HomeUiState(
            isLockingEnabled = config.isLockingEnabled,
            lockedAppCount = enabledCount,
            unlockedToday = unlockedToday,
            recentEvents = sessions.take(RECENT_LIMIT).map { s ->
                AccessLogRow(
                    id = s.id,
                    label = labels[s.triggeringPackage] ?: s.triggeringPackage,
                    status = s.status,
                    timestamp = s.timestamp
                )
            }
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

    fun deleteEvent(id: Long) {
        viewModelScope.launch { runCatching { focusSessionRepository.deleteSession(id) } }
    }

    fun clearHistory() {
        viewModelScope.launch { runCatching { focusSessionRepository.clearHistory() } }
    }

    private companion object {
        const val RECENT_LIMIT = 30
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
