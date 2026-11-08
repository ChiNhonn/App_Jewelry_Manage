package com.example.jewelrymanagement.models

import com.example.jewelrymanagement.utils.DateUtils
import java.io.Serializable
import java.util.Date

data class Promotion(
    val id: Long = 0,
    val code: String,
    val name: String,
    val description: String = "",
    val discountType: String, // "PERCENTAGE" or "FIXED_AMOUNT"
    val discountValue: Double,
    val startDate: String, // "yyyy-MM-dd HH:mm:ss"
    val endDate: String,   // "yyyy-MM-dd HH:mm:ss"
    val targetType: String = TARGET_ALL,
    val targetCategoryIds: List<Long> = emptyList(),
    val targetProductIds: List<Long> = emptyList(),
    /** Human-readable names of the targets, filled by the DAO for display. */
    val targetNames: List<String> = emptyList(),
    val isActive: Boolean = true,
    val createdAt: String = ""
) : Serializable {

    companion object {
        const val TARGET_ALL = "ALL"
        const val TARGET_CATEGORY = "CATEGORY"
        const val TARGET_PRODUCT = "PRODUCT"

        const val STATUS_PAUSED = "Tạm ngừng"
        const val STATUS_UPCOMING = "Sắp diễn ra"
        const val STATUS_RUNNING = "Đang diễn ra"
        const val STATUS_ENDED = "Đã kết thúc"
    }

    /** Status is always derived from the current time, never stored. */
    fun getStatus(now: Date = Date()): String {
        val start = DateUtils.parse(startDate)
        val end = DateUtils.parse(endDate)
        return when {
            end != null && now.after(end) -> STATUS_ENDED
            !isActive -> STATUS_PAUSED
            start != null && now.before(start) -> STATUS_UPCOMING
            start == null || end == null -> STATUS_PAUSED
            else -> STATUS_RUNNING
        }
    }

    fun isCurrentlyActive(now: Date = Date()): Boolean = getStatus(now) == STATUS_RUNNING

    fun appliesTo(productId: Long, categoryId: Long): Boolean = when (targetType) {
        TARGET_ALL -> true
        TARGET_CATEGORY -> categoryId in targetCategoryIds
        TARGET_PRODUCT -> productId in targetProductIds
        else -> false
    }

    /** Discount per unit for the given original price, rounded to whole đồng. */
    fun calculateDiscountAmount(originalPrice: Double): Double {
        if (originalPrice <= 0) return 0.0
        val raw = when (discountType) {
            "PERCENTAGE" -> originalPrice * (discountValue.coerceIn(0.0, 100.0) / 100.0)
            "FIXED_AMOUNT" -> discountValue
            else -> 0.0
        }
        return Math.round(raw.coerceIn(0.0, originalPrice)).toDouble()
    }

    fun discountLabel(): String =
        if (discountType == "PERCENTAGE") "-${formatNumber(discountValue)}%"
        else "-${com.example.jewelrymanagement.utils.CurrencyUtils.formatVnd(discountValue)}"

    private fun formatNumber(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
}
