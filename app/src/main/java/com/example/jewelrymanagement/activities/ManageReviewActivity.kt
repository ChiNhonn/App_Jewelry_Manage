package com.example.jewelrymanagement.activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.jewelrymanagement.adapters.AdminReviewAdapter
import com.example.jewelrymanagement.database.ReviewDao
import com.example.jewelrymanagement.databinding.ActivityManageReviewBinding

class ManageReviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageReviewBinding
    private lateinit var reviewDao: ReviewDao
    private lateinit var adapter: AdminReviewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        reviewDao = ReviewDao(this)

        setupToolbar()
        setupRecyclerView()

        binding.swipeRefresh.setOnRefreshListener {
            loadReviews()
        }
    }

    override fun onResume() {
        super.onResume()
        loadReviews()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = AdminReviewAdapter(
            context = this,
            reviews = emptyList(),
            onToggleVisibility = { review, isVisible ->
                val ok = reviewDao.toggleVisibility(review.id, isVisible)
                if (ok) {
                    val statusText = if (isVisible) "hiển thị" else "ẩn"
                    Toast.makeText(this, "Đã $statusText nhận xét!", Toast.LENGTH_SHORT).show()
                    loadReviews()
                } else {
                    Toast.makeText(this, "Lỗi khi cập nhật trạng thái nhận xét!", Toast.LENGTH_SHORT).show()
                }
            }
        )

        binding.rvAdminReviews.layoutManager = LinearLayoutManager(this)
        binding.rvAdminReviews.adapter = adapter
    }

    private fun loadReviews() {
        binding.swipeRefresh.isRefreshing = true
        val list = reviewDao.getAllReviews()
        adapter.updateData(list)
        binding.layoutEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.swipeRefresh.isRefreshing = false
    }
}
