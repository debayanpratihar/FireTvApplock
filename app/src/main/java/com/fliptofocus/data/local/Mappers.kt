package com.fliptofocus.data.local

import com.fliptofocus.domain.model.AppConfig
import com.fliptofocus.domain.model.BlockedApp
import com.fliptofocus.domain.model.FocusSession
import com.fliptofocus.domain.model.SessionStatus

// ---------------------------------------------------------------------------
// BlockedApp <-> BlockedAppEntity
// ---------------------------------------------------------------------------

fun BlockedAppEntity.toDomain(): BlockedApp =
    BlockedApp(
        packageName = packageName,
        appLabel = appLabel,
        isEnabled = isEnabled
    )

fun BlockedApp.toEntity(addedAt: Long = System.currentTimeMillis()): BlockedAppEntity =
    BlockedAppEntity(
        packageName = packageName,
        appLabel = appLabel,
        isEnabled = isEnabled,
        addedAt = addedAt
    )

// ---------------------------------------------------------------------------
// FocusSession (access-log entry) <-> FocusSessionEntity
//
// The legacy focus_sessions table is reused as the access log: startTimestamp holds when the
// locked app was opened; endTimestamp / challengeDurationMillis are unused (kept as columns only).
// ---------------------------------------------------------------------------

fun FocusSessionEntity.toDomain(): FocusSession =
    FocusSession(
        id = id,
        timestamp = startTimestamp,
        triggeringPackage = triggeringPackage,
        status = runCatching { SessionStatus.valueOf(status) }
            .getOrDefault(SessionStatus.LOCKED)
    )

// ---------------------------------------------------------------------------
// AppConfig <-> AppConfigEntity
// ---------------------------------------------------------------------------

fun AppConfigEntity.toDomain(): AppConfig =
    AppConfig(
        isLockingEnabled = isBlockingEnabled,
        pinHash = pinHash,
        pinSalt = pinSalt,
        comboHash = comboHash,
        comboSalt = comboSalt,
        recoveryHash = recoveryHash,
        recoverySalt = recoverySalt,
        relockGraceSeconds = relockGraceSeconds ?: 0
    )

fun AppConfig.toEntity(): AppConfigEntity =
    AppConfigEntity(
        id = 1,
        // Legacy columns keep their data-class defaults; the lock does not use them.
        isBlockingEnabled = isLockingEnabled,
        pinHash = pinHash,
        pinSalt = pinSalt,
        comboHash = comboHash,
        comboSalt = comboSalt,
        recoveryHash = recoveryHash,
        recoverySalt = recoverySalt,
        relockGraceSeconds = relockGraceSeconds
    )
