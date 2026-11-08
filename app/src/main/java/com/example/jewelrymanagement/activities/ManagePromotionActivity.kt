package com.example.jewelrymanagement.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.jewelrymanagement.R
import com.example.jewelrymanagement.adapters.PromotionAdapter
import com.example.jewelrymanagement.database.PromotionDao
import com.example.jewelrymanagement.databinding.ActivityManagePromotionBinding
import com.example.jewelrymanagement.models.Promotion
import com.example.jewelrymanagement.utils.DateUtils

class ManagePromotionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManagePromotionBinding
    private lateinit var promotionDao: PromotionDao
    private lateinit var adapter: PromotionAdapter
    private var allPromotions: List<Promotion> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManagePromotionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        promotionDao = PromotionDao(this)

        setupToolbar()
        setupRecyclerView()
        setupFilters()
        setupFab()

        binding.swipeRefresh.setOnRefreshListener {
            loadPromotions()
        }
    }

    override fun onResume() {
        super.onResume()
        loadPromotions()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = PromotionAdapter(
            context = this,
            promotions = emptyList(),
            onEditClick = { promo ->
                val intent = Intent(this, AddEditPromotionActivity::class.java).apply {
                    putExtra("EXTRA_PROMO_ID", promo.id)
                }
                startActivity(intent)
            },
            onDeleteClick = { promo ->
                confirmDelete(promo)
            },
            onToggleActive = { promo, isChecked ->
                val success = promotionDao.toggleStatus(promo.id, isChecked)
                if (success) {
                    Toast.makeText(this, "Đã cập nhật trạng thái sự kiện!", Toast.LENGTH_SHORT).show()
                    loadPromotions()
                } else {
                    Toast.makeText(this, "Cập nhật trạng thái thất bại!", Toast.LENGTH_SHORT).show()
                }
            },
            onItemClick = { showDetail(it) }
        )

        binding.rvPromotions.layoutManager = LinearLayoutManager(this)
        binding.rvPromotions.adapter = adapter
    }

    private fun setupFilters() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            applyFilter()
        }
    }

    private fun setupFab() {
        binding.btnAddPromotion.setOnClickListener {
            startActivity(Intent(this, AddEditPromotionActivity::class.java))
        }
    }

    private fun loadPromotions() {
        binding.swipeRefresh.isRefreshing = true
        allPromotions = promotionDao.getAllPromotions()
        applyFilter()
        binding.swipeRefresh.isRefreshing = false
    }

    private fun applyFilter() {
        val filtered = when {
            binding.chipActive.isChecked -> allPromotions.filter { it.getStatus() == "Đang diễn ra" }
            binding.chipUpcoming.isChecked -> allPromotions.filter { it.getStatus() == "Sắp diễn ra" }
            binding.chipEnded.isChecked -> allPromotions.filter { it.getStatus() == "Đã kết thúc" }
            else -> allPromotions
        }

        adapter.updateData(filtered)
        binding.layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showDetail(promo: Promotion) {
        val target = when (promo.targetType) {
            Promotion.TARGET_CATEGORY -> "Danh mục: ${promo.targetNames.joinToString(", ")}"
            Promotion.TARGET_PRODUCT -> "Sản phẩm: ${promo.targetNames.joinToString(", ")}"
            else -> "Toàn bộ sản phẩm"
        }
        val message = """
            Mã: ${promo.code}
            Trạng thái: ${promo.getStatus()}
            Mức giảm: ${promo.discountLabel()}
            Áp dụng: $target
            Bắt đầu: ${DateUtils.toDisplay(promo.startDate)}
            Kết thúc: ${DateUtils.toDisplay(promo.endDate)}
            Mô tả: ${promo.description.ifBlank { "(không có)" }}

            Mỗi sản phẩm chỉ áp dụng một sự kiện có mức giảm cao nhất; giá gốc sản phẩm không bị thay đổi.
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle(promo.name)
            .setMessage(message)
            .setPositiveButton("Đóng", null)
            .setNeutralButton("Chỉnh sửa") { _, _ ->
                startActivity(Intent(this, AddEditPromotionActivity::class.java).putExtra(AddEditPromotionActivity.EXTRA_PROMO_ID, promo.id))
            }
            .show()
    }

    private fun confirmDelete(promo: Promotion) {
        AlertDialog.Builder(this)
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn có chắc chắn muốn xóa sự kiện khuyến mãi '${promo.name}' không?")
            .setPositiveButton("Xóa") { _, _ ->
                if (promotionDao.delete(promo.id)) {
                    Toast.makeText(this, "Đã xóa sự kiện thành công!", Toast.LENGTH_SHORT).show()
                    loadPromotions()
                } else {
                    Toast.makeText(this, "Xóa sự kiện thất bại!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
