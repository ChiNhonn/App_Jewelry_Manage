package com.example.jewelrymanagement

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.jewelrymanagement.database.CartDao
import com.example.jewelrymanagement.database.CouponDao
import com.example.jewelrymanagement.database.DatabaseHelper
import com.example.jewelrymanagement.database.NotificationDao
import com.example.jewelrymanagement.database.OrderDao
import com.example.jewelrymanagement.database.ProductDao
import com.example.jewelrymanagement.database.PromotionDao
import com.example.jewelrymanagement.database.ReviewDao
import com.example.jewelrymanagement.database.UserDao
import com.example.jewelrymanagement.models.Coupon
import com.example.jewelrymanagement.models.ProductReview
import com.example.jewelrymanagement.models.Promotion
import com.example.jewelrymanagement.repositories.PricingRepository
import com.example.jewelrymanagement.repositories.ReportRepository
import com.example.jewelrymanagement.repositories.ReportType
import com.example.jewelrymanagement.utils.DateUtils
import com.example.jewelrymanagement.utils.PdfReportWriter
import com.example.jewelrymanagement.utils.XlsxExporter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.Calendar

/** Runs against the app's real SQLite database; data is reset to the seed before and after. */
@RunWith(AndroidJUnit4::class)
class OrderFlowInstrumentedTest {

