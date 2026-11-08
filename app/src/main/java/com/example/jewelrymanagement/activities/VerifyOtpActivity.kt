package com.example.jewelrymanagement.activities

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.jewelrymanagement.R
import com.example.jewelrymanagement.databinding.ActivityVerifyOtpBinding
import com.example.jewelrymanagement.repositories.OtpRequestResult
import com.example.jewelrymanagement.repositories.OtpVerifyResult
import com.example.jewelrymanagement.repositories.PasswordResetRepository
import com.example.jewelrymanagement.utils.OtpUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Step 2: the user types the 6-digit code. Shows the remaining validity, lets the user resend
 * after the cooldown and opens the reset screen only after a successful verification.
 */
class VerifyOtpActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EMAIL = "EMAIL"
    }

    private lateinit var binding: ActivityVerifyOtpBinding
    private lateinit var repository: PasswordResetRepository
    private var email = ""

    private var expiryTimer: CountDownTimer? = null
    private var resendTimer: CountDownTimer? = null

    /** False once the current code expired, was used or was locked; verify is then disabled. */
    private var codeUsable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerifyOtpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = PasswordResetRepository(this)

        email = intent.getStringExtra(EXTRA_EMAIL).orEmpty()
        if (email.isBlank()) {
            Toast.makeText(this, "Thiếu thông tin email. Vui lòng thực hiện lại.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.tvOtpEmailHint.text = "Mã OTP gồm 6 chữ số đã được gửi tới $email. Hãy kiểm tra cả thư mục Spam."
        binding.btnBackVerifyOtp.setOnClickListener { finish() }
        binding.btnVerifyOtp.setOnClickListener { verify() }
        binding.btnResendOtp.setOnClickListener { resend() }
    }

    override fun onResume() {
        super.onResume()
        // Timers are rebuilt from the stored request, so they stay correct after rotation or returning to the app.
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) { repository.currentSession(email) }
            if (session == null || session.used || session.isVerified) {
                showCodeUnusable("Không có mã OTP còn hiệu lực. Vui lòng bấm \"Gửi lại mã\".")
                startResendCountdown(0)
            } else {
                startExpiryCountdown(session.expiresAt)
                startResendCountdown(session.lastSentAt + OtpUtils.RESEND_COOLDOWN_MS)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        expiryTimer?.cancel()
        resendTimer?.cancel()
    }

    private fun verify() {
        val otp = binding.etOtpCode.text.toString().trim()
        if (!OtpUtils.isValidFormat(otp)) {
            binding.tilOtpCode.error = "Mã OTP phải gồm đúng ${OtpUtils.OTP_LENGTH} chữ số"
            return
        }
        binding.tilOtpCode.error = null
        setLoading(true)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { repository.verifyOtp(email, otp) }
            setLoading(false)
            when (result) {
                is OtpVerifyResult.Verified -> {
                    Toast.makeText(this@VerifyOtpActivity, "Xác minh thành công!", Toast.LENGTH_SHORT).show()
                    startActivity(
                        Intent(this@VerifyOtpActivity, ResetPasswordActivity::class.java)
                            .putExtra(ResetPasswordActivity.EXTRA_EMAIL, email)
                            .putExtra(ResetPasswordActivity.EXTRA_RESET_TOKEN, result.resetToken)
                    )
                    finish()
                }
                is OtpVerifyResult.WrongCode -> {
                    binding.tilOtpCode.error = result.message
                    binding.etOtpCode.selectAll()
                }
                is OtpVerifyResult.Error -> {
                    if (result.mustRequestNew) {
                        binding.etOtpCode.setText("")
                        showCodeUnusable(result.message)
                    } else {
                        binding.tilOtpCode.error = result.message
                    }
                }
            }
        }
    }

    private fun resend() {
        binding.btnResendOtp.isEnabled = false
        setLoading(true)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { repository.requestOtp(email) }
            setLoading(false)
            when (result) {
                is OtpRequestResult.Sent -> {
                    binding.etOtpCode.setText("")
                    binding.tilOtpCode.error = null
                    Toast.makeText(this@VerifyOtpActivity, "Đã gửi mã OTP mới tới $email", Toast.LENGTH_LONG).show()
                    startExpiryCountdown(result.expiresAt)
                    startResendCountdown(result.resendAvailableAt)
                }
                is OtpRequestResult.Error -> {
                    Toast.makeText(this@VerifyOtpActivity, result.message, Toast.LENGTH_LONG).show()
                    startResendCountdown(System.currentTimeMillis() + result.retryAfterMs)
                }
            }
        }
    }

    private fun startExpiryCountdown(expiresAt: Long) {
        expiryTimer?.cancel()
        val remaining = expiresAt - System.currentTimeMillis()
        if (remaining <= 0) {
            showCodeUnusable("Mã OTP đã hết hạn. Vui lòng bấm \"Gửi lại mã\".")
            return
        }
        codeUsable = true
        binding.btnVerifyOtp.isEnabled = true
        binding.tvOtpCountdown.setTextColor(ContextCompat.getColor(this, R.color.gold_primary_dark))
        expiryTimer = object : CountDownTimer(remaining, 1000) {
            override fun onTick(ms: Long) {
                binding.tvOtpCountdown.text = "Mã còn hiệu lực: ${OtpUtils.formatRemaining(ms)}"
            }

            override fun onFinish() {
                showCodeUnusable("Mã OTP đã hết hạn. Vui lòng bấm \"Gửi lại mã\".")
            }
        }.start()
    }

    private fun startResendCountdown(availableAt: Long) {
        resendTimer?.cancel()
        val remaining = availableAt - System.currentTimeMillis()
        if (remaining <= 0) {
            binding.btnResendOtp.isEnabled = true
            binding.btnResendOtp.text = "Gửi lại mã"
            return
        }
        binding.btnResendOtp.isEnabled = false
        resendTimer = object : CountDownTimer(remaining, 1000) {
            override fun onTick(ms: Long) {
                binding.btnResendOtp.text = "Gửi lại sau ${(ms + 999) / 1000}s"
            }

            override fun onFinish() {
                binding.btnResendOtp.isEnabled = true
                binding.btnResendOtp.text = "Gửi lại mã"
            }
        }.start()
    }

    /** The current code can no longer be verified: disable the button and explain why. */
    private fun showCodeUnusable(message: String) {
        expiryTimer?.cancel()
        codeUsable = false
        binding.btnVerifyOtp.isEnabled = false
        binding.tvOtpCountdown.text = message
        binding.tvOtpCountdown.setTextColor(ContextCompat.getColor(this, R.color.stock_out))
    }

    private fun setLoading(loading: Boolean) {
        binding.progressVerifyOtp.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnVerifyOtp.isEnabled = !loading && codeUsable
    }
}
