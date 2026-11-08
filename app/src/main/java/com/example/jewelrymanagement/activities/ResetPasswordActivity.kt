package com.example.jewelrymanagement.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.jewelrymanagement.databinding.ActivityResetPasswordBinding
import com.example.jewelrymanagement.repositories.PasswordResetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Step 3: choose a new password. Only reachable from VerifyOtpActivity with the one-time reset
 * token; the repository re-checks the token, so the OTP step cannot be skipped.
 */
class ResetPasswordActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EMAIL = "EMAIL"
        const val EXTRA_RESET_TOKEN = "RESET_TOKEN"
    }

    private lateinit var binding: ActivityResetPasswordBinding
    private lateinit var repository: PasswordResetRepository
    private var email = ""
    private var resetToken = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = PasswordResetRepository(this)

        email = intent.getStringExtra(EXTRA_EMAIL).orEmpty()
        resetToken = intent.getStringExtra(EXTRA_RESET_TOKEN).orEmpty()
        if (email.isBlank() || resetToken.isBlank()) {
            Toast.makeText(this, "Vui lòng xác minh mã OTP trước khi đặt lại mật khẩu.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.tvResetEmailDisplay.text = "Tài khoản: $email"
        binding.btnBackReset.setOnClickListener { finish() }
        binding.btnConfirmReset.setOnClickListener { updatePassword() }
    }

    private fun updatePassword() {
        val newPassword = binding.etNewPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        binding.tilNewPassword.error = when {
            newPassword.isBlank() -> "Vui lòng nhập mật khẩu mới"
            newPassword.length < PasswordResetRepository.MIN_PASSWORD_LENGTH ->
                "Mật khẩu tối thiểu ${PasswordResetRepository.MIN_PASSWORD_LENGTH} ký tự"
            else -> null
        }
        binding.tilConfirmPassword.error =
            if (binding.tilNewPassword.error == null && newPassword != confirmPassword) "Mật khẩu xác nhận không khớp" else null
        if (binding.tilNewPassword.error != null || binding.tilConfirmPassword.error != null) return

        setLoading(true)
        lifecycleScope.launch {
            // Hashing (PBKDF2) and the SQLite transaction run off the main thread.
            val error = withContext(Dispatchers.IO) {
                repository.resetPassword(email, resetToken, newPassword, confirmPassword)
            }
            setLoading(false)
            if (error == null) {
                Toast.makeText(this@ResetPasswordActivity, "Đổi mật khẩu thành công", Toast.LENGTH_LONG).show()
                backToLogin()
            } else {
                AlertDialog.Builder(this@ResetPasswordActivity)
                    .setTitle("Không thể đổi mật khẩu")
                    .setMessage(error)
                    .setPositiveButton("Đóng", null)
                    .setNegativeButton("Yêu cầu mã mới") { _, _ ->
                        startActivity(
                            Intent(this@ResetPasswordActivity, ForgotPasswordActivity::class.java)
                                .putExtra(VerifyOtpActivity.EXTRA_EMAIL, email)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        )
                        finish()
                    }
                    .show()
            }
        }
    }

    /** Returns to the existing login screen (or opens one) and removes the reset screens from the back stack. */
    private fun backToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.progressReset.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnConfirmReset.isEnabled = !loading
        binding.btnConfirmReset.text = if (loading) "Đang cập nhật..." else "Cập Nhật Mật Khẩu"
    }
}