    private lateinit var ctx: Context
    private lateinit var db: DatabaseHelper

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = DatabaseHelper.getInstance(ctx)
        db.resetToDefaultData()
    }

    @After
    fun tearDown() {
        db.resetToDefaultData()
    }

    private fun shift(days: Int): String {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, days)
        return DateUtils.format(c.time)
    }

    @Test
    fun seedAccountsLogIn() {
        assertNotNull(UserDao(ctx).login("admin", "admin123"))
        assertNotNull(UserDao(ctx).login("customer1", "customer123"))
    }

    @Test
    fun fullOrderFlow_promotionCouponDeliveryReviewReport() {
        val users = UserDao(ctx)
        val customer = users.login("customer1", "customer123")!!
        val productDao = ProductDao(ctx)
        val promotionDao = PromotionDao(ctx)
        val couponDao = CouponDao(ctx)
        val cartDao = CartDao(ctx)
        val orderDao = OrderDao(ctx)
        val pricing = PricingRepository(ctx)

        promotionDao.getAllPromotions().forEach { promotionDao.toggleStatus(it.id, false) }
        val products = productDao.getAll().filter { it.quantity >= 3 && !it.isDiscontinued }
        val a = products[0]
        val b = products.first { it.id != a.id }

        assertTrue(promotionDao.insert(
            Promotion(
                code = "TEST_EVT", name = "Sự kiện test", discountType = "PERCENTAGE", discountValue = 10.0,
                startDate = shift(-1), endDate = shift(1),
                targetType = Promotion.TARGET_PRODUCT, targetProductIds = listOf(a.id)
            )
        ) > 0)
        assertTrue(couponDao.insert(
            Coupon(
                code = "TEST100K", title = "Giảm 100K", discountType = "FIXED_AMOUNT", discountValue = 100_000.0,
                startDate = shift(-1), endDate = shift(1), usageLimit = 1
            )
        ) > 0)

        // Promotion applies to A only and products.price is unchanged.
        val priceA = pricing.priceFor(productDao.getById(a.id)!!)
        assertEquals(Math.round(a.price * 0.1).toDouble(), priceA.discountPerUnit, 0.0)
        assertEquals(a.price, productDao.getById(a.id)!!.price, 0.0)
        assertFalse(pricing.priceFor(b).hasDiscount)

        // Coupon only discounts the non-promoted line.
        assertTrue(cartDao.addToCart(customer.id, a.id, 1).first)
        assertTrue(cartDao.addToCart(customer.id, b.id, 1).first)
        val quote = pricing.quote(listOf(a.id to 1, b.id to 1), "TEST100K")
        assertNull(quote.couponError)
        assertEquals(b.price, quote.couponEligibleAmount, 0.0)
        assertEquals(minOf(100_000.0, b.price), quote.couponDiscount, 0.0)

        // Wrong expected total -> nothing written.
        val stale = orderDao.placeOrder(customer.id, "Khách", "0900000000", "HCM", "COD", "TEST100K", quote.finalAmount + 5_000)
        assertFalse(stale.success)
        assertTrue(stale.priceChanged)
        assertEquals(0, couponDao.getCouponByCode("TEST100K")!!.usedCount)

        val placed = orderDao.placeOrder(customer.id, "Khách", "0900000000", "HCM", "COD", "TEST100K", quote.finalAmount)
        assertTrue(placed.message, placed.success)
        val order = orderDao.getOrderById(placed.orderId)!!
        assertEquals(quote.finalAmount, order.finalAmount, 0.0)
        assertEquals("TEST100K", order.couponCode)
        assertEquals(1, couponDao.getCouponByCode("TEST100K")!!.usedCount)
        assertEquals(a.quantity - 1, productDao.getById(a.id)!!.quantity)
        assertEquals(0, cartDao.getCartCount(customer.id))
        val lineA = orderDao.getOrderDetails(order.id).first { it.productId == a.id }
        assertEquals("Sự kiện test", lineA.promotionName)
        assertEquals(a.price, lineA.originalUnitPrice, 0.0)

        // Usage limit reached -> second order with the same coupon is rejected.
        cartDao.addToCart(customer.id, b.id, 1)
        val q2 = pricing.quote(listOf(b.id to 1), "TEST100K")
        assertNotNull(q2.couponError)
        val again = orderDao.placeOrder(customer.id, "Khách", "0900000000", "HCM", "COD", "TEST100K", b.price)
        assertFalse(again.success)

        // Order without coupon, then cancel -> stock restored.
        val o2 = orderDao.placeOrder(customer.id, "Khách", "0900000000", "HCM", "COD", "", b.price)
        assertTrue(o2.message, o2.success)
        val stockAfterOrder = productDao.getById(b.id)!!.quantity
        assertTrue(orderDao.cancelOrder(o2.orderId, isCustomer = true, requesterUserId = customer.id).first)
        assertEquals(stockAfterOrder + 1, productDao.getById(b.id)!!.quantity)

        // Review only after delivery.
        val reviewDao = ReviewDao(ctx)
        assertNotNull(reviewDao.eligibilityError(order.id, a.id, customer.id))

        val notifications = NotificationDao(ctx)
        val before = notifications.getNotificationsForUser(customer.id).count { it.orderId == order.id }
        assertTrue(orderDao.updateOrderStatus(order.id, DatabaseHelper.STATUS_DELIVERED).first)
        assertFalse(orderDao.updateOrderStatus(order.id, DatabaseHelper.STATUS_DELIVERED).first)
        assertEquals(before + 1, notifications.getNotificationsForUser(customer.id).count { it.orderId == order.id })
        assertEquals("Đã thanh toán", orderDao.getOrderById(order.id)!!.paymentStatus)

        assertNull(reviewDao.eligibilityError(order.id, a.id, customer.id))
        val admin = users.login("admin", "admin123")!!
        assertNotNull(reviewDao.eligibilityError(order.id, a.id, admin.id))
        val review = ProductReview(productId = a.id, orderId = order.id, userId = customer.id, userName = customer.fullName, rating = 5, comment = "Đẹp")
        assertTrue(reviewDao.insert(review).first > 0)
        assertTrue(reviewDao.insert(review).first <= 0)
        assertEquals(1, reviewDao.getRatingSummaryForProduct(a.id).totalReviews)

        // Reports include the delivered order and render to PDF / XLSX.
        val today = DateUtils.now().substring(0, 10)
        val revenue = ReportRepository(ctx).build(ReportType.REVENUE, today, today, ReportRepository.STATUS_ALL)
        assertFalse(revenue.isEmpty)
        val all = ReportRepository(ctx).build(ReportType.ALL, today, today, ReportRepository.STATUS_ALL)
        val pdf = ByteArrayOutputStream().also { PdfReportWriter.write(all, it) }.toByteArray()
        assertTrue(String(pdf, 0, 5, Charsets.US_ASCII).startsWith("%PDF"))
        val xlsx = ByteArrayOutputStream().also { XlsxExporter.write(all, it) }.toByteArray()
        assertTrue(xlsx.size > 1000 && xlsx[0] == 'P'.code.toByte() && xlsx[1] == 'K'.code.toByte())
    }
}
