package com.example.jewelrymanagement

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.jewelrymanagement.activities.AddEditCouponActivity
import com.example.jewelrymanagement.activities.AddEditProductActivity
import com.example.jewelrymanagement.activities.AddEditPromotionActivity
import com.example.jewelrymanagement.activities.AdminActivity
import com.example.jewelrymanagement.activities.CartActivity
import com.example.jewelrymanagement.activities.CheckoutActivity
import com.example.jewelrymanagement.activities.CompareProductsActivity
import com.example.jewelrymanagement.activities.CustomerActivity
import com.example.jewelrymanagement.activities.ExportReportActivity
import com.example.jewelrymanagement.activities.FavoriteProductsActivity
import com.example.jewelrymanagement.activities.ForgotPasswordActivity
import com.example.jewelrymanagement.activities.LoginActivity
import com.example.jewelrymanagement.activities.ManageCouponActivity
import com.example.jewelrymanagement.activities.ManageOrderActivity
import com.example.jewelrymanagement.activities.ManagePromotionActivity
import com.example.jewelrymanagement.activities.ManageReviewActivity
import com.example.jewelrymanagement.activities.NotificationActivity
import com.example.jewelrymanagement.activities.OrderDetailActivity
import com.example.jewelrymanagement.activities.OrderHistoryActivity
import com.example.jewelrymanagement.activities.ProductDetailActivity
import com.example.jewelrymanagement.activities.StatisticsActivity
import com.example.jewelrymanagement.activities.VerifyOtpActivity
import com.example.jewelrymanagement.database.CartDao
import com.example.jewelrymanagement.database.DatabaseHelper
import com.example.jewelrymanagement.database.FavoriteDao
import com.example.jewelrymanagement.database.OrderDao
import com.example.jewelrymanagement.database.ProductDao
import com.example.jewelrymanagement.database.UserDao
import com.example.jewelrymanagement.utils.CompareManager
import com.example.jewelrymanagement.utils.SessionManager
import org.junit.After
import com.example.jewelrymanagement.repositories.PricingRepository
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Opens each screen with realistic data and fails if any of them crashes while starting. */
@RunWith(AndroidJUnit4::class)
class ScreenSmokeInstrumentedTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        DatabaseHelper.getInstance(ctx).resetToDefaultData()
    }

    @After
    fun tearDown() {
        SessionManager(ctx).clearSession()
        DatabaseHelper.getInstance(ctx).resetToDefaultData()
    }

    private fun open(intent: Intent) {
        ActivityScenario.launch<Activity>(intent).use { scenario ->
            assertNotEquals(Lifecycle.State.INITIALIZED, scenario.state)
        }
    }

    private fun open(cls: Class<out Activity>, extras: Intent.() -> Unit = {}) =
        open(Intent(ctx, cls).apply(extras))

    @Test
    fun customerScreensOpen() {
        val customer = UserDao(ctx).login("customer1", "customer123")!!
        SessionManager(ctx).saveUser(customer)
        val products = ProductDao(ctx).getAll().filter { it.quantity > 0 }
        CartDao(ctx).addToCart(customer.id, products[0].id, 1)
        FavoriteDao(ctx).addFavorite(customer.id, products[0].id)
        val compare = CompareManager(ctx)
        compare.clear(customer.id)
        products.take(3).forEach { compare.add(customer.id, it.id) }

        open(CustomerActivity::class.java)
        open(ProductDetailActivity::class.java) { putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, products[0].id) }
        open(CartActivity::class.java)
        open(CheckoutActivity::class.java)
        open(FavoriteProductsActivity::class.java)
        open(CompareProductsActivity::class.java)
        open(NotificationActivity::class.java)
        open(OrderHistoryActivity::class.java)

        val expected = PricingRepository(ctx).quote(listOf(products[0].id to 1), null).finalAmount
        val placed = OrderDao(ctx).placeOrder(customer.id, "Khách", "0900000000", "HCM", "COD", "", expected)
        assertTrue(placed.message, placed.success)
        OrderDao(ctx).updateOrderStatus(placed.orderId, DatabaseHelper.STATUS_DELIVERED)
        open(OrderDetailActivity::class.java) { putExtra(OrderDetailActivity.EXTRA_ORDER_ID, placed.orderId) }
        open(NotificationActivity::class.java)
        compare.clear(customer.id)
    }

    @Test
    fun adminScreensOpen() {
        SessionManager(ctx).saveUser(UserDao(ctx).login("admin", "admin123")!!)
        open(AdminActivity::class.java)
        open(ManagePromotionActivity::class.java)
        open(AddEditPromotionActivity::class.java)
        open(ManageCouponActivity::class.java)
        open(AddEditCouponActivity::class.java)
        open(ManageReviewActivity::class.java)
        open(ManageOrderActivity::class.java)
        open(StatisticsActivity::class.java)
        open(ExportReportActivity::class.java)
        open(AddEditProductActivity::class.java)
    }

    @Test
    fun authScreensOpen() {
        SessionManager(ctx).clearSession()
        open(LoginActivity::class.java)
        open(ForgotPasswordActivity::class.java)
        open(VerifyOtpActivity::class.java) { putExtra(VerifyOtpActivity.EXTRA_EMAIL, "someone@example.com") }
    }
}
