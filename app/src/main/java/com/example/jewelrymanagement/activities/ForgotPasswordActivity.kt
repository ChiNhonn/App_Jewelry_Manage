package com.example.jewelrymanagement.activities

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.jewelrymanagement.databinding.ActivityForgotPasswordBinding
import com.example.jewelrymanagement.repositories.OtpRequestResult
import com.example.jewelrymanagement.repositories.PasswordResetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Step 1: the user enters the registered email; an OTP is generated and sent to that Gmail. */
class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private lateinit var repository: PasswordResetRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = PasswordResetRepository(this)

        intent.getStringExtra(VerifyOtpActivity.EXTRA_EMAIL)?.let { binding.etForgotEmail.setText(it) }
        binding.btnBackForgot.setOnClickListener { finish() }
        binding.btnSendOtp.setOnClickListener { sendOtp() }
    }

    private fun sendOtp() {
        val email = binding.etForgotEmail.text.toString().trim().lowercase()
        binding.tvForgotInfo.visibility = View.GONE
        when {
            email.isBlank() -> {
                binding.tilForgotEmail.error = "Vui lòng nhập địa chỉ email"
                return
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.tilForgotEmail.error = "Email không đúng định dạng"
                return
            }
        }
        binding.tilForgotEmail.error = null
        setLoading(true)

        // SQLite lookup + SMTP sending run on the IO dispatcher so the UI never freezes.
        lifecycleScope.launch {
            val (result, codeStillValid) = withContext(Dispatchers.IO) {
                val r = repository.requestOtp(email)
                r to (r is OtpRequestResult.Error && r.retryAfterMs > 0 && repository.hasActiveCode(email))
            }
            setLoading(false)
            when (result) {
                is OtpRequestResult.Sent -> {
                    Toast.makeText(this@ForgotPasswordActivity, "Đã gửi mã OTP tới $email", Toast.LENGTH_LONG).show()
                    openVerifyScreen(email)
                }
                is OtpRequestResult.Error -> {
                    // A code sent less than a minute ago is still valid: let the user go enter it.
                    if (codeStillValid) {
                        Toast.makeText(this@ForgotPasswordActivity, result.message, Toast.LENGTH_LONG).show()
                        openVerifyScreen(email)
                    } else {
                        binding.tvForgotInfo.text = result.message
                        binding.tvForgotInfo.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun openVerifyScreen(email: String) {
        startActivity(Intent(this, VerifyOtpActivity::class.java).putExtra(VerifyOtpActivity.EXTRA_EMAIL, email))
    }

    private fun setLoading(loading: Boolean) {
        binding.progressForgot.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSendOtp.isEnabled = !loading
        binding.etForgotEmail.isEnabled = !loading
        binding.btnSendOtp.text = if (loading) "Đang gửi..." else "Gửi Mã OTP"
    }
}
