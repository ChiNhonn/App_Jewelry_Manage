package com.example.jewelrymanagement.repositories

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.jewelrymanagement.database.CouponDao
import com.example.jewelrymanagement.database.DatabaseHelper
import com.example.jewelrymanagement.database.PromotionDao
import com.example.jewelrymanagement.models.Coupon
import com.example.jewelrymanagement.models.Product
import com.example.jewelrymanagement.models.Promotion

/** Effective price of one product right now. The products table always keeps the original price. */
data class PriceInfo(
    val productId: Long,
    val originalPrice: Double,
    val discountPerUnit: Double,
    val promotion: Promotion?
) {
    val finalPrice: Double get() = (originalPrice - discountPerUnit).coerceAtLeast(0.0)
    val hasDiscount: Boolean get() = promotion != null && discountPerUnit > 0
    val discountPercent: Int
        get() = if (originalPrice > 0) Math.round(discountPerUnit / originalPrice * 100).toInt() else 0
}

data class QuoteLine(
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val availableStock: Int,
    val price: PriceInfo
) {
    val originalSubtotal: Double get() = price.originalPrice * quantity
    val promotionDiscount: Double get() = price.discountPerUnit * quantity
    val subtotal: Double get() = price.finalPrice * quantity
}

data class CheckoutQuote(
    val lines: List<QuoteLine>,
    val missingProductIds: List<Long>,
    val coupon: Coupon?,
    val couponDiscount: Double,
    /** Subtotal of lines without a running promotion: the only part a coupon may discount. */
    val couponEligibleAmount: Double,
    val couponError: String?
) {
    val grossAmount: Double get() = lines.sumOf { it.originalSubtotal }
    val promotionDiscount: Double get() = lines.sumOf { it.promotionDiscount }
    val totalDiscount: Double get() = promotionDiscount + couponDiscount
    val finalAmount: Double get() = (grossAmount - totalDiscount).coerceAtLeast(0.0)
}

/**
 * Pricing rules (shared by product pages, cart, checkout, order placement):
 * 1. For each product pick the single running promotion with the largest discount (no stacking).
 * 2. A coupon only discounts products that have NO running promotion, so a product is never
 *    discounted twice. Minimum order value is checked against that eligible subtotal.
 * 3. All amounts are rounded to whole đồng.
 */
class PricingRepository(context: Context) {
    private val dbHelper = DatabaseHelper.getInstance(context)
    private val promotionDao = PromotionDao(context)
    private val couponDao = CouponDao(context)

    fun priceFor(product: Product): PriceInfo =
        bestPrice(product.id, product.categoryId, product.price, promotionDao.getRunningPromotions())

    fun pricesFor(products: List<Product>): Map<Long, PriceInfo> {
        val running = promotionDao.getRunningPromotions()
        return products.associate { it.id to bestPrice(it.id, it.categoryId, it.price, running) }
    }

    /**
     * Builds a quote from the current SQLite data. Pass the transaction's [db] when called
     * from order placement so the quote and the order are computed from the same snapshot.
     */
    fun quote(
        items: List<Pair<Long, Int>>,
        couponCode: String?,
        db: SQLiteDatabase = dbHelper.readableDatabase
    ): CheckoutQuote {
        val running = promotionDao.getRunningPromotions(db)
        val lines = mutableListOf<QuoteLine>()
        val missing = mutableListOf<Long>()

        for ((productId, qty) in items) {
            db.rawQuery(
                "SELECT ${DatabaseHelper.COL_PROD_NAME}, ${DatabaseHelper.COL_PROD_PRICE}, ${DatabaseHelper.COL_PROD_CATEGORY_ID}, ${DatabaseHelper.COL_PROD_QUANTITY} " +
                    "FROM ${DatabaseHelper.TABLE_PRODUCTS} WHERE ${DatabaseHelper.COL_PROD_ID} = ?",
                arrayOf(productId.toString())
            ).use { c ->
                if (c.moveToFirst()) {
                    val price = bestPrice(productId, c.getLong(2), c.getDouble(1), running)
                    lines.add(QuoteLine(productId, c.getString(0), qty, c.getInt(3), price))
                } else {
                    missing.add(productId)
                }
            }
        }

        val eligible = lines.filter { !it.price.hasDiscount }.sumOf { it.subtotal }
        val code = couponCode?.trim()?.uppercase().orEmpty()
        if (code.isEmpty()) {
            return CheckoutQuote(lines, missing, null, 0.0, eligible, null)
        }

        val coupon = couponDao.getCouponByCode(code, db)
            ?: return CheckoutQuote(lines, missing, null, 0.0, eligible, "Mã giảm giá '$code' không tồn tại.")

        if (eligible <= 0) {
            return CheckoutQuote(
                lines, missing, null, 0.0, eligible,
                "Mã '$code' không áp dụng cho sản phẩm đang có chương trình khuyến mãi (không cộng dồn ưu đãi)."
            )
        }

        val error = coupon.validationError(eligible)
        if (error != null) {
            return CheckoutQuote(lines, missing, null, 0.0, eligible, error)
        }
        return CheckoutQuote(lines, missing, coupon, coupon.calculateDiscount(eligible), eligible, null)
    }

    companion object {
        /** Picks the single promotion with the largest discount for this product (no stacking). */
        fun bestPrice(productId: Long, categoryId: Long, originalPrice: Double, running: List<Promotion>): PriceInfo {
            var best: Promotion? = null
            var bestDiscount = 0.0
            for (promo in running) {
                if (!promo.appliesTo(productId, categoryId)) continue
                val discount = promo.calculateDiscountAmount(originalPrice)
                if (discount > bestDiscount) {
                    bestDiscount = discount
                    best = promo
                }
            }
            return PriceInfo(productId, originalPrice, bestDiscount, best)
        }
    }
}
