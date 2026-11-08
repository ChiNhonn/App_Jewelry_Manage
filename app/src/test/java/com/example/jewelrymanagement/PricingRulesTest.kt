package com.example.jewelrymanagement

import com.example.jewelrymanagement.models.Coupon
import com.example.jewelrymanagement.models.Promotion
import com.example.jewelrymanagement.repositories.PricingRepository
import com.example.jewelrymanagement.utils.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class PricingRulesTest {

    private val now = DateUtils.parse("2026-10-30 12:00:00")!!

    private fun promo(
        id: Long, type: String, value: Double,
        target: String = Promotion.TARGET_ALL,
        products: List<Long> = emptyList(),
        categories: List<Long> = emptyList(),
        start: String = "2026-10-01 00:00:00",
        end: String = "2026-11-30 23:59:59",
        active: Boolean = true
    ) = Promotion(
        id = id, code = "P$id", name = "Promo $id", discountType = type, discountValue = value,
        startDate = start, endDate = end, targetType = target,
        targetProductIds = products, targetCategoryIds = categories, isActive = active
    )

    private fun coupon(
        type: String = "PERCENTAGE", value: Double = 10.0, minOrder: Double = 0.0, max: Double? = null,
        used: Int = 0, limit: Int = 10, active: Boolean = true,
        start: String = "2026-10-01 00:00:00", end: String = "2026-11-30 23:59:59"
    ) = Coupon(
        code = "SALE10", title = "Test", discountType = type, discountValue = value, minOrder = minOrder,
        maxDiscount = max, startDate = start, endDate = end, usageLimit = limit, usedCount = used, isActive = active
    )

    @Test
    fun promotionStatus_followsTimeAndActiveFlag() {
        assertEquals(Promotion.STATUS_RUNNING, promo(1, "PERCENTAGE", 10.0).getStatus(now))
        assertEquals(Promotion.STATUS_UPCOMING, promo(1, "PERCENTAGE", 10.0, start = "2026-11-01 00:00:00").getStatus(now))
        assertEquals(Promotion.STATUS_ENDED, promo(1, "PERCENTAGE", 10.0, end = "2026-10-29 00:00:00").getStatus(now))
        assertEquals(Promotion.STATUS_PAUSED, promo(1, "PERCENTAGE", 10.0, active = false).getStatus(now))
    }

    @Test
    fun promotionDiscount_isRoundedAndNeverExceedsPrice() {
        assertEquals(333_333.0, promo(1, "PERCENTAGE", 33.3333).calculateDiscountAmount(1_000_000.0), 0.0)
        assertEquals(500_000.0, promo(1, "FIXED_AMOUNT", 2_000_000.0).calculateDiscountAmount(500_000.0), 0.0)
        assertEquals(1_000_000.0, promo(1, "PERCENTAGE", 150.0).calculateDiscountAmount(1_000_000.0), 0.0)
    }

    @Test
    fun bestPrice_picksLargestSingleDiscount_noStacking() {
        val running = listOf(
            promo(1, "PERCENTAGE", 10.0),                                              // 200.000
            promo(2, "FIXED_AMOUNT", 300_000.0, Promotion.TARGET_CATEGORY, categories = listOf(5)), // 300.000
            promo(3, "PERCENTAGE", 50.0, Promotion.TARGET_PRODUCT, products = listOf(99))           // not this product
        )
        val price = PricingRepository.bestPrice(productId = 7, categoryId = 5, originalPrice = 2_000_000.0, running = running)
        assertEquals(2L, price.promotion?.id)
        assertEquals(300_000.0, price.discountPerUnit, 0.0)
        assertEquals(1_700_000.0, price.finalPrice, 0.0)
        assertEquals(15, price.discountPercent)
    }

    @Test
    fun bestPrice_withoutMatchingPromotion_keepsOriginalPrice() {
        val running = listOf(promo(3, "PERCENTAGE", 50.0, Promotion.TARGET_PRODUCT, products = listOf(99)))
        val price = PricingRepository.bestPrice(7, 5, 1_000_000.0, running)
        assertNull(price.promotion)
        assertEquals(1_000_000.0, price.finalPrice, 0.0)
        assertTrue(!price.hasDiscount)
    }

    @Test
    fun coupon_percentageRespectsMaxDiscount() {
        val c = coupon(value = 20.0, max = 100_000.0)
        assertEquals(100_000.0, c.calculateDiscount(1_000_000.0), 0.0)
        assertEquals(40_000.0, c.calculateDiscount(200_000.0), 0.0)
    }

    @Test
    fun coupon_fixedAmountNeverExceedsEligibleAmount() {
        assertEquals(50_000.0, coupon(type = "FIXED_AMOUNT", value = 200_000.0).calculateDiscount(50_000.0), 0.0)
        assertEquals(0.0, coupon().calculateDiscount(0.0), 0.0)
    }

    @Test
    fun coupon_validationReasons() {
        assertNull(coupon().validationError(100_000.0, now))
        assertNotNull(coupon(active = false).validationError(100_000.0, now))
        assertTrue(coupon(end = "2026-10-29 00:00:00").validationError(100_000.0, now)!!.contains("hết hạn"))
        assertTrue(coupon(start = "2026-11-01 00:00:00").validationError(100_000.0, now)!!.contains("chưa đến"))
        assertTrue(coupon(used = 10, limit = 10).validationError(100_000.0, now)!!.contains("hết lượt"))
        assertTrue(coupon(minOrder = 500_000.0).validationError(100_000.0, now)!!.contains("tối thiểu"))
    }

    @Test
    fun dateUtils_roundTripsDbFormat() {
        val d: Date = DateUtils.parse("2026-12-01 08:30:00")!!
        assertEquals("2026-12-01 08:30:00", DateUtils.format(d))
        assertEquals("01/12/2026 08:30", DateUtils.toDisplay("2026-12-01 08:30:00"))
        assertNull(DateUtils.parse("not a date"))
    }
}
