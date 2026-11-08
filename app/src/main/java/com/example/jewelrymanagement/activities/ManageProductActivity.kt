package com.example.jewelrymanagement.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.jewelrymanagement.R
import com.example.jewelrymanagement.adapters.AdminProductAdapter
import com.example.jewelrymanagement.database.CategoryDao
import com.example.jewelrymanagement.database.ProductDao
import com.example.jewelrymanagement.databinding.ActivityManageProductBinding
import com.example.jewelrymanagement.models.Category
import com.example.jewelrymanagement.models.Product

class ManageProductActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageProductBinding
    private lateinit var productDao: ProductDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var adapter: AdminProductAdapter

    private var categoriesList = mutableListOf<Category>()
    private var selectedCategoryId: Long = 0
    private var selectedStockFilter: String = "ALL" // "ALL", "IN_STOCK", "OUT_OF_STOCK", "LOW_STOCK"
    private var currentSearchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageProductBinding.inflate(layoutInflater)
        setContentView(binding.root)

        productDao = ProductDao(this)
        categoryDao = CategoryDao(this)

        setupRecyclerView()
        setupSpinners()
        setupSearch()
        setupSwipeRefresh()
        setupListeners()

        val extraStock = intent.getStringExtra("FILTER_STOCK")
        if (extraStock == "LOW_STOCK") {
            selectedStockFilter = "LOW_STOCK"
            binding.spAdminFilterStock.setSelection(3)
        }
    }

    override fun onResume() {
        super.onResume()
        loadCategories()
        applyFilters()
    }

    private fun setupRecyclerView() {
        adapter = AdminProductAdapter(
            products = emptyList(),
            onEditClick = { product ->
                val intent = Intent(this, AddEditProductActivity::class.java)
                intent.putExtra("PRODUCT_EXTRA", product)
                startActivity(intent)
            },
            onDeleteClick = { product ->
                showDeleteDialog(product)
            },
            onItemClick = { product ->
                val intent = Intent(this, ProductDetailActivity::class.java)
                intent.putExtra("PRODUCT_ID", product.id)
                startActivity(intent)
            }
        )
        binding.rvAdminProducts.layoutManager = LinearLayoutManager(this)
        binding.rvAdminProducts.adapter = adapter
    }

    private fun setupSpinners() {
        // Stock Filter Spinner
        val stockOptions = listOf("Tất cả tồn kho", "Còn hàng", "Hết hàng", "Sắp hết hàng (<=5)")
        val stockAdapter = ArrayAdapter(this, R.layout.item_spinner_compact, stockOptions)
        stockAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        binding.spAdminFilterStock.adapter = stockAdapter
        binding.spAdminFilterStock.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedStockFilter = when (position) {
                    1 -> "IN_STOCK"
                    2 -> "OUT_OF_STOCK"
                    3 -> "LOW_STOCK"
                    else -> "ALL"
                }
                applyFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadCategories() {
        categoriesList = mutableListOf(Category(id = 0, name = "Tất cả danh mục", description = ""))
        categoriesList.addAll(categoryDao.getAll())

        val catAdapter = ArrayAdapter(this, R.layout.item_spinner_compact, categoriesList)
        catAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        binding.spAdminFilterCategory.adapter = catAdapter
        binding.spAdminFilterCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCategoryId = categoriesList[position].id
                applyFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshAdminProduct.setColorSchemeColors(
            androidx.core.content.ContextCompat.getColor(this, R.color.gold_primary),
            androidx.core.content.ContextCompat.getColor(this, R.color.gold_primary_dark)
        )
        binding.swipeRefreshAdminProduct.setOnRefreshListener {
            loadCategories()
            applyFilters()
            binding.swipeRefreshAdminProduct.isRefreshing = false
            Toast.makeText(this, "Đã làm mới danh sách sản phẩm!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetFilters() {
        binding.etSearchAdminProduct.setText("")
        binding.spAdminFilterCategory.setSelection(0)
        binding.spAdminFilterStock.setSelection(0)
        selectedCategoryId = 0
        selectedStockFilter = "ALL"
        currentSearchQuery = ""
        applyFilters()
        Toast.makeText(this, "Đã đặt lại bộ lọc tìm kiếm", Toast.LENGTH_SHORT).show()
    }

    private fun setupSearch() {
        binding.btnClearSearchAdminProduct.setOnClickListener {
            binding.etSearchAdminProduct.setText("")
        }

        binding.etSearchAdminProduct.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                binding.btnClearSearchAdminProduct.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun applyFilters() {
        val filtered = productDao.searchAndFilter(
            nameQuery = currentSearchQuery,
            categoryId = selectedCategoryId,
            stockFilter = selectedStockFilter
        )
        adapter.updateData(filtered)
        binding.tvAdminProductCount.text = "Hiển thị ${filtered.size} sản phẩm"

        if (filtered.isEmpty()) {
            binding.layoutAdminEmptyProducts.visibility = View.VISIBLE
            binding.rvAdminProducts.visibility = View.GONE
        } else {
            binding.layoutAdminEmptyProducts.visibility = View.GONE
            binding.rvAdminProducts.visibility = View.VISIBLE
        }
    }

    private fun setupListeners() {
        binding.btnBackManageProduct.setOnClickListener { finish() }

        binding.btnResetAdminProductFilter.setOnClickListener { resetFilters() }
        binding.btnResetAdminProductEmpty.setOnClickListener { resetFilters() }

        binding.btnAddProduct.setOnClickListener {
            startActivity(Intent(this, AddEditProductActivity::class.java))
        }
    }

    private fun showDeleteDialog(product: Product) {
        AlertDialog.Builder(this)
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn có chắc chắn muốn xóa sản phẩm '${product.name}'?")
            .setPositiveButton("Xóa") { _, _ ->
                val result = productDao.delete(product.id)
                Toast.makeText(this, result.second, Toast.LENGTH_LONG).show()
                applyFilters()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
