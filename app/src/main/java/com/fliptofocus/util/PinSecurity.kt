package com.fliptofocus.util

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Small, self-contained crypto helper for the parental lock. Credentials (PIN, secret remote
 * sequence, recovery code) are never stored in the clear: each is kept as a salted SHA-256 hash so
 * that even a reader of the app's private database cannot recover it. All processing is on-device.
 */
object PinSecurity {

    private val random = SecureRandom()

    /** Fresh random salt, base64-encoded. */
    fun newSalt(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** Salted SHA-256 hash of [secret], base64-encoded. */
    fun hash(secret: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest((salt + secret).toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    /** True when [secret] matches [expectedHash] under [salt]. Safe on null/blank stored values. */
    fun verify(secret: String, salt: String?, expectedHash: String?): Boolean {
        if (salt.isNullOrEmpty() || expectedHash.isNullOrEmpty()) return false
        val actual = runCatching { hash(secret, salt) }.getOrNull() ?: return false
        return constantTimeEquals(actual, expectedHash)
    }

    /**
     * A 10-digit recovery code shown once at setup, grouped for readability, e.g. "1234-567-890".
     * Digits only so it can be typed on the same numeric pad used for the PIN (D-pad friendly on
     * Fire TV). 10 digits give 10^10 combinations.
     */
    fun generateRecoveryCode(): String {
        val digits = StringBuilder(10)
        repeat(10) { digits.append(random.nextInt(10)) }
        val d = digits.toString()
        return "${d.substring(0, 4)}-${d.substring(4, 7)}-${d.substring(7, 10)}"
    }

    /** Canonical form of a recovery code for hashing/comparison (digits only). */
    fun normalizeRecovery(input: String): String =
        input.filter { it.isDigit() }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val ab = a.toByteArray(Charsets.UTF_8)
        val bb = b.toByteArray(Charsets.UTF_8)
        if (ab.size != bb.size) return false
        var result = 0
        for (i in ab.indices) result = result or (ab[i].toInt() xor bb[i].toInt())
        return result == 0
    }
}
