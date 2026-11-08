package com.example.jewelrymanagement.repositories

import android.content.Context
import android.util.Patterns
import com.example.jewelrymanagement.database.DatabaseHelper
import com.example.jewelrymanagement.database.OtpSessionDao
import com.example.jewelrymanagement.database.UserDao
import com.example.jewelrymanagement.models.OtpSession
import com.example.jewelrymanagement.utils.GmailOtpSender
import com.example.jewelrymanagement.utils.OtpEmailResult
import com.example.jewelrymanagement.utils.OtpEmailSender
import com.example.jewelrymanagement.utils.OtpUtils
import com.example.jewelrymanagement.utils.PasswordUtils

sealed class OtpRequestResult {
    data class Sent(val email: String, val expiresAt: Long, val resendAvailableAt: Long) : OtpRequestResult()
    data class Error(val message: String, val retryAfterMs: Long = 0) : OtpRequestResult()
}

sealed class OtpVerifyResult {
    /** [resetToken] must be passed to the reset screen; it is the only way to change the password. */
    data class Verified(val resetToken: String) : OtpVerifyResult()
    data class WrongCode(val message: String, val remainingAttempts: Int) : OtpVerifyResult()
    /** [mustRequestNew] = the current code can no longer be used (expired, used, too many attempts). */
    data class Error(val message: String, val mustRequestNew: Boolean) : OtpVerifyResult()
}

/**
 * Forgot-password flow, fully on the device:
 * 1. [requestOtp]  – find the account by email in SQLite, generate an OTP, email it via Gmail SMTP.
 * 2. [verifyOtp]   – check code, expiry, single use and attempt limit; return a one-time reset token.
 * 3. [resetPassword] – with a valid token, update the password and invalidate the code in one transaction.
 *
 * Every method blocks (database and network). Call them from Dispatchers.IO.
 */
