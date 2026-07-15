package com.fliptofocus.lock

import com.fliptofocus.domain.repository.AppConfigRepository
import com.fliptofocus.util.PinSecurity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central place for creating and updating the parental-lock credentials. Everything is hashed via
 * [PinSecurity] before it touches storage, so the ViewModels never handle raw secrets beyond the
 * moment of entry.
 */
@Singleton
class LockCredentialsManager @Inject constructor(
    private val repo: AppConfigRepository
) {

    /**
     * Sets (or changes) the PIN. The first time a PIN is ever created this also generates a
     * recovery code and returns it so the UI can show it ONCE for the parent to write down; on a
     * later change it returns null (the existing recovery code still applies).
     */
    suspend fun setPin(pin: String): String? {
        val cfg = repo.getConfig()
        val salt = PinSecurity.newSalt()
        var updated = cfg.copy(pinHash = PinSecurity.hash(pin, salt), pinSalt = salt)

        var recoveryToShow: String? = null
        if (!cfg.isRecoverySet) {
            val code = PinSecurity.generateRecoveryCode()
            val rSalt = PinSecurity.newSalt()
            updated = updated.copy(
                recoveryHash = PinSecurity.hash(PinSecurity.normalizeRecovery(code), rSalt),
                recoverySalt = rSalt
            )
            recoveryToShow = code
        }
        repo.updateConfig(updated)
        return recoveryToShow
    }

    suspend fun verifyPin(pin: String): Boolean {
        val cfg = repo.getConfig()
        return PinSecurity.verify(pin, cfg.pinSalt, cfg.pinHash)
    }

    suspend fun setCombo(sequence: List<Int>) {
        val cfg = repo.getConfig()
        val salt = PinSecurity.newSalt()
        repo.updateConfig(
            cfg.copy(comboHash = PinSecurity.hash(Combo.encode(sequence), salt), comboSalt = salt)
        )
    }

    suspend fun clearCombo() {
        repo.updateConfig(repo.getConfig().copy(comboHash = null, comboSalt = null))
    }

    /** Creates a fresh recovery code, returns it for one-time display. */
    suspend fun regenerateRecovery(): String {
        val cfg = repo.getConfig()
        val code = PinSecurity.generateRecoveryCode()
        val salt = PinSecurity.newSalt()
        repo.updateConfig(
            cfg.copy(
                recoveryHash = PinSecurity.hash(PinSecurity.normalizeRecovery(code), salt),
                recoverySalt = salt
            )
        )
        return code
    }

    suspend fun setLockingEnabled(enabled: Boolean) {
        repo.updateConfig(repo.getConfig().copy(isLockingEnabled = enabled))
    }

    suspend fun setRelockGraceSeconds(seconds: Int) {
        repo.updateConfig(repo.getConfig().copy(relockGraceSeconds = seconds.coerceIn(0, 3600)))
    }
}
