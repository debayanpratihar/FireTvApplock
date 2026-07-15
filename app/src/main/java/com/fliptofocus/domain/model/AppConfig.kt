package com.fliptofocus.domain.model

/**
 * Parental-lock configuration for KidLock TV. Everything is stored strictly on-device; nothing is
 * ever transmitted.
 *
 * Credentials are never stored in the clear: the PIN, the optional secret remote sequence, and the
 * recovery code are each kept as a salted SHA-256 hash (see [com.fliptofocus.util.PinSecurity]).
 *
 * @param isLockingEnabled Master switch. When off, no app is intercepted.
 * @param pinHash Salted hash of the parent's PIN, or null until a PIN is set.
 * @param pinSalt Salt used for [pinHash].
 * @param comboHash Salted hash of the optional secret remote-button sequence, or null if unset.
 * @param comboSalt Salt used for [comboHash].
 * @param recoveryHash Salted hash of the auto-generated recovery code, or null if unset.
 * @param recoverySalt Salt used for [recoveryHash].
 * @param relockGraceSeconds How long an unlocked app stays open after the user leaves it before it
 *        re-locks. 0 means re-lock as soon as the app leaves the foreground.
 */
data class AppConfig(
    val isLockingEnabled: Boolean = true,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val comboHash: String? = null,
    val comboSalt: String? = null,
    val recoveryHash: String? = null,
    val recoverySalt: String? = null,
    val relockGraceSeconds: Int = 0
) {
    /** True once the parent has created a PIN (the app's core credential). */
    val isPinSet: Boolean get() = !pinHash.isNullOrEmpty() && !pinSalt.isNullOrEmpty()

    /** True when an optional secret remote-button sequence has been configured. */
    val isComboSet: Boolean get() = !comboHash.isNullOrEmpty() && !comboSalt.isNullOrEmpty()

    /** True once a recovery code exists (generated the first time a PIN is set). */
    val isRecoverySet: Boolean get() = !recoveryHash.isNullOrEmpty() && !recoverySalt.isNullOrEmpty()
}
