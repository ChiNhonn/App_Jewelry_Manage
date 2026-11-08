package com.example.jewelrymanagement.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.jewelrymanagement.database.CategoryDao
import com.example.jewelrymanagement.database.OrderDao
import com.example.jewelrymanagement.database.ProductDao
import com.example.jewelrymanagement.database.UserDao
import com.example.jewelrymanagement.databinding.ActivityAdminBinding
import com.example.jewelrymanagement.utils.CurrencyUtils
import com.example.jewelrymanagement.utils.SessionManager

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var productDao: ProductDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var orderDao: OrderDao
    private lateinit var userDao: UserDao

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        // Security check
        if (!sessionManager.isLoggedIn() || !sessionManager.isAdmin()) {
            val intent = Intent(this, CustomerActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        productDao = ProductDao(this)
        categoryDao = CategoryDao(this)
        orderDao = OrderDao(this)
        userDao = UserDao(this)

        setupSwipeRefresh()
        setupListeners()
        if (savedInstanceState == null) requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        loadDashboardMetrics()
    }

    private fun loadDashboardMetrics() {
        val user = sessionManager.getUser()
        binding.tvAdminGreeting.text = "Xin chào, ${user?.fullName ?: "Quản Trị Viên"}"

        val totalRevenue = orderDao.getTotalRevenue()
        binding.tvAdminRevenue.text = CurrencyUtils.formatVnd(totalRevenue)

        binding.tvCountProducts.text = productDao.getProductCount().toString()
        binding.tvCountOrders.text = orderDao.getOrderCount().toString()
        binding.tvCountCategories.text = categoryDao.getCategoryCount().toString()
        binding.tvCountCustomers.text = userDao.getCustomerCount().toString()

        // Check low stock
        val lowStockProducts = productDao.getLowStockProducts(5)
        if (lowStockProducts.isNotEmpty()) {
            binding.cardStockAlert.visibility = View.VISIBLE
            binding.tvStockAlertMessage.text = "Cảnh báo: Có ${lowStockProducts.size} sản phẩm tồn kho thấp (<= 5)!"
        } else {
            binding.cardStockAlert.visibility = View.GONE
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshAdminDashboard.setColorSchemeColors(
            androidx.core.content.ContextCompat.getColor(this, com.example.jewelrymanagement.R.color.gold_primary),
            androidx.core.content.ContextCompat.getColor(this, com.example.jewelrymanagement.R.color.gold_primary_dark)
        )
        binding.swipeRefreshAdminDashboard.setOnRefreshListener {
            loadDashboardMetrics()
            binding.swipeRefreshAdminDashboard.isRefreshing = false
            android.widget.Toast.makeText(this, "Đã làm mới bảng điều khiển!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupListeners() {
        binding.btnAdminLogout.setOnClickListener {
            showLogoutDialog()
        }

        binding.cardStockAlert.setOnClickListener {
            val intent = Intent(this, ManageProductActivity::class.java)
            intent.putExtra("FILTER_STOCK", "LOW_STOCK")
            startActivity(intent)
        }

        binding.btnNavManageProducts.setOnClickListener {
            startActivity(Intent(this, ManageProductActivity::class.java))
        }

        binding.btnNavManageCategories.setOnClickListener {
            startActivity(Intent(this, ManageCategoryActivity::class.java))
        }

        binding.btnNavManageOrders.setOnClickListener {
            startActivity(Intent(this, ManageOrderActivity::class.java))
        }

        binding.btnNavManageCustomers.setOnClickListener {
            startActivity(Intent(this, ManageCustomerActivity::class.java))
        }

        binding.btnNavStatistics.setOnClickListener {
            startActivity(Intent(this, StatisticsActivity::class.java))
        }

        binding.btnResetSampleData.setOnClickListener {
            showResetDatabaseDialog()
        }

        binding.btnNavManagePromotions.setOnClickListener {
            startActivity(Intent(this, ManagePromotionActivity::class.java))
        }

        binding.btnNavManageCoupons.setOnClickListener {
            startActivity(Intent(this, ManageCouponActivity::class.java))
        }

        binding.btnNavManageReviews.setOnClickListener {
            startActivity(Intent(this, ManageReviewActivity::class.java))
        }

        binding.btnNavExportReport.setOnClickListener {
            startActivity(Intent(this, ExportReportActivity::class.java))
        }
    }

    /**
     * Delivery notifications are local: they are shown on this device. Asking here lets the demo
     * (admin marks an order delivered on the same phone) display the system notification.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun showResetDatabaseDialog() {
        AlertDialog.Builder(this)
            .setTitle("Khôi phục dữ liệu mẫu ban đầu")
            .setMessage("Thao tác này sẽ đặt lại cơ sở dữ liệu về 12 sản phẩm trang sức, 6 danh mục và đơn hàng mặc định ban đầu. Bạn có chắc chắn muốn thực hiện?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Khôi phục") { _, _ ->
                com.example.jewelrymanagement.database.DatabaseHelper.getInstance(this).resetToDefaultData()
                loadDashboardMetrics()
                android.widget.Toast.makeText(this, "Đã khôi phục dữ liệu mẫu mặc định thành công!", android.widget.Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Đăng xuất")
            .setMessage("Bạn có chắc chắn muốn đăng xuất khỏi trang quản trị?")
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
