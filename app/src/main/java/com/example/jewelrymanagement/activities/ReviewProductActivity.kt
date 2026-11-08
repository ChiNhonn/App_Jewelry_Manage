package com.example.jewelrymanagement.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.jewelrymanagement.database.ReviewDao
import com.example.jewelrymanagement.databinding.ActivityReviewProductBinding
import com.example.jewelrymanagement.models.ProductReview
import com.example.jewelrymanagement.utils.SessionManager
import com.example.jewelrymanagement.utils.ProductImageUtils

class ReviewProductActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ORDER_ID = "EXTRA_ORDER_ID"
        const val EXTRA_PROD_ID = "EXTRA_PROD_ID"
        const val EXTRA_PROD_NAME = "EXTRA_PROD_NAME"
        const val EXTRA_PROD_IMAGE = "EXTRA_PROD_IMAGE"

        fun intent(context: Context, orderId: Long, productId: Long, productName: String, imageName: String): Intent =
            Intent(context, ReviewProductActivity::class.java)
                .putExtra(EXTRA_ORDER_ID, orderId)
                .putExtra(EXTRA_PROD_ID, productId)
                .putExtra(EXTRA_PROD_NAME, productName)
                .putExtra(EXTRA_PROD_IMAGE, imageName)
    }

    private lateinit var binding: ActivityReviewProductBinding
    private lateinit var reviewDao: ReviewDao
    private lateinit var sessionManager: SessionManager

    private var orderId: Long = 0
    private var productId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewProductBinding.inflate(layoutInflater)
        setContentView(binding.root)

        reviewDao = ReviewDao(this)
        sessionManager = SessionManager(this)

        orderId = intent.getLongExtra(EXTRA_ORDER_ID, 0)
        productId = intent.getLongExtra(EXTRA_PROD_ID, 0)
        if (orderId <= 0 || productId <= 0 || !sessionManager.isLoggedIn() || sessionManager.isAdmin()) {
            Toast.makeText(this, "Thông tin đơn hàng hoặc sản phẩm không hợp lệ!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val error = reviewDao.eligibilityError(orderId, productId, sessionManager.getUserId())
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupUI(
            intent.getStringExtra(EXTRA_PROD_NAME).orEmpty(),
            intent.getStringExtra(EXTRA_PROD_IMAGE) ?: "img_jewelry_placeholder"
        )
        setupListeners()
    }

    private fun setupUI(productName: String, productImage: String) {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.tvProductNameReview.text = productName
        binding.tvOrderIdReview.text = "Mã đơn hàng: #$orderId"

        ProductImageUtils.load(binding.imgProductReview, productImage)

        binding.ratingBar.setOnRatingBarChangeListener { _, rating, _ ->
            binding.tvRatingDescription.text = when (rating.toInt()) {
                5 -> "Tuyệt vời - Rất hài lòng (5 sao)"
                4 -> "Hài lòng - Chất lượng tốt (4 sao)"
                3 -> "Bình thường - Tạm được (3 sao)"
                2 -> "Không hài lòng (2 sao)"
                1 -> "Rất thất vọng (1 sao)"
                else -> "Chưa chọn số sao"
            }
        }
    }

    private fun setupListeners() {
        binding.btnLater.setOnClickListener { finish() }

        binding.btnSubmitReview.setOnClickListener {
            val rating = binding.ratingBar.rating.toInt()
            if (rating !in 1..5) {
                Toast.makeText(this, "Vui lòng chọn số sao đánh giá từ 1 đến 5!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val comment = binding.etComment.text?.toString()?.trim().orEmpty()
            if (comment.length > 500) {
                binding.tilComment.error = "Nhận xét tối đa 500 ký tự"
                return@setOnClickListener
            }
            binding.tilComment.error = null

            binding.btnSubmitReview.isEnabled = false
            val result = reviewDao.insert(
                ProductReview(
                    productId = productId,
                    orderId = orderId,
                    userId = sessionManager.getUserId(),
                    userName = sessionManager.getUser()?.fullName?.ifBlank { null } ?: "Khách hàng",
                    rating = rating,
                    comment = comment
                )
            )
            binding.btnSubmitReview.isEnabled = true

            if (result.first > 0) {
                setResult(Activity.RESULT_OK)
                AlertDialog.Builder(this)
                    .setTitle("Đánh giá thành công!")
                    .setMessage(ReviewDao.THANK_YOU_MESSAGE)
                    .setCancelable(false)
                    .setPositiveButton("Đóng") { _, _ -> finish() }
                    .show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Không thể gửi đánh giá")
                    .setMessage(result.second)
                    .setPositiveButton("Đóng", null)
                    .show()
            }
        }
    }
}
