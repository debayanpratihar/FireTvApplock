package com.fliptofocus.domain.model

/**
 * Outcome of a single time a locked app was opened.
 *
 * LOCKED    - the lock screen was shown (default / in-progress).
 * UNLOCKED  - the correct PIN or secret sequence was entered.
 * DENIED    - the user backed out without unlocking.
 */
enum class SessionStatus { LOCKED, UNLOCKED, DENIED }

/**
 * One entry in the parent-visible access log: a record that a locked app was opened and what
 * happened. Purely local history so a parent can see when protected apps were accessed.
 *
 * @param id Local database identifier (0 until persisted).
 * @param timestamp Epoch millis when the locked app was opened.
 * @param triggeringPackage The locked app that was opened.
 * @param status What happened ([SessionStatus]).
 */
data class FocusSession(
    val id: Long = 0,
    val timestamp: Long,
    val triggeringPackage: String,
    val status: SessionStatus
)
