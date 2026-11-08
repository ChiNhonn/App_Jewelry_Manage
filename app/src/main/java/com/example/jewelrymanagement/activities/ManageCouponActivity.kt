package com.example.jewelrymanagement.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.jewelrymanagement.adapters.CouponAdapter
import com.example.jewelrymanagement.database.CouponDao
import com.example.jewelrymanagement.databinding.ActivityManageCouponBinding
import com.example.jewelrymanagement.models.Coupon

class ManageCouponActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageCouponBinding
    private lateinit var couponDao: CouponDao
    private lateinit var adapter: CouponAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageCouponBinding.inflate(layoutInflater)
        setContentView(binding.root)

        couponDao = CouponDao(this)

        setupToolbar()
        setupRecyclerView()
        setupFab()

        binding.swipeRefresh.setOnRefreshListener {
            loadCoupons()
        }
    }

    override fun onResume() {
        super.onResume()
        loadCoupons()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = CouponAdapter(
            context = this,
            coupons = emptyList(),
            onEditClick = { coupon ->
                val intent = Intent(this, AddEditCouponActivity::class.java).apply {
                    putExtra(AddEditCouponActivity.EXTRA_COUPON_ID, coupon.id)
                }
                startActivity(intent)
            },
            onDeleteClick = { coupon ->
                confirmDelete(coupon)
            },
            onToggleActive = { coupon, isChecked ->
                val success = couponDao.toggleStatus(coupon.id, isChecked)
                if (success) {
                    Toast.makeText(this, "Đã cập nhật trạng thái coupon!", Toast.LENGTH_SHORT).show()
                    loadCoupons()
                } else {
                    Toast.makeText(this, "Cập nhật trạng thái thất bại!", Toast.LENGTH_SHORT).show()
                }
            }
        )

        binding.rvCoupons.layoutManager = LinearLayoutManager(this)
        binding.rvCoupons.adapter = adapter
    }

    private fun setupFab() {
        binding.btnAddCoupon.setOnClickListener {
            startActivity(Intent(this, AddEditCouponActivity::class.java))
        }
    }

    private fun loadCoupons() {
        binding.swipeRefresh.isRefreshing = true
        val list = couponDao.getAllCoupons()
        adapter.updateData(list)
        binding.layoutEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.swipeRefresh.isRefreshing = false
    }

    private fun confirmDelete(coupon: Coupon) {
        AlertDialog.Builder(this)
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn có chắc chắn muốn xóa mã giảm giá '${coupon.code}' không?")
            .setPositiveButton("Xóa") { _, _ ->
                if (couponDao.delete(coupon.id)) {
                    Toast.makeText(this, "Đã xóa mã giảm giá thành công!", Toast.LENGTH_SHORT).show()
                    loadCoupons()
                } else {
                    Toast.makeText(this, "Xóa mã giảm giá thất bại!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
