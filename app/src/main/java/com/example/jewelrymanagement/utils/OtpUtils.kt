package com.example.jewelrymanagement.utils

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** OTP policy and helpers: generation with SecureRandom, salted hashing, constant-time comparison. */
object OtpUtils {

    const val OTP_LENGTH = 6

    /** An OTP is valid for 5 minutes after it is sent. */
    const val OTP_VALIDITY_MS = 5 * 60_000L

    /** After this many wrong entries the code is invalidated and a new one must be requested. */
    const val MAX_FAILED_ATTEMPTS = 5

    /** Minimum time between two emails to the same address. */
    const val RESEND_COOLDOWN_MS = 60_000L

    /** At most this many emails per address per hour. */
    const val MAX_SENDS_PER_HOUR = 5
    const val SEND_WINDOW_MS = 60 * 60_000L

    /** After a correct OTP the user has 10 minutes to choose the new password. */
    const val RESET_TOKEN_VALIDITY_MS = 10 * 60_000L

    private val random = SecureRandom()
    private val otpFormat = Regex("^\\d{$OTP_LENGTH}$")

    fun generateOtp(): String = buildString {
        repeat(OTP_LENGTH) { append(random.nextInt(10)) }
    }

    fun isValidFormat(otp: String): Boolean = otpFormat.matches(otp)

    fun newSalt(): String = randomBase64(16)

    /** Random one-time token handed to the reset screen after a correct OTP. */
    fun newResetToken(): String = randomBase64(32)

    fun hash(value: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray(Charsets.UTF_8))
        digest.update(':'.code.toByte())
        digest.update(value.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest.digest())
    }

    fun matches(value: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(hash(value, salt).toByteArray(), expectedHash.toByteArray())

    /** "mm:ss" for countdown labels. */
    fun formatRemaining(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0) + 999) / 1000
        return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    private fun randomBase64(bytes: Int): String {
        val buf = ByteArray(bytes).also { random.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf)
    }
}
