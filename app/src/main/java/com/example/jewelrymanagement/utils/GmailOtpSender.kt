package com.example.jewelrymanagement.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.jewelrymanagement.BuildConfig
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Date
import java.util.Properties
import javax.mail.AuthenticationFailedException
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.PasswordAuthentication
import javax.mail.SendFailedException
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/** Outcome of sending one OTP email. */
sealed class OtpEmailResult {
    object Sent : OtpEmailResult()
    object NotConfigured : OtpEmailResult()
    object NoNetwork : OtpEmailResult()
    object AuthenticationFailed : OtpEmailResult()
    object InvalidRecipient : OtpEmailResult()
    data class Failed(val reason: String) : OtpEmailResult()
}

/** Abstraction so the reset flow can be tested without sending real email. */
fun interface OtpEmailSender {
    /** Blocking network call: always invoke from Dispatchers.IO. */
    fun send(toEmail: String, otp: String, validityMinutes: Long): OtpEmailResult
}

/**
 * Sends the OTP through Gmail SMTP (smtp.gmail.com:587, STARTTLS) with the JavaMail-for-Android library.
 *
 * Sender address and App Password come from BuildConfig, which Gradle fills from the git-ignored
 * secrets.properties / local.properties or environment variables. They are never logged.
 */
class GmailOtpSender(
    private val context: Context,
    private val username: String = BuildConfig.MAIL_USERNAME,
    private val appPassword: String = BuildConfig.MAIL_APP_PASSWORD
) : OtpEmailSender {

    companion object {
        private const val TAG = "GmailOtpSender"
        private const val SMTP_HOST = "smtp.gmail.com"
        private const val SMTP_PORT = "587"
        private const val TIMEOUT_MS = "15000"
        private const val SENDER_NAME = "JewelryManagement"
        const val SUBJECT = "Mã xác minh khôi phục mật khẩu JewelryManagement"

        fun isConfigured(): Boolean =
            BuildConfig.MAIL_USERNAME.isNotBlank() && BuildConfig.MAIL_APP_PASSWORD.isNotBlank()
    }

    override fun send(toEmail: String, otp: String, validityMinutes: Long): OtpEmailResult {
        if (username.isBlank() || appPassword.isBlank()) return OtpEmailResult.NotConfigured
        if (!isNetworkAvailable()) return OtpEmailResult.NoNetwork

        val props = Properties().apply {
            put("mail.smtp.host", SMTP_HOST)
            put("mail.smtp.port", SMTP_PORT)
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.starttls.required", "true")
            put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")
            put("mail.smtp.connectiontimeout", TIMEOUT_MS)
            put("mail.smtp.timeout", TIMEOUT_MS)
            put("mail.smtp.writetimeout", TIMEOUT_MS)
        }
        // Gmail App Passwords are shown with spaces ("abcd efgh ..."); SMTP expects them without.
        val password = appPassword.replace(" ", "")
        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(username, password)
        })

        return try {
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(username, SENDER_NAME, "UTF-8"))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail, true))
                setSubject(SUBJECT, "UTF-8")
                setText(buildBody(otp, validityMinutes), "UTF-8")
                sentDate = Date()
            }
            Transport.send(message)
            OtpEmailResult.Sent
        } catch (e: AuthenticationFailedException) {
            Log.w(TAG, "SMTP authentication failed")
            OtpEmailResult.AuthenticationFailed
        } catch (e: SendFailedException) {
            Log.w(TAG, "SMTP rejected recipient")
            OtpEmailResult.InvalidRecipient
        } catch (e: MessagingException) {
            Log.w(TAG, "SMTP error: ${e.javaClass.simpleName}")
            if (isNetworkError(e)) OtpEmailResult.NoNetwork
            else OtpEmailResult.Failed(e.javaClass.simpleName)
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error: ${e.javaClass.simpleName}")
            OtpEmailResult.Failed(e.javaClass.simpleName)
        }
    }

    private fun buildBody(otp: String, validityMinutes: Long) = """
        Xin chào,

        Bạn (hoặc ai đó) vừa yêu cầu khôi phục mật khẩu tài khoản JewelryManagement.

        Mã xác minh của bạn là: $otp

        Mã có hiệu lực trong $validityMinutes phút và chỉ dùng được một lần.
        Tuyệt đối không chia sẻ mã này cho bất kỳ ai, kể cả người tự nhận là nhân viên JewelryManagement.
        Nếu bạn không yêu cầu đổi mật khẩu, hãy bỏ qua email này; mật khẩu hiện tại vẫn giữ nguyên.

        Trân trọng,
        JewelryManagement
    """.trimIndent()

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isNetworkError(e: Throwable): Boolean {
        var current: Throwable? = e
        var depth = 0
        while (current != null && depth++ < 10) {
            if (current is UnknownHostException || current is ConnectException || current is SocketTimeoutException) return true
            current = if (current is MessagingException) current.nextException ?: current.cause else current.cause
        }
        return false
    }
}
