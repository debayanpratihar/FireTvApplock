package com.fliptofocus.data.local

import com.fliptofocus.domain.model.AppConfig
import com.fliptofocus.domain.model.BlockedApp

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
