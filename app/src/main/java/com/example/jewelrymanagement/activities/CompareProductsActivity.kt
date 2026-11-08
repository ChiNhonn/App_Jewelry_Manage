package com.example.jewelrymanagement.activities

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.jewelrymanagement.R
import com.example.jewelrymanagement.database.CartDao
import com.example.jewelrymanagement.database.ProductDao
import com.example.jewelrymanagement.database.ReviewDao
import com.example.jewelrymanagement.databinding.ActivityCompareProductsBinding
import com.example.jewelrymanagement.models.Product
import com.example.jewelrymanagement.repositories.PriceInfo
import com.example.jewelrymanagement.repositories.PricingRepository
import com.example.jewelrymanagement.utils.CompareManager
import com.example.jewelrymanagement.utils.CurrencyUtils
import com.example.jewelrymanagement.utils.SessionManager
import com.google.android.material.button.MaterialButton
import java.util.Locale
import com.example.jewelrymanagement.utils.ProductImageUtils

class CompareProductsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompareProductsBinding
    private lateinit var productDao: ProductDao
    private lateinit var reviewDao: ReviewDao
    private lateinit var cartDao: CartDao
    private lateinit var pricing: PricingRepository
    private lateinit var compareManager: CompareManager
    private lateinit var sessionManager: SessionManager

    private val missing = "Chưa có thông tin"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompareProductsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        productDao = ProductDao(this)
        reviewDao = ReviewDao(this)
        cartDao = CartDao(this)
        pricing = PricingRepository(this)
        compareManager = CompareManager(this)
        sessionManager = SessionManager(this)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnClearAll.setOnClickListener {
            if (compareManager.getIds(userId()).isEmpty()) return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("Xóa danh sách so sánh")
                .setMessage("Bỏ tất cả sản phẩm khỏi danh sách so sánh?")
                .setPositiveButton("Xóa tất cả") { _, _ ->
                    compareManager.clear(userId())
                    render()
                }
                .setNegativeButton("Hủy", null)
                .show()
        }
        binding.btnAddProductToCompare.setOnClickListener { showAddProductDialog() }
        binding.btnEmptyAddProduct.setOnClickListener { showAddProductDialog() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun userId() = sessionManager.getUserId()

    private fun showAddProductDialog() {
        val ids = compareManager.getIds(userId())
        if (ids.size >= CompareManager.MAX_ITEMS) {
            Toast.makeText(this, "Chỉ có thể so sánh tối đa ${CompareManager.MAX_ITEMS} sản phẩm cùng lúc!", Toast.LENGTH_SHORT).show()
            return
        }
        val candidates = productDao.getAllProducts().filter { it.id !in ids && !it.isDiscontinued }
        if (candidates.isEmpty()) {
            Toast.makeText(this, "Không còn sản phẩm nào khác để thêm!", Toast.LENGTH_SHORT).show()
            return
        }
        val prices = pricing.pricesFor(candidates)
        val names = candidates.map { "${it.name} (${CurrencyUtils.formatVnd(prices[it.id]?.finalPrice ?: it.price)})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Chọn sản phẩm để so sánh")
            .setItems(names) { _, which ->
                compareManager.add(userId(), candidates[which].id)
                render()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun render() {
        val ids = compareManager.getIds(userId())
        val products = productDao.getByIds(ids)
        compareManager.retainOnly(userId(), products.map { it.id })

        binding.layoutComparisonColumns.removeAllViews()
        if (products.isEmpty()) {
            binding.scrollCompareContent.visibility = View.GONE
            binding.layoutEmptyCompare.visibility = View.VISIBLE
            return
        }
        binding.scrollCompareContent.visibility = View.VISIBLE
        binding.layoutEmptyCompare.visibility = View.GONE
        binding.btnAddProductToCompare.isEnabled = products.size < CompareManager.MAX_ITEMS
        binding.btnAddProductToCompare.text = "+ Thêm sản phẩm (${products.size}/${CompareManager.MAX_ITEMS})"
        binding.tvCategoryWarning.visibility = if (products.map { it.categoryId }.distinct().size > 1) View.VISIBLE else View.GONE

        val prices = pricing.pricesFor(products)
        val ratings = reviewDao.getRatingsForProducts()
        binding.layoutComparisonColumns.addView(buildTable(products, prices, ratings))
    }

    private fun buildTable(products: List<Product>, prices: Map<Long, PriceInfo>, ratings: Map<Long, Pair<Double, Int>>): TableLayout {
        val table = TableLayout(this)

        table.addView(TableRow(this).apply {
            addView(labelCell(""))
            products.forEach { addView(headerCell(it)) }
        })

        val rows: List<Pair<String, (Product) -> String>> = listOf(
            "Tên sản phẩm" to { p -> p.name },
            "Danh mục" to { p -> p.categoryName.ifBlank { missing } },
            "Giá gốc" to { p -> CurrencyUtils.formatVnd(p.price) },
            "Giá áp dụng" to { p -> CurrencyUtils.formatVnd(prices[p.id]?.finalPrice ?: p.price) },
            "Giảm giá" to { p ->
                val info = prices[p.id]
                if (info != null && info.hasDiscount) "-${info.discountPercent}% (${info.promotion?.name})" else "Không có"
            },
            "Chất liệu" to { p -> p.material.ifBlank { missing } },
            "Tuổi vàng / độ tinh khiết" to { p -> p.purity.ifBlank { missing } },
            "Trọng lượng" to { p -> p.weight.ifBlank { missing } },
            "Loại đá" to { p -> p.gemType.ifBlank { missing } },
            "Kích thước" to { p -> p.size.ifBlank { missing } },
            "Đánh giá" to { p ->
                val r = ratings[p.id]
                if (r == null || r.second == 0) "Chưa có đánh giá" else String.format(Locale.US, "%.1f ★ (%d)", r.first, r.second)
            },
            "Tồn kho" to { p ->
                when {
                    p.isDiscontinued -> "Ngừng kinh doanh"
                    p.quantity > 0 -> "Còn ${p.quantity} sp"
                    else -> "Hết hàng"
                }
            },
            "Bảo hành" to { p -> p.warranty.ifBlank { missing } }
        )

        rows.forEachIndexed { index, (label, value) ->
            table.addView(TableRow(this).apply {
                if (index % 2 == 0) setBackgroundColor(ContextCompat.getColor(context, R.color.gold_pale))
                addView(labelCell(label))
                products.forEach { p ->
                    val text = value(p)
                    addView(valueCell(text, highlight = label == "Giá áp dụng", muted = text == missing))
                }
            })
        }

        table.addView(TableRow(this).apply {
            addView(labelCell(""))
            products.forEach { addView(actionCell(it)) }
        })
        return table
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun labelCell(text: String) = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTypeface(null, Typeface.BOLD)
        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        setPadding(dp(8), dp(8), dp(8), dp(8))
        layoutParams = TableRow.LayoutParams(dp(110), TableRow.LayoutParams.MATCH_PARENT)
    }

    private fun valueCell(text: String, highlight: Boolean, muted: Boolean) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setPadding(dp(8), dp(8), dp(8), dp(8))
        val color = when {
            highlight -> R.color.gold_primary_dark
            muted -> R.color.text_tertiary
            else -> R.color.text_primary
        }
        setTextColor(ContextCompat.getColor(context, color))
        if (highlight) setTypeface(null, Typeface.BOLD)
        layoutParams = TableRow.LayoutParams(dp(170), TableRow.LayoutParams.MATCH_PARENT)
    }

    private fun headerCell(product: Product): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(8))
            layoutParams = TableRow.LayoutParams(dp(170), TableRow.LayoutParams.WRAP_CONTENT)
        }
        col.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            setBackgroundResource(android.R.color.transparent)
            contentDescription = "Bỏ khỏi so sánh"
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { gravity = Gravity.END }
            setOnClickListener {
                compareManager.remove(userId(), product.id)
                render()
            }
        })
        col.addView(ImageView(this).apply {
            ProductImageUtils.load(this, product.imageName)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(100))
            setOnClickListener { openDetail(product) }
        })
        col.addView(TextView(this).apply {
            text = product.name
            textSize = 13f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setOnClickListener { openDetail(product) }
        })
        return col
    }

    private fun actionCell(product: Product): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = TableRow.LayoutParams(dp(170), TableRow.LayoutParams.WRAP_CONTENT)
        }
        col.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Xem chi tiết"
            isAllCaps = false
            setOnClickListener { openDetail(product) }
        })
        col.addView(MaterialButton(this).apply {
            text = "+ Thêm vào giỏ"
            isAllCaps = false
            isEnabled = product.quantity > 0 && !product.isDiscontinued && !sessionManager.isAdmin()
            setOnClickListener {
                val (_, msg) = cartDao.addToCart(userId(), product.id, 1)
                Toast.makeText(this@CompareProductsActivity, msg, Toast.LENGTH_SHORT).show()
            }
        })
        return col
    }

    private fun openDetail(product: Product) {
        startActivity(Intent(this, ProductDetailActivity::class.java).putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, product.id))
    }
}