class PasswordResetRepository(
    context: Context,
    private val emailSender: OtpEmailSender = GmailOtpSender(context.applicationContext),
    private val clock: () -> Long = System::currentTimeMillis
) {

    companion object {
        const val MIN_PASSWORD_LENGTH = 6
        private const val INVALID_SESSION =
            "Phiên đặt lại mật khẩu không hợp lệ hoặc đã được sử dụng. Vui lòng yêu cầu mã OTP mới."
    }

    private val dbHelper = DatabaseHelper.getInstance(context)
    private val userDao = UserDao(context)
    private val otpDao = OtpSessionDao(context)

    /** Current request for [email], used by the verify screen to restore its countdowns. */
    fun currentSession(email: String): OtpSession? = otpDao.get(email)

    /** True if the last code for [email] can still be entered. */
    fun hasActiveCode(email: String): Boolean {
        val s = otpDao.get(email) ?: return false
        return !s.used && !s.isVerified && !s.isExpired(clock()) && s.failedAttempts < OtpUtils.MAX_FAILED_ATTEMPTS
    }

    fun requestOtp(rawEmail: String): OtpRequestResult {
        val email = rawEmail.trim().lowercase()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return OtpRequestResult.Error("Email không đúng định dạng.")
        }
        val user = userDao.getUserByEmail(email)
            ?: return OtpRequestResult.Error("Email này chưa được đăng ký tài khoản JewelryManagement.")
        if (user.isLocked) {
            return OtpRequestResult.Error("Tài khoản này đang bị khóa. Vui lòng liên hệ cửa hàng.")
        }

        val now = clock()
        val previous = otpDao.get(email)
        if (previous != null) {
            val wait = previous.lastSentAt + OtpUtils.RESEND_COOLDOWN_MS - now
            if (wait > 0) {
                return OtpRequestResult.Error(
                    "Vui lòng đợi ${(wait + 999) / 1000} giây trước khi yêu cầu mã mới.", wait
                )
            }
        }

        // Hourly limit per email.
        val inWindow = previous != null && now - previous.sendWindowStart < OtpUtils.SEND_WINDOW_MS
        val windowStart = if (inWindow) previous!!.sendWindowStart else now
        val sendCount = if (inWindow) previous!!.sendCount + 1 else 1
        if (sendCount > OtpUtils.MAX_SENDS_PER_HOUR) {
            val wait = windowStart + OtpUtils.SEND_WINDOW_MS - now
            return OtpRequestResult.Error(
                "Bạn đã yêu cầu quá ${OtpUtils.MAX_SENDS_PER_HOUR} mã trong 1 giờ. " +
                    "Vui lòng thử lại sau ${(wait + 59_999) / 60_000} phút.", wait
            )
        }

        val otp = OtpUtils.generateOtp()
        val sendResult = emailSender.send(user.email, otp, OtpUtils.OTP_VALIDITY_MS / 60_000)
        if (sendResult != OtpEmailResult.Sent) return OtpRequestResult.Error(messageFor(sendResult))

        // The new code replaces any previous one, so older codes stop working immediately.
        val salt = OtpUtils.newSalt()
        val session = OtpSession(
            userId = user.id,
            email = email,
            otpHash = OtpUtils.hash(otp, salt),
            otpSalt = salt,
            expiresAt = now + OtpUtils.OTP_VALIDITY_MS,
            lastSentAt = now,
            sendCount = sendCount,
            sendWindowStart = windowStart
        )
        if (otpDao.save(session) <= 0) {
            return OtpRequestResult.Error("Không lưu được mã xác minh. Vui lòng thử lại.")
        }
        return OtpRequestResult.Sent(email, session.expiresAt, now + OtpUtils.RESEND_COOLDOWN_MS)
    }

    fun verifyOtp(rawEmail: String, rawOtp: String): OtpVerifyResult {
        val otp = rawOtp.trim()
        if (!OtpUtils.isValidFormat(otp)) {
            return OtpVerifyResult.Error("Mã OTP phải gồm đúng ${OtpUtils.OTP_LENGTH} chữ số.", mustRequestNew = false)
        }
        val session = otpDao.get(rawEmail)
            ?: return OtpVerifyResult.Error("Chưa có yêu cầu khôi phục cho email này. Vui lòng gửi mã mới.", true)
        val now = clock()
        when {
            session.used || session.isVerified ->
                return OtpVerifyResult.Error("Mã OTP này đã được sử dụng. Vui lòng yêu cầu mã mới.", true)
            session.isExpired(now) ->
                return OtpVerifyResult.Error("Mã OTP đã hết hạn. Vui lòng bấm \"Gửi lại mã\".", true)
            session.failedAttempts >= OtpUtils.MAX_FAILED_ATTEMPTS ->
                return OtpVerifyResult.Error("Bạn đã nhập sai quá nhiều lần. Vui lòng yêu cầu mã mới.", true)
        }

        if (!OtpUtils.matches(otp, session.otpSalt, session.otpHash)) {
            val failed = otpDao.incrementFailedAttempts(session.id)
            val remaining = OtpUtils.MAX_FAILED_ATTEMPTS - failed
            if (remaining <= 0) {
                otpDao.markUsed(session.id)
                return OtpVerifyResult.Error("Bạn đã nhập sai quá nhiều lần. Vui lòng yêu cầu mã mới.", true)
            }
            return OtpVerifyResult.WrongCode("Mã OTP không đúng. Bạn còn $remaining lần thử.", remaining)
        }

        val token = OtpUtils.newResetToken()
        if (!otpDao.markVerified(session.id, now, OtpUtils.hash(token, session.otpSalt))) {
            return OtpVerifyResult.Error("Mã OTP này đã được sử dụng. Vui lòng yêu cầu mã mới.", true)
        }
        return OtpVerifyResult.Verified(token)
    }

    /** Returns null on success, otherwise a Vietnamese error message. */
    fun resetPassword(rawEmail: String, resetToken: String, newPassword: String, confirmPassword: String): String? {
        validateNewPassword(newPassword, confirmPassword)?.let { return it }

        val session = otpDao.get(rawEmail) ?: return INVALID_SESSION
        val verifiedAt = session.verifiedAt
        val tokenHash = session.resetTokenHash
        if (session.used || verifiedAt == null || tokenHash == null) return INVALID_SESSION
        if (!OtpUtils.matches(resetToken, session.otpSalt, tokenHash)) return INVALID_SESSION
        if (clock() > verifiedAt + OtpUtils.RESET_TOKEN_VALIDITY_MS) {
            otpDao.markUsed(session.id)
            return "Phiên đặt lại mật khẩu đã hết hạn. Vui lòng yêu cầu mã OTP mới."
        }

        val newHash = PasswordUtils.hashPassword(newPassword)
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            // Consume the code first: if another attempt already used it, nothing is changed.
            if (!otpDao.markUsed(session.id, db)) return INVALID_SESSION
            if (!userDao.updatePassword(session.userId, newHash, db)) {
                return "Không tìm thấy tài khoản cần đổi mật khẩu."
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return null
    }

    fun validateNewPassword(newPassword: String, confirmPassword: String): String? = when {
        newPassword.isBlank() -> "Vui lòng nhập mật khẩu mới."
        newPassword.length < MIN_PASSWORD_LENGTH -> "Mật khẩu phải có ít nhất $MIN_PASSWORD_LENGTH ký tự."
        newPassword != confirmPassword -> "Mật khẩu xác nhận không khớp."
        else -> null
    }

    private fun messageFor(result: OtpEmailResult): String = when (result) {
        OtpEmailResult.NotConfigured ->
            "Chưa cấu hình Gmail gửi OTP (MAIL_USERNAME, MAIL_APP_PASSWORD). Xem hướng dẫn trong README."
        OtpEmailResult.NoNetwork ->
            "Không có kết nối mạng hoặc không kết nối được Gmail. Vui lòng kiểm tra Internet rồi thử lại."
        OtpEmailResult.AuthenticationFailed ->
            "Gmail từ chối đăng nhập. Kiểm tra lại Gmail gửi OTP và App Password trong cấu hình."
        OtpEmailResult.InvalidRecipient ->
            "Không gửi được tới địa chỉ email này. Vui lòng kiểm tra lại email."
        is OtpEmailResult.Failed -> "Gửi email thất bại. Vui lòng thử lại sau."
        OtpEmailResult.Sent -> ""
    }
}
