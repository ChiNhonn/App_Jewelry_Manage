package com.example.jewelrymanagement.models

import com.example.jewelrymanagement.utils.CurrencyUtils
import com.example.jewelrymanagement.utils.DateUtils
import java.io.Serializable
import java.util.Date

data class Coupon(
    val id: Long = 0,
    val code: String,
    val title: String,
    val discountType: String, // "PERCENTAGE" or "FIXED_AMOUNT"
    val discountValue: Double,
    val minOrder: Double = 0.0,
    /** Cap for percentage coupons; null = no cap. */
    val maxDiscount: Double? = null,
    val startDate: String,
    val endDate: String,
    val usageLimit: Int = 100,
    val usedCount: Int = 0,
    val isActive: Boolean = true,
    val createdAt: String = ""
) : Serializable {

    val minOrderAmount: Double get() = minOrder
    val maxDiscountAmount: Double? get() = maxDiscount
    val remainingUses: Int get() = (usageLimit - usedCount).coerceAtLeast(0)

    fun isExpired(now: Date = Date()): Boolean {
        val end = DateUtils.parse(endDate)
        return end != null && now.after(end)
    }

    fun isNotStarted(now: Date = Date()): Boolean {
        val start = DateUtils.parse(startDate)
        return start != null && now.before(start)
    }

    /** Status derived from flags, dates and usage at the current time. */
    fun getStatus(now: Date = Date()): String = when {
        isExpired(now) -> "Hết hạn"
        !isActive -> "Tạm ngừng"
        isNotStarted(now) -> "Chưa bắt đầu"
        usedCount >= usageLimit -> "Hết lượt"
        else -> "Đang hoạt động"
    }

    /**
     * Returns null when the coupon can be applied to [eligibleAmount] (the part of the order
     * the coupon may discount), otherwise a user-facing reason in Vietnamese.
     */
    fun validationError(eligibleAmount: Double, now: Date = Date()): String? = when {
        !isActive -> "Mã giảm giá '$code' hiện đang tạm ngừng."
        isNotStarted(now) -> "Mã giảm giá '$code' chưa đến thời gian áp dụng (bắt đầu từ ${DateUtils.toDisplay(startDate)})."
        isExpired(now) -> "Mã giảm giá '$code' đã hết hạn (hạn cuối ${DateUtils.toDisplay(endDate)})."
        usedCount >= usageLimit -> "Mã giảm giá '$code' đã hết lượt sử dụng."
        eligibleAmount < minOrder ->
            "Giá trị sản phẩm được áp dụng mã chưa đạt tối thiểu ${CurrencyUtils.formatVnd(minOrder)} " +
                "(hiện có ${CurrencyUtils.formatVnd(eligibleAmount)})."
        else -> null
    }

    /** Discount for [amount], rounded to whole đồng and never larger than [amount]. */
    fun calculateDiscount(amount: Double): Double {
        if (amount <= 0) return 0.0
        val raw = when (discountType) {
            "PERCENTAGE" -> {
                val pct = amount * (discountValue.coerceIn(0.0, 100.0) / 100.0)
                if (maxDiscount != null && maxDiscount > 0) pct.coerceAtMost(maxDiscount) else pct
            }
            "FIXED_AMOUNT" -> discountValue
            else -> 0.0
        }
        return Math.round(raw.coerceIn(0.0, amount)).toDouble()
    }

    fun discountLabel(): String =
        if (discountType == "PERCENTAGE") {
            val pct = if (discountValue % 1.0 == 0.0) discountValue.toLong().toString() else discountValue.toString()
            "Giảm $pct%" + (maxDiscount?.let { " (tối đa ${CurrencyUtils.formatVnd(it)})" } ?: "")
        } else {
            "Giảm ${CurrencyUtils.formatVnd(discountValue)}"
        }
}
