package com.example.jewelrymanagement.models

/**
 * One password-reset request for one email (row in `otp_sessions`).
 * The OTP and the reset token are never stored in clear text, only salted hashes.
 * Times are epoch milliseconds.
 */
data class OtpSession(
    val id: Long = 0,
    val userId: Long,
    val email: String,
    val purpose: String = PURPOSE_RESET_PASSWORD,
    val otpHash: String,
    val otpSalt: String,
    val expiresAt: Long,
    val lastSentAt: Long,
    /** Number of emails sent since [sendWindowStart] (hourly limit). */
    val sendCount: Int,
    val sendWindowStart: Long,
    val failedAttempts: Int = 0,
    /** Set when the correct OTP was entered; the reset token is valid only after this. */
    val verifiedAt: Long? = null,
    val resetTokenHash: String? = null,
    /** True once the password was changed (or the code was invalidated); cannot be reused. */
    val used: Boolean = false
) {
    companion object {
        const val PURPOSE_RESET_PASSWORD = "RESET_PASSWORD"
    }

    val isVerified: Boolean get() = verifiedAt != null

    fun isExpired(now: Long = System.currentTimeMillis()): Boolean = now > expiresAt
}
