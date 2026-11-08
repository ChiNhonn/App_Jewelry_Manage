package com.example.jewelrymanagement.activities

import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.jewelrymanagement.adapters.CategoryAdapter
import com.example.jewelrymanagement.database.CategoryDao
import com.example.jewelrymanagement.databinding.ActivityManageCategoryBinding
import com.example.jewelrymanagement.models.Category

class ManageCategoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageCategoryBinding
    private lateinit var categoryDao: CategoryDao
    private lateinit var adapter: CategoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageCategoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        categoryDao = CategoryDao(this)

        setupRecyclerView()
        setupSwipeRefresh()
        binding.btnBackManageCategory.setOnClickListener { finish() }
        binding.btnAddCategory.setOnClickListener { showAddEditCategoryDialog(null) }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshCategories.setColorSchemeColors(
            androidx.core.content.ContextCompat.getColor(this, com.example.jewelrymanagement.R.color.gold_primary),
            androidx.core.content.ContextCompat.getColor(this, com.example.jewelrymanagement.R.color.gold_primary_dark)
        )
        binding.swipeRefreshCategories.setOnRefreshListener {
            loadCategories()
            binding.swipeRefreshCategories.isRefreshing = false
            android.widget.Toast.makeText(this, "Đã làm mới danh mục trang sức!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadCategories()
    }

    private fun setupRecyclerView() {
        adapter = CategoryAdapter(
            categories = emptyList(),
            isAdminMode = true,
            onItemClick = {},
            onEditClick = { category -> showAddEditCategoryDialog(category) },
            onDeleteClick = { category -> showDeleteCategoryDialog(category) }
        )
        binding.rvManageCategories.layoutManager = LinearLayoutManager(this)
        binding.rvManageCategories.adapter = adapter
    }

    private fun loadCategories() {
        val list = categoryDao.getAll()
        adapter.updateData(list)
        binding.tvCategoryTotalCount.text = "Hiện có ${list.size} danh mục trang sức"
    }

    private fun showAddEditCategoryDialog(category: Category?) {
        val isEdit = category != null
        val title = if (isEdit) "Chỉnh Sửa Danh Mục" else "Thêm Danh Mục Mới"

        val context = this
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val etName = EditText(context).apply {
            hint = "Tên danh mục (ví dụ: Nhẫn Cưới, Lắc Vàng)"
            setText(category?.name ?: "")
            setTextColor(androidx.core.content.ContextCompat.getColor(context, com.example.jewelrymanagement.R.color.text_primary))
            setHintTextColor(androidx.core.content.ContextCompat.getColor(context, com.example.jewelrymanagement.R.color.text_hint))
        }
        val etDesc = EditText(context).apply {
            hint = "Mô tả danh mục"
            setText(category?.description ?: "")
            setTextColor(androidx.core.content.ContextCompat.getColor(context, com.example.jewelrymanagement.R.color.text_primary))
            setHintTextColor(androidx.core.content.ContextCompat.getColor(context, com.example.jewelrymanagement.R.color.text_hint))
        }

        layout.addView(etName)
        layout.addView(etDesc)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton("Lưu") { _, _ ->
                val name = etName.text.toString().trim()
                val desc = etDesc.text.toString().trim()

                if (name.isEmpty()) {
                    Toast.makeText(this, "Tên danh mục không được để trống!", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (isEdit) {
                    val updated = category!!.copy(name = name, description = desc)
                    val success = categoryDao.update(updated)
                    if (success) {
                        Toast.makeText(this, "Cập nhật danh mục thành công!", Toast.LENGTH_SHORT).show()
                        loadCategories()
                    }
                } else {
                    val newCat = Category(name = name, description = desc)
                    val id = categoryDao.insert(newCat)
                    if (id > 0) {
                        Toast.makeText(this, "Thêm danh mục thành công!", Toast.LENGTH_SHORT).show()
                        loadCategories()
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showDeleteCategoryDialog(category: Category) {
        AlertDialog.Builder(this)
            .setTitle("Xác nhận xóa danh mục")
            .setMessage("Bạn có chắc chắn muốn xóa danh mục '${category.name}'?")
            .setPositiveButton("Xóa") { _, _ ->
                val result = categoryDao.delete(category.id)
                Toast.makeText(this, result.second, Toast.LENGTH_LONG).show()
                if (result.first) {
                    loadCategories()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
