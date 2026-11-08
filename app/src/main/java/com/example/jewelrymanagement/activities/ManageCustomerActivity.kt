package com.example.jewelrymanagement.activities

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.jewelrymanagement.R
import com.example.jewelrymanagement.adapters.CustomerAdapter
import com.example.jewelrymanagement.database.UserDao
import com.example.jewelrymanagement.databinding.ActivityManageCustomerBinding
import com.example.jewelrymanagement.models.User

class ManageCustomerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageCustomerBinding
    private lateinit var userDao: UserDao
    private lateinit var adapter: CustomerAdapter
    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageCustomerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userDao = UserDao(this)

        setupRecyclerView()
        setupSearch()
        setupSwipeRefresh()
        binding.btnBackManageCustomer.setOnClickListener { finish() }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshCustomers.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.gold_primary),
            ContextCompat.getColor(this, R.color.gold_primary_dark)
        )
        binding.swipeRefreshCustomers.setOnRefreshListener { loadCustomers() }
    }

    override fun onResume() {
        super.onResume()
        loadCustomers()
    }

    private fun setupRecyclerView() {
        adapter = CustomerAdapter(
            customers = emptyList(),
            onItemClick = { customer -> showCustomerDetailDialog(customer) }
        )
        binding.rvManageCustomers.layoutManager = LinearLayoutManager(this)
        binding.rvManageCustomers.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearchCustomer.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                loadCustomers()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadCustomers() {
        val list = if (searchQuery.isBlank()) {
            userDao.getAllCustomers()
        } else {
            userDao.searchCustomers(searchQuery)
        }

        adapter.updateData(list)
        binding.tvCustomerSummary.text = "Hiển thị ${list.size} khách hàng"

        if (list.isEmpty()) {
            binding.layoutEmptyCustomers.visibility = View.VISIBLE
            binding.rvManageCustomers.visibility = View.GONE
        } else {
            binding.layoutEmptyCustomers.visibility = View.GONE
            binding.rvManageCustomers.visibility = View.VISIBLE
        }
        binding.swipeRefreshCustomers.isRefreshing = false
    }

    private fun showCustomerDetailDialog(customer: User) {
        val status = if (customer.isLocked) "Đã khóa" else "Đang hoạt động"
        val message = """
            • Mã ID: #${customer.id}
            • Họ và tên: ${customer.fullName}
            • Tên đăng nhập: @${customer.username}
            • Email: ${customer.email}
            • Số điện thoại: ${if (customer.phone.isNotBlank()) customer.phone else "Chưa cung cấp"}
            • Địa chỉ nhận hàng: ${if (customer.address.isNotBlank()) customer.address else "Chưa cung cấp"}
            • Trạng thái: $status
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Hồ Sơ Khách Hàng")
            .setMessage(message)
            .setPositiveButton(if (customer.isLocked) "Mở khóa" else "Khóa tài khoản") { _, _ ->
                confirmLockChange(customer)
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun confirmLockChange(customer: User) {
        val locking = !customer.isLocked
        val title = if (locking) "Khóa tài khoản?" else "Mở khóa tài khoản?"
        val message = if (locking) {
            "Khách @${customer.username} sẽ không đăng nhập, đổi mật khẩu hoặc lấy lại mật khẩu cho đến khi được mở khóa."
        } else {
            "Khách @${customer.username} sẽ đăng nhập lại được."
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(if (locking) "Khóa" else "Mở khóa") { _, _ ->
                val ok = userDao.setLocked(customer.id, locking)
                val notice = when {
                    !ok -> "Không đổi được trạng thái tài khoản."
                    locking -> "Đã khóa tài khoản @${customer.username}."
                    else -> "Đã mở khóa tài khoản @${customer.username}."
                }
                Toast.makeText(this, notice, Toast.LENGTH_SHORT).show()
                loadCustomers()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
