package com.example.jewelrymanagement.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.jewelrymanagement.R
import com.example.jewelrymanagement.database.CartDao
import com.example.jewelrymanagement.database.FavoriteDao
import com.example.jewelrymanagement.database.ProductDao
import com.example.jewelrymanagement.database.RatingSummary
import com.example.jewelrymanagement.database.ReviewDao
import com.example.jewelrymanagement.databinding.ActivityProductDetailBinding
import com.example.jewelrymanagement.models.Product
import com.example.jewelrymanagement.models.ProductReview
import com.example.jewelrymanagement.repositories.PricingRepository
import com.example.jewelrymanagement.utils.CompareManager
import com.example.jewelrymanagement.utils.CurrencyUtils
import com.example.jewelrymanagement.utils.DateUtils
import com.example.jewelrymanagement.utils.SessionManager
import com.example.jewelrymanagement.utils.requireLogin
import com.google.android.material.snackbar.Snackbar
import java.util.Locale
import com.example.jewelrymanagement.utils.ProductImageUtils

class ProductDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PRODUCT_ID = "PRODUCT_ID"
    }

    private lateinit var binding: ActivityProductDetailBinding
    private lateinit var productDao: ProductDao
    private lateinit var cartDao: CartDao
    private lateinit var favoriteDao: FavoriteDao
    private lateinit var reviewDao: ReviewDao
    private lateinit var pricing: PricingRepository
    private lateinit var compareManager: CompareManager
    private lateinit var sessionManager: SessionManager

    private var productId: Long = -1
    private var currentProduct: Product? = null
    private var selectedQuantity: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        productDao = ProductDao(this)
        cartDao = CartDao(this)
        favoriteDao = FavoriteDao(this)
        reviewDao = ReviewDao(this)
        pricing = PricingRepository(this)
        compareManager = CompareManager(this)
        sessionManager = SessionManager(this)

        productId = intent.getLongExtra(EXTRA_PRODUCT_ID, -1)
        if (productId <= 0) {
            Toast.makeText(this, "Không tìm thấy thông tin sản phẩm!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setupListeners()
    }

    /** Everything is re-read on resume so stock, price, promotion and reviews are always current. */
    override fun onResume() {
        super.onResume()
        if (productId <= 0) return
        val prod = productDao.getById(productId)
        if (prod == null) {
            Toast.makeText(this, "Sản phẩm không tồn tại hoặc đã bị xóa!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        currentProduct = prod
        bindProduct(prod)
        updateCartBadge()
        updateFavoriteIcon()
        updateCompareButton()
        loadReviewsAndRatings(prod.id)
    }

    private fun bindProduct(prod: Product) {
        binding.tvDetailName.text = prod.name
        binding.tvDetailCategory.text = prod.categoryName.ifBlank { "Chưa phân loại" }
        binding.tvDetailMaterial.text = prod.material.ifBlank { "Chưa có thông tin" }
        binding.tvDetailWeight.text = prod.weight.ifBlank { "Chưa có thông tin" }
        binding.tvDetailDescription.text = prod.description.ifBlank { "Chưa có mô tả cho sản phẩm này." }
        binding.tvDetailExtraSpecs.text = listOf(
            "Loại đá" to prod.gemType,
            "Độ tinh khiết / tuổi vàng" to prod.purity,
            "Kích thước" to prod.size,
            "Bảo hành" to prod.warranty
        ).joinToString("\n") { (label, value) -> "$label: ${value.ifBlank { "Chưa có thông tin" }}" }

        val price = pricing.priceFor(prod)
        binding.tvDetailPrice.text = CurrencyUtils.formatVnd(price.finalPrice)
        val promo = price.promotion
        if (price.hasDiscount && promo != null) {
            binding.tvDetailOriginalPrice.text = CurrencyUtils.formatVnd(price.originalPrice)
            binding.tvDetailOriginalPrice.paintFlags = binding.tvDetailOriginalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            binding.tvDetailOriginalPrice.visibility = View.VISIBLE
            binding.tvDetailPromoBadge.text = "-${price.discountPercent}%"
            binding.tvDetailPromoBadge.visibility = View.VISIBLE
            binding.tvDetailPromoName.text = "* Áp dụng sự kiện: ${promo.name} (${promo.discountLabel()}) đến ${DateUtils.toDisplay(promo.endDate)}"
            binding.tvDetailPromoName.visibility = View.VISIBLE
        } else {
            binding.tvDetailOriginalPrice.visibility = View.GONE
            binding.tvDetailPromoBadge.visibility = View.GONE
            binding.tvDetailPromoName.visibility = View.GONE
        }

        ProductImageUtils.load(binding.imgDetailProduct, prod.imageName)

        val inStock = prod.quantity > 0 && !prod.isDiscontinued
        when {
            prod.isDiscontinued -> setStock("Ngừng kinh doanh", "Sản phẩm đã ngừng kinh doanh", R.color.stock_out)
            prod.quantity <= 0 -> setStock("Hết hàng", "Tạm hết hàng trong kho", R.color.stock_out)
            prod.quantity <= 5 -> setStock("Còn ${prod.quantity} sp (Sắp hết)", "Còn ít hàng trong kho (${prod.quantity} sp)", R.color.stock_low)
            else -> setStock("Còn ${prod.quantity} sp", "Sẵn hàng giao ngay", R.color.stock_in)
        }
        selectedQuantity = if (inStock) selectedQuantity.coerceIn(1, prod.quantity) else 0
        binding.tvDetailQuantity.text = selectedQuantity.toString()
        listOf(binding.btnDetailAddToCart, binding.btnDetailBuyNow).forEach {
            it.isEnabled = inStock
            it.alpha = if (inStock) 1f else 0.5f
        }
        binding.btnDetailIncrease.isEnabled = inStock
        binding.btnDetailDecrease.isEnabled = inStock
    }

    private fun setStock(badge: String, status: String, colorRes: Int) {
        val color = ContextCompat.getColor(this, colorRes)
        binding.tvDetailStockBadge.text = badge
        binding.tvDetailStockBadge.setTextColor(color)
        binding.tvDetailStockStatus.text = status
        binding.tvDetailStockStatus.setTextColor(color)
    }

    private fun updateFavoriteIcon() {
        val userId = sessionManager.getUserId()
        val fav = userId > 0 && favoriteDao.isFavorite(userId, productId)
        binding.btnFavoriteDetail.setImageResource(if (fav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
        binding.btnFavoriteDetail.setColorFilter(ContextCompat.getColor(this, if (fav) R.color.heart_active else R.color.heart_inactive))
    }

    private fun updateCompareButton() {
        val userId = sessionManager.getUserId()
        val count = compareManager.getIds(userId).size
        binding.btnCompareProduct.text = if (compareManager.contains(userId, productId)) {
            "Bỏ khỏi danh sách so sánh ($count/${CompareManager.MAX_ITEMS})"
        } else {
            "Thêm vào so sánh ($count/${CompareManager.MAX_ITEMS})"
        }
    }

    private fun loadReviewsAndRatings(productId: Long) {
        val summary = reviewDao.getRatingSummaryForProduct(productId)
        if (summary.totalReviews > 0) {
            binding.tvAverageRatingScore.text = String.format(Locale.US, "%.1f", summary.averageRating)
            binding.rbDetailAverage.rating = summary.averageRating.toFloat()
            binding.tvTotalReviewsCount.text = "Dựa trên ${summary.totalReviews} lượt đánh giá thực tế"
        } else {
            binding.tvAverageRatingScore.text = "–"
            binding.rbDetailAverage.rating = 0f
            binding.tvTotalReviewsCount.text = "Chưa có lượt đánh giá nào"
        }
        bindStarDistribution(summary)

        val reviews = reviewDao.getReviewsForProduct(productId, onlyVisible = true)
        binding.layoutReviewsList.removeAllViews()
        binding.tvNoReviews.visibility = if (reviews.isEmpty()) View.VISIBLE else View.GONE
        reviews.forEach { binding.layoutReviewsList.addView(createReviewRow(it)) }

        checkReviewEligibility(productId)
    }

    private fun bindStarDistribution(summary: RatingSummary) {
        val container = binding.layoutStarDistribution
        container.removeAllViews()
        if (summary.totalReviews == 0) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE
        val starColor = ContextCompat.getColor(this, R.color.rating_star)
        for (star in 5 downTo 1) {
            val count = summary.starCounts[star] ?: 0
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 2, 0, 2)
            }
            row.addView(TextView(this).apply {
                text = "$star ★"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                layoutParams = LinearLayout.LayoutParams(dp(36), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            row.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = summary.totalReviews
                progress = count
                progressTintList = ColorStateList.valueOf(starColor)
                layoutParams = LinearLayout.LayoutParams(0, dp(8), 1f)
            })
            row.addView(TextView(this).apply {
                text = count.toString()
                textSize = 12f
                gravity = Gravity.END
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                layoutParams = LinearLayout.LayoutParams(dp(32), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            container.addView(row)
        }
    }

    private fun createReviewRow(review: ProductReview): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        row.addView(TextView(this).apply {
            text = "${review.userName} • ${"★".repeat(review.rating)}${"☆".repeat(5 - review.rating)}"
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setTypeface(null, Typeface.BOLD)
            textSize = 14f
        })
        row.addView(TextView(this).apply {
            text = DateUtils.toDisplay(review.createdAt)
            setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
            textSize = 11f
        })
        if (review.comment.isNotBlank()) {
            row.addView(TextView(this).apply {
                text = review.comment
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 13f
            })
        }
        return row
    }

    private fun checkReviewEligibility(productId: Long) {
        val userId = sessionManager.getUserId()
        val orderId = if (userId > 0 && !sessionManager.isAdmin()) reviewDao.findReviewableOrderForProduct(userId, productId) else null
        if (orderId == null) {
            binding.btnWriteReview.visibility = View.GONE
            return
        }
        binding.btnWriteReview.visibility = View.VISIBLE
        binding.btnWriteReview.setOnClickListener {
            val prod = currentProduct ?: return@setOnClickListener
            startActivity(ReviewProductActivity.intent(this, orderId, prod.id, prod.name, prod.imageName))
        }
    }

    private fun updateCartBadge() {
        val count = if (sessionManager.isAdmin()) 0 else cartDao.getCartCount(sessionManager.getShopperId())
        binding.tvDetailCartBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
        binding.tvDetailCartBadge.text = if (count > 99) "99+" else count.toString()
    }

    private fun setupListeners() {
        binding.btnBackDetail.setOnClickListener { finish() }
        binding.btnDetailCart.setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }

        binding.btnFavoriteDetail.setOnClickListener {
            val prod = currentProduct ?: return@setOnClickListener
            if (!requireLogin("Vui lòng đăng nhập để lưu sản phẩm yêu thích")) return@setOnClickListener
            val userId = sessionManager.getUserId()
            val nowFavorite = favoriteDao.toggleFavorite(userId, prod.id)
            updateFavoriteIcon()
            Toast.makeText(
                this,
                if (nowFavorite) "Đã thêm '${prod.name}' vào yêu thích!" else "Đã xóa khỏi danh sách yêu thích!",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnCompareDetail.setOnClickListener { startActivity(Intent(this, CompareProductsActivity::class.java)) }
        binding.btnCompareProduct.setOnClickListener { toggleCompare() }

        binding.btnDetailIncrease.setOnClickListener {
            val prod = currentProduct ?: return@setOnClickListener
            if (selectedQuantity < prod.quantity) {
                selectedQuantity++
                binding.tvDetailQuantity.text = selectedQuantity.toString()
            } else {
                Toast.makeText(this, "Số lượng đã đạt giới hạn tồn kho (${prod.quantity})!", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnDetailDecrease.setOnClickListener {
            if (selectedQuantity > 1) {
                selectedQuantity--
                binding.tvDetailQuantity.text = selectedQuantity.toString()
            }
        }
        binding.btnDetailAddToCart.setOnClickListener { addToCart(openCart = false) }
        binding.btnDetailBuyNow.setOnClickListener { addToCart(openCart = true) }
    }

    private fun toggleCompare() {
        val userId = sessionManager.getUserId()
        if (compareManager.contains(userId, productId)) {
            compareManager.remove(userId, productId)
            Toast.makeText(this, "Đã bỏ sản phẩm khỏi danh sách so sánh.", Toast.LENGTH_SHORT).show()
            updateCompareButton()
            return
        }
        val message = when (compareManager.add(userId, productId)) {
            CompareManager.AddResult.ADDED -> "Đã thêm vào so sánh (${compareManager.getIds(userId).size}/${CompareManager.MAX_ITEMS})."
            CompareManager.AddResult.ALREADY_ADDED -> "Sản phẩm đã có trong danh sách so sánh."
            CompareManager.AddResult.FULL -> "Chỉ so sánh tối đa ${CompareManager.MAX_ITEMS} sản phẩm. Hãy bỏ bớt một sản phẩm trước."
        }
        updateCompareButton()
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("So sánh") { startActivity(Intent(this, CompareProductsActivity::class.java)) }
            .show()
    }

    private fun addToCart(openCart: Boolean) {
        val prod = currentProduct ?: return
        if (sessionManager.isLoggedIn() && sessionManager.isAdmin()) {
            Toast.makeText(this, "Vui lòng đăng nhập tài khoản khách hàng để mua sắm!", Toast.LENGTH_SHORT).show()
            return
        }
        val result = cartDao.addToCart(sessionManager.getShopperId(), prod.id, selectedQuantity)
        Toast.makeText(this, result.second, Toast.LENGTH_SHORT).show()
        if (result.first) {
            updateCartBadge()
            if (openCart) startActivity(Intent(this, CartActivity::class.java))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
