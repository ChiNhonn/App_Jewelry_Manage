package com.example.jewelrymanagement.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.jewelrymanagement.R
import com.example.jewelrymanagement.adapters.ProductAdapter
import com.example.jewelrymanagement.database.CartDao
import com.example.jewelrymanagement.database.CategoryDao
import com.example.jewelrymanagement.database.FavoriteDao
import com.example.jewelrymanagement.database.NotificationDao
import com.example.jewelrymanagement.database.ProductDao
import com.example.jewelrymanagement.database.UserDao
import com.example.jewelrymanagement.databinding.ActivityCustomerBinding
import com.example.jewelrymanagement.models.Category
import com.example.jewelrymanagement.models.Product
import com.example.jewelrymanagement.repositories.PricingRepository
import com.example.jewelrymanagement.utils.LocationAddressHelper
import com.example.jewelrymanagement.utils.SessionManager
import com.example.jewelrymanagement.utils.requireLogin
import com.example.jewelrymanagement.utils.showEnableLocationDialog
import kotlinx.coroutines.launch

class CustomerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomerBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var productDao: ProductDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var cartDao: CartDao
    private lateinit var favoriteDao: FavoriteDao
    private lateinit var notificationDao: NotificationDao
    private lateinit var pricing: PricingRepository
    private lateinit var adapter: ProductAdapter

    private var categoriesList = mutableListOf<Category>()
    private var selectedCategoryId: Long = 0
    private var selectedSort: String = "NONE" // "NONE", "ASC", "DESC"
    private var isOnlyInStock: Boolean = false
    private var searchQuery: String = ""
    private var retryLocationOnResume = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "Bạn vẫn xem được thông báo trong mục Thông báo của ứng dụng.", Toast.LENGTH_LONG).show()
            }
        }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.any { it }) {
                detectAddress(announce = true)
            } else {
                Toast.makeText(this, "Bạn có thể tự nhập địa chỉ hoặc bật vị trí lại ở trang thanh toán.", Toast.LENGTH_LONG).show()
            }
            requestNotificationPermissionIfNeeded()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        if (sessionManager.isLoggedIn() && sessionManager.isAdmin()) {
            startActivity(Intent(this, AdminActivity::class.java))
            finish()
            return
        }

        productDao = ProductDao(this)
        categoryDao = CategoryDao(this)
        cartDao = CartDao(this)
        favoriteDao = FavoriteDao(this)
        notificationDao = NotificationDao(this)
        pricing = PricingRepository(this)

        setupRecyclerView()
        setupSpinners()
        setupSearchAndFilter()
        setupSwipeRefresh()
        setupNavigation()
        if (savedInstanceState == null) startStartupPermissionFlow()
    }

    private fun startStartupPermissionFlow() {
        when {
            LocationAddressHelper.hasPermission(this) -> {
                detectAddress(announce = false)
                requestNotificationPermissionIfNeeded()
            }
            LocationAddressHelper.getSavedAddress(this).isBlank() -> showLocationPrompt()
            else -> requestNotificationPermissionIfNeeded()
        }
    }

    private fun showLocationPrompt() {
        AlertDialog.Builder(this)
            .setIcon(R.drawable.ic_my_location)
            .setTitle("Bật vị trí?")
            .setMessage("Cho phép ứng dụng dùng vị trí hiện tại để tự động điền sẵn địa chỉ giao hàng khi bạn đặt hàng.\n\nBạn vẫn có thể sửa địa chỉ trước khi xác nhận đơn.")
            .setCancelable(false)
            .setPositiveButton("Cho phép") { _, _ -> locationPermissionLauncher.launch(LocationAddressHelper.PERMISSIONS) }
            .setNegativeButton("Để sau") { _, _ -> requestNotificationPermissionIfNeeded() }
            .show()
    }

    private fun detectAddress(announce: Boolean) {
        lifecycleScope.launch {
            val result = LocationAddressHelper.detectAddress(this@CustomerActivity)
            if (!announce) return@launch
            when (result) {
                is LocationAddressHelper.Result.Success ->
                    Toast.makeText(this@CustomerActivity, "Đã lưu địa chỉ: ${result.address}", Toast.LENGTH_LONG).show()
                LocationAddressHelper.Result.LocationDisabled -> {
                    retryLocationOnResume = true
                    showEnableLocationDialog()
                }
                else -> Toast.makeText(
                    this@CustomerActivity,
                    "Chưa lấy được địa chỉ lúc này. Bạn có thể thử lại ở trang thanh toán.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::adapter.isInitialized) return
        if (sessionManager.isLoggedIn() && !sessionManager.isAdmin() && UserDao(this).isLocked(sessionManager.getUserId())) {
            sessionManager.clearSession()
            Toast.makeText(this, "Tài khoản đã bị khóa. Bạn vẫn xem được cửa hàng.", Toast.LENGTH_LONG).show()
        }
        if (retryLocationOnResume && LocationAddressHelper.isLocationEnabled(this)) {
            retryLocationOnResume = false
            detectAddress(announce = true)
        }
        updateHeaderBadges()
        loadCategories()
        applyFilters()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (sessionManager.isLoggedIn() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun updateHeaderBadges() {
        val user = sessionManager.getUser()
        binding.tvCustomerGreeting.text = "Xin chào, ${user?.fullName ?: "Quý Khách"}"

        val userId = sessionManager.getUserId()
        val cartCount = cartDao.getCartCount(sessionManager.getShopperId())
        binding.tvCartBadgeCount.visibility = if (cartCount > 0) View.VISIBLE else View.GONE
        binding.tvCartBadgeCount.text = if (cartCount > 99) "99+" else cartCount.toString()

        val unread = notificationDao.getUnreadCount(userId)
        binding.tvNotificationBadgeCount.visibility = if (unread > 0) View.VISIBLE else View.GONE
        binding.tvNotificationBadgeCount.text = if (unread > 99) "99+" else unread.toString()
    }

    private fun setupRecyclerView() {
        adapter = ProductAdapter(
            products = emptyList(),
            onItemClick = { product ->
                startActivity(Intent(this, ProductDetailActivity::class.java).putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, product.id))
            },
            onQuickAddToCart = { quickAddToCart(it) },
            onFavoriteClick = { toggleFavorite(it) }
        )
        binding.rvCustomerProducts.layoutManager = GridLayoutManager(this, 2)
        binding.rvCustomerProducts.adapter = adapter
    }

    private fun toggleFavorite(product: Product) {
        if (!requireLogin("Vui lòng đăng nhập để lưu sản phẩm yêu thích")) return
        val nowFavorite = favoriteDao.toggleFavorite(sessionManager.getUserId(), product.id)
        Toast.makeText(
            this,
            if (nowFavorite) "Đã thêm '${product.name}' vào yêu thích!" else "Đã bỏ '${product.name}' khỏi yêu thích.",
            Toast.LENGTH_SHORT
        ).show()
        applyFilters()
    }

    private fun quickAddToCart(product: Product) {
        val result = cartDao.addToCart(sessionManager.getShopperId(), product.id, 1)
        Toast.makeText(this, result.second, Toast.LENGTH_SHORT).show()
        if (result.first) updateHeaderBadges()
    }

    private fun setupSpinners() {
        val sortOptions = listOf("Mới nhất", "Giá tăng dần", "Giá giảm dần")
        val sortAdapter = ArrayAdapter(this, R.layout.item_spinner_compact, sortOptions)
        sortAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        binding.spCustomerSortPrice.adapter = sortAdapter
        binding.spCustomerSortPrice.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSort = when (position) {
                    1 -> "ASC"
                    2 -> "DESC"
                    else -> "NONE"
                }
                applyFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadCategories() {
        categoriesList = mutableListOf(Category(id = 0, name = "Tất cả danh mục", description = ""))
        categoriesList.addAll(categoryDao.getAll())
        if (categoriesList.none { it.id == selectedCategoryId }) selectedCategoryId = 0

        val catAdapter = ArrayAdapter(this, R.layout.item_spinner_compact, categoriesList)
        catAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        binding.spCustomerCategory.adapter = catAdapter
        binding.spCustomerCategory.setSelection(categoriesList.indexOfFirst { it.id == selectedCategoryId }.coerceAtLeast(0), false)
        binding.spCustomerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newId = categoriesList[position].id
                if (newId != selectedCategoryId) {
                    selectedCategoryId = newId
                    applyFilters()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshCustomer.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.gold_primary),
            ContextCompat.getColor(this, R.color.gold_primary_dark)
        )
        binding.swipeRefreshCustomer.setOnRefreshListener {
            loadCategories()
            applyFilters()
            updateHeaderBadges()
            binding.swipeRefreshCustomer.isRefreshing = false
        }
    }

    private fun resetFilters() {
        binding.etSearchCustomerShop.setText("")
        binding.spCustomerCategory.setSelection(0)
        binding.spCustomerSortPrice.setSelection(0)
        binding.cbInStockOnly.isChecked = false
        selectedCategoryId = 0
        selectedSort = "NONE"
        isOnlyInStock = false
        searchQuery = ""
        applyFilters()
        Toast.makeText(this, "Đã đặt lại bộ lọc tìm kiếm", Toast.LENGTH_SHORT).show()
    }

    private fun setupSearchAndFilter() {
        binding.btnClearSearchCustomer.setOnClickListener { binding.etSearchCustomerShop.setText("") }
        binding.btnResetCustomerFilter.setOnClickListener { resetFilters() }
        binding.btnResetEmptyCustomer.setOnClickListener { resetFilters() }

        binding.etSearchCustomerShop.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                searchQuery = s?.toString()?.trim().orEmpty()
                binding.btnClearSearchCustomer.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.cbInStockOnly.setOnCheckedChangeListener { _, isChecked ->
            isOnlyInStock = isChecked
            applyFilters()
        }
    }

    /** Sorting by price uses the price the customer actually pays (after promotion). */
    private fun applyFilters() {
        val list = productDao.searchAndFilter(
            nameQuery = searchQuery,
            categoryId = selectedCategoryId,
            stockFilter = if (isOnlyInStock) "IN_STOCK" else "ALL"
        )
        val prices = pricing.pricesFor(list)
        val sorted = when (selectedSort) {
            "ASC" -> list.sortedBy { prices[it.id]?.finalPrice ?: it.price }
            "DESC" -> list.sortedByDescending { prices[it.id]?.finalPrice ?: it.price }
            else -> list
        }
        adapter.updateData(sorted, prices, favoriteDao.getFavoriteIds(sessionManager.getUserId()))
        binding.tvCustomerProdCount.text = "${sorted.size} sản phẩm trang sức"
        binding.layoutCustomerEmpty.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
        binding.rvCustomerProducts.visibility = if (sorted.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun setupNavigation() {
        binding.btnNavCart.setOnClickListener { openCart() }
        binding.btnNavNotifications.setOnClickListener {
            if (requireLogin("Vui lòng đăng nhập để xem thông báo")) startActivity(Intent(this, NotificationActivity::class.java))
        }
        binding.btnNavFavorites.setOnClickListener {
            if (requireLogin("Vui lòng đăng nhập để xem sản phẩm yêu thích")) startActivity(Intent(this, FavoriteProductsActivity::class.java))
        }
        binding.btnNavCompare.setOnClickListener { startActivity(Intent(this, CompareProductsActivity::class.java)) }
        binding.navBottomHome.setOnClickListener { resetFilters() }
        binding.navBottomOrders.setOnClickListener {
            if (requireLogin("Vui lòng đăng nhập để xem đơn hàng")) startActivity(Intent(this, OrderHistoryActivity::class.java))
        }
        binding.navBottomCart.setOnClickListener { openCart() }
        binding.navBottomProfile.setOnClickListener {
            if (requireLogin("Đăng nhập hoặc đăng ký để quản lý tài khoản")) startActivity(Intent(this, CustomerProfileActivity::class.java))
        }
    }

    private fun openCart() {
        startActivity(Intent(this, CartActivity::class.java))
    }
}
