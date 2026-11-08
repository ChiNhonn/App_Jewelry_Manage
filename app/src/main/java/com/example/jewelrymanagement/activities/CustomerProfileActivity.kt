package com.example.jewelrymanagement.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.jewelrymanagement.database.UserDao
import com.example.jewelrymanagement.databinding.ActivityCustomerProfileBinding
import com.example.jewelrymanagement.databinding.DialogChangePasswordBinding
import com.example.jewelrymanagement.repositories.PasswordResetRepository
import com.example.jewelrymanagement.utils.SessionManager
import com.example.jewelrymanagement.utils.ValidationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CustomerProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomerProfileBinding
    private lateinit var userDao: UserDao
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userDao = UserDao(this)
        sessionManager = SessionManager(this)

        loadUserProfile()
        setupListeners()
    }

    private fun loadUserProfile() {
        val userId = sessionManager.getUserId()
        val user = userDao.getUserById(userId) ?: sessionManager.getUser()

        if (user != null) {
            binding.tvProfileNameHeader.text = user.fullName
            binding.tvProfileUsernameHeader.text = "@${user.username}"
            binding.etProfileEmail.setText(user.email)
            binding.etProfileFullName.setText(user.fullName)
            binding.etProfilePhone.setText(user.phone)
            binding.etProfileAddress.setText(user.address)
        }
    }

    private fun setupListeners() {
        binding.btnBackProfile.setOnClickListener { finish() }

        binding.btnProfileOrderHistory.setOnClickListener {
            startActivity(Intent(this, OrderHistoryActivity::class.java))
        }

        binding.btnSaveProfile.setOnClickListener {
            saveChanges()
        }

        binding.btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        binding.btnCustomerLogout.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun showChangePasswordDialog() {
        val dialogBinding = DialogChangePasswordBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Đổi mật khẩu")
            .setView(dialogBinding.root)
            .setPositiveButton("Cập nhật", null)
            .setNegativeButton("Hủy", null)
            .create()

        // Set after show() so a validation error keeps the dialog open.
        dialog.setOnShowListener {
            val btnConfirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnConfirm.setOnClickListener {
                val current = dialogBinding.etCurrentPassword.text?.toString().orEmpty()
                val newPassword = dialogBinding.etChangeNewPassword.text?.toString().orEmpty()
                val confirm = dialogBinding.etChangeConfirmPassword.text?.toString().orEmpty()
                val minLength = PasswordResetRepository.MIN_PASSWORD_LENGTH

                dialogBinding.tilCurrentPassword.error =
                    if (current.isEmpty()) "Vui lòng nhập mật khẩu hiện tại" else null
                dialogBinding.tilChangeNewPassword.error = when {
                    newPassword.isBlank() -> "Vui lòng nhập mật khẩu mới"
                    newPassword.length < minLength -> "Mật khẩu tối thiểu $minLength ký tự"
                    newPassword == current -> "Mật khẩu mới phải khác mật khẩu hiện tại"
                    else -> null
                }
                dialogBinding.tilChangeConfirmPassword.error =
                    if (dialogBinding.tilChangeNewPassword.error == null && newPassword != confirm) "Mật khẩu xác nhận không khớp" else null
                if (dialogBinding.tilCurrentPassword.error != null ||
                    dialogBinding.tilChangeNewPassword.error != null ||
                    dialogBinding.tilChangeConfirmPassword.error != null
                ) return@setOnClickListener

                btnConfirm.isEnabled = false
                lifecycleScope.launch {
                    // PBKDF2 hashing is slow, keep it off the main thread.
                    val changed = withContext(Dispatchers.IO) {
                        userDao.changePassword(sessionManager.getUserId(), current, newPassword)
                    }
                    btnConfirm.isEnabled = true
                    if (changed) {
                        dialog.dismiss()
                        Toast.makeText(this@CustomerProfileActivity, "Đổi mật khẩu thành công!", Toast.LENGTH_SHORT).show()
                    } else {
                        dialogBinding.tilCurrentPassword.error = "Mật khẩu hiện tại không đúng"
                    }
                }
            }
        }
        dialog.show()
    }

    private fun saveChanges() {
        val fullName = binding.etProfileFullName.text?.toString()?.trim() ?: ""
        val phone = binding.etProfilePhone.text?.toString()?.trim() ?: ""
        val address = binding.etProfileAddress.text?.toString()?.trim() ?: ""

        binding.tilProfileFullName.error = null
        binding.tilProfilePhone.error = null

        if (fullName.isEmpty()) {
            binding.tilProfileFullName.error = "Họ và tên không được để trống"
            return
        }

        if (phone.isNotEmpty() && !ValidationUtils.isValidPhone(phone)) {
            binding.tilProfilePhone.error = "Số điện thoại không hợp lệ"
            return
        }

        val userId = sessionManager.getUserId()
        val success = userDao.updateProfile(userId, fullName, phone, address)
        if (success) {
            sessionManager.updateUser(fullName, phone, address)
            binding.tvProfileNameHeader.text = fullName
            Toast.makeText(this, "Cập nhật hồ sơ thành công!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Cập nhật thất bại!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Đăng xuất")
            .setMessage("Bạn có chắc chắn muốn đăng xuất khỏi tài khoản?")
            .setPositiveButton("Đăng xuất") { _, _ ->
                sessionManager.clearSession()
                val intent = Intent(this, CustomerActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
