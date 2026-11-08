package com.example.jewelrymanagement.utils

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Password hashing with PBKDF2-HMAC-SHA256 and a random salt per password.
 *
 * Stored format: `pbkdf2_sha256$<iterations>$<salt base64>$<hash base64>`.
 * Older rows hashed with plain SHA-256 (64 hex chars) still verify; [needsRehash] tells
 * the caller to upgrade them to the new format after a successful login.
 */
object PasswordUtils {

    private const val PREFIX = "pbkdf2_sha256"
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    /** Kept moderate so login/registration stay responsive on low-end phones. */
    private const val ITERATIONS = 60_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    private val random = SecureRandom()
    private val legacySha256 = Regex("^[0-9a-f]{64}$")

    fun hashPassword(password: String): String {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val hash = pbkdf2(password, salt, ITERATIONS)
        val enc = Base64.getEncoder()
        return "$PREFIX\$$ITERATIONS\$${enc.encodeToString(salt)}\$${enc.encodeToString(hash)}"
    }

    fun verifyPassword(password: String, storedHash: String): Boolean {
        if (storedHash.startsWith("$PREFIX\$")) {
            val parts = storedHash.split('$')
            if (parts.size != 4) return false
            return try {
                val iterations = parts[1].toInt()
                val dec = Base64.getDecoder()
                val salt = dec.decode(parts[2])
                val expected = dec.decode(parts[3])
                MessageDigest.isEqual(expected, pbkdf2(password, salt, iterations))
            } catch (e: Exception) {
                false
            }
        }
        if (legacySha256.matches(storedHash)) {
            return MessageDigest.isEqual(storedHash.toByteArray(), legacySha256Hex(password).toByteArray())
        }
        // Very old databases may contain an unhashed password; it is upgraded right after login.
        return MessageDigest.isEqual(storedHash.toByteArray(), password.toByteArray())
    }

    /** True when [storedHash] is not in the current PBKDF2 format/strength. */
    fun needsRehash(storedHash: String): Boolean {
        if (!storedHash.startsWith("$PREFIX\$")) return true
        return storedHash.split('$').getOrNull(1)?.toIntOrNull() != ITERATIONS
    }

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun legacySha256Hex(password: String): String =
        MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
