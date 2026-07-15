package com.fliptofocus.domain.repository

import com.fliptofocus.domain.model.FocusSession
import com.fliptofocus.domain.model.SessionStatus
import kotlinx.coroutines.flow.Flow

/** Local access log: records each time a locked app was opened and what happened. */
interface FocusSessionRepository {
    /** Appends one entry to the access log. Returns the new row id. */
    suspend fun logEvent(triggeringPackage: String, status: SessionStatus): Long
    fun observeSessions(): Flow<List<FocusSession>>
    suspend fun deleteSession(id: Long)
    suspend fun clearHistory()
}
