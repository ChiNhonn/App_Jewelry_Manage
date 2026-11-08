package com.example.jewelrymanagement.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.jewelrymanagement.R
import com.example.jewelrymanagement.database.CartDao
import com.example.jewelrymanagement.database.UserDao
import com.example.jewelrymanagement.databinding.ActivityLoginBinding
import com.example.jewelrymanagement.utils.SessionManager

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var userDao: UserDao
    private lateinit var sessionManager: SessionManager
    private var selectedRole: String = "CUSTOMER"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userDao = UserDao(this)
        sessionManager = SessionManager(this)

        val roleExtra = intent.getStringExtra("LOGIN_ROLE")
        if (roleExtra == "ADMIN") {
            setRole("ADMIN")
        } else {
            setRole("CUSTOMER")
        }

        binding.tabCustomer.setOnClickListener { setRole("CUSTOMER") }
        binding.tabAdmin.setOnClickListener { setRole("ADMIN") }

        binding.btnLoginBack.setOnClickListener { finish() }

        binding.tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        binding.tvQuickFillHint.setOnClickListener {
            if (selectedRole == "ADMIN") {
                binding.etUsername.setText("admin")
                binding.etPassword.setText("admin123")
            } else {
                binding.etUsername.setText("customer1")
                binding.etPassword.setText("customer123")
            }
        }

        binding.btnLoginSubmit.setOnClickListener {
            performLogin()
        }
    }

    private fun setRole(role: String) {
        selectedRole = role
        binding.tvLoginError.visibility = View.GONE

        if (role == "ADMIN") {
            binding.tabAdmin.setBackgroundResource(R.drawable.bg_button_gold)
            binding.tabAdmin.setTextColor(ContextCompat.getColor(this, R.color.white))

            binding.tabCustomer.setBackgroundResource(android.R.color.transparent)
            binding.tabCustomer.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

            binding.tvLoginTitle.text = "Đăng Nhập Quản Trị Viên"
            binding.tvLoginSubtitle.text = "Dành riêng cho nhân viên quản trị cửa hàng"
            binding.layoutRegisterPrompt.visibility = View.GONE
        } else {
            binding.tabCustomer.setBackgroundResource(R.drawable.bg_button_gold)
            binding.tabCustomer.setTextColor(ContextCompat.getColor(this, R.color.white))

            binding.tabAdmin.setBackgroundResource(android.R.color.transparent)
            binding.tabAdmin.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

            binding.tvLoginTitle.text = "Đăng Nhập Khách Hàng"
            binding.tvLoginSubtitle.text = "Khám phá trang sức hoàng gia và ưu đãi độc quyền"
            binding.layoutRegisterPrompt.visibility = View.VISIBLE
        }
    }

    private fun performLogin() {
        val usernameOrEmail = binding.etUsername.text?.toString()?.trim() ?: ""
        val password = binding.etPassword.text?.toString() ?: ""

        if (usernameOrEmail.isEmpty()) {
            binding.tilUsername.error = "Vui lòng nhập tên đăng nhập hoặc email"
            return
        } else {
            binding.tilUsername.error = null
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = "Vui lòng nhập mật khẩu"
            return
        } else {
            binding.tilPassword.error = null
        }

        val user = userDao.login(usernameOrEmail, password)
        if (user == null) {
            binding.tvLoginError.text = "Tài khoản hoặc mật khẩu không chính xác!"
            binding.tvLoginError.visibility = View.VISIBLE
            return
        }
        if (user.isLocked) {
            binding.tvLoginError.text = "Tài khoản đã bị khóa. Vui lòng liên hệ cửa hàng."
            binding.tvLoginError.visibility = View.VISIBLE
            return
        }

        // Validate role
        if (selectedRole == "ADMIN" && user.role != "ADMIN") {
            binding.tvLoginError.text = "Tài khoản này không có quyền truy cập Quản Trị Viên!"
            binding.tvLoginError.visibility = View.VISIBLE
            return
        }

        // Save session
        sessionManager.saveUser(user)
        if (user.role == "CUSTOMER") CartDao(this).mergeCart(UserDao(this).getGuestUserId(), user.id)
        Toast.makeText(this, "Đăng nhập thành công! Xin chào ${user.fullName}", Toast.LENGTH_SHORT).show()

        if (user.role == "ADMIN") {
            val intent = Intent(this, AdminActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        } else if (isTaskRoot) {
            startActivity(Intent(this, CustomerActivity::class.java))
        }
        // Customers return to the screen that asked them to log in; it refreshes in onResume.
        finish()
    }
}
