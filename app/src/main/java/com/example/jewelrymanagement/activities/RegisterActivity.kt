package com.example.jewelrymanagement.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.jewelrymanagement.database.UserDao
import com.example.jewelrymanagement.databinding.ActivityRegisterBinding
import com.example.jewelrymanagement.models.User
import com.example.jewelrymanagement.utils.ValidationUtils

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var userDao: UserDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userDao = UserDao(this)

        binding.btnRegisterBack.setOnClickListener { finish() }
        binding.tvBackToLogin.setOnClickListener { finish() }

        binding.btnRegisterSubmit.setOnClickListener {
            performRegister()
        }
    }

    private fun performRegister() {
        val fullName = binding.etFullName.text?.toString()?.trim() ?: ""
        val username = binding.etRegUsername.text?.toString()?.trim() ?: ""
        val email = binding.etEmail.text?.toString()?.trim() ?: ""
        val phone = binding.etPhone.text?.toString()?.trim() ?: ""
        val address = binding.etAddress.text?.toString()?.trim() ?: ""
        val password = binding.etRegPassword.text?.toString() ?: ""
        val confirmPassword = binding.etConfirmPassword.text?.toString() ?: ""

        // Reset errors
        binding.tilFullName.error = null
        binding.tilRegUsername.error = null
        binding.tilEmail.error = null
        binding.tilPhone.error = null
        binding.tilRegPassword.error = null
        binding.tilConfirmPassword.error = null

        var hasError = false

        if (fullName.isEmpty()) {
            binding.tilFullName.error = "Vui lòng nhập họ và tên"
            hasError = true
        }

        if (!ValidationUtils.isValidUsername(username)) {
            binding.tilRegUsername.error = "Tên đăng nhập phải có ít nhất 3 ký tự"
            hasError = true
        } else if (userDao.isUsernameExists(username)) {
            binding.tilRegUsername.error = "Tên đăng nhập này đã được sử dụng!"
            hasError = true
        }

        if (!ValidationUtils.isValidEmail(email)) {
            binding.tilEmail.error = "Email không đúng định dạng hợp lệ"
            hasError = true
        } else if (userDao.isEmailExists(email)) {
            binding.tilEmail.error = "Email này đã được đăng ký tài khoản khác!"
            hasError = true
        }

        if (phone.isNotEmpty() && !ValidationUtils.isValidPhone(phone)) {
            binding.tilPhone.error = "Số điện thoại phải từ 10 chữ số (bắt đầu bằng 0)"
            hasError = true
        }

        if (!ValidationUtils.isValidPassword(password)) {
            binding.tilRegPassword.error = "Mật khẩu phải chứa ít nhất 6 ký tự"
            hasError = true
        }

        if (password != confirmPassword) {
            binding.tilConfirmPassword.error = "Mật khẩu xác nhận không khớp!"
            hasError = true
        }

        if (hasError) return

        val newUser = User(
            username = username,
            fullName = fullName,
            email = email,
            phone = phone,
            passwordHash = password, // Will be hashed inside userDao.registerCustomer
            role = "CUSTOMER",
            address = address
        )

        val newId = userDao.registerCustomer(newUser)
        if (newId > 0) {
            Toast.makeText(this, "Đăng ký thành công! Vui lòng đăng nhập.", Toast.LENGTH_LONG).show()
            finish()
        } else {
            Toast.makeText(this, "Đăng ký thất bại, vui lòng thử lại!", Toast.LENGTH_SHORT).show()
        }
    }
}
