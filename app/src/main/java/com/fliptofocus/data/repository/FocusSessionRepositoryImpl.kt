package com.fliptofocus.data.repository

import com.fliptofocus.data.local.FocusSessionDao
import com.fliptofocus.data.local.FocusSessionEntity
import com.fliptofocus.data.local.toDomain
import com.fliptofocus.domain.model.FocusSession
import com.fliptofocus.domain.model.SessionStatus
import com.fliptofocus.domain.repository.FocusSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FocusSessionRepositoryImpl @Inject constructor(
    private val dao: FocusSessionDao
) : FocusSessionRepository {

    override suspend fun logEvent(
        triggeringPackage: String,
        status: SessionStatus
    ): Long =
        dao.insert(
            FocusSessionEntity(
                startTimestamp = System.currentTimeMillis(),
                endTimestamp = null,
                challengeDurationMillis = 0L,
                triggeringPackage = triggeringPackage,
                status = status.name
            )
        )

    override fun observeSessions(): Flow<List<FocusSession>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun deleteSession(id: Long) = dao.deleteById(id)

    override suspend fun clearHistory() = dao.clearAll()
}
