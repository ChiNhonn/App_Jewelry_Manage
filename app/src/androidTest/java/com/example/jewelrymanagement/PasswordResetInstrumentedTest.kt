package com.example.jewelrymanagement

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.jewelrymanagement.database.CartDao
import com.example.jewelrymanagement.database.DatabaseHelper
import com.example.jewelrymanagement.database.ProductDao
import com.example.jewelrymanagement.database.UserDao
import com.example.jewelrymanagement.repositories.OtpRequestResult
import com.example.jewelrymanagement.repositories.OtpVerifyResult
import com.example.jewelrymanagement.repositories.PasswordResetRepository
import com.example.jewelrymanagement.utils.OtpEmailResult
import com.example.jewelrymanagement.utils.OtpUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Full forgot-password flow against the real SQLite DB, with a fake mail sender and clock (no Gmail). */
@RunWith(AndroidJUnit4::class)
class PasswordResetInstrumentedTest {

    private lateinit var ctx: Context
    private lateinit var db: DatabaseHelper
    private var now = 1_000_000_000_000L
    private val sentCodes = mutableMapOf<String, String>()
    private lateinit var repo: PasswordResetRepository

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = DatabaseHelper.getInstance(ctx)
        db.resetToDefaultData()
        repo = PasswordResetRepository(ctx, { to, otp, _ ->
            sentCodes[to.lowercase()] = otp
            OtpEmailResult.Sent
        }, { now })
    }

    @After
    fun tearDown() {
        db.resetToDefaultData()
    }

    private fun wrongCode(email: String): String {
        val right = sentCodes.getValue(email)
        return if (right == "000000") "111111" else "000000"
    }

    private fun resetFlow(username: String, oldPassword: String, email: String) {
        val users = UserDao(ctx)
        val user = users.login(username, oldPassword)!!

        assertTrue(repo.requestOtp(email.uppercase()) is OtpRequestResult.Sent)
        assertTrue(repo.verifyOtp(email, wrongCode(email)) is OtpVerifyResult.WrongCode)
        val verified = repo.verifyOtp(email, sentCodes.getValue(email)) as OtpVerifyResult.Verified

        // Same code cannot be verified twice.
        assertTrue(repo.verifyOtp(email, sentCodes.getValue(email)) is OtpVerifyResult.Error)

        assertNotNull(repo.resetPassword(email, verified.resetToken, "abc", "abc"))
        assertNotNull(repo.resetPassword(email, verified.resetToken, "matkhau1", "matkhau2"))
        assertNotNull(repo.resetPassword(email, "fake-token", "matkhau1", "matkhau1"))
        assertNull(repo.resetPassword(email, verified.resetToken, "matkhau1", "matkhau1"))
        // Token is single use.
        assertNotNull(repo.resetPassword(email, verified.resetToken, "matkhau9", "matkhau9"))

        assertNull(users.login(username, oldPassword))
        val after = users.login(username, "matkhau1")!!
        assertEquals(user.id, after.id)
        assertEquals(user.fullName, after.fullName)
        assertEquals(user.role, after.role)
    }

    @Test
    fun admin_canResetPassword() = resetFlow("admin", "admin123", "duongtrinhan00@gmail.com")

    @Test
    fun customer_canResetPassword_andKeepsCart() {
        val customer = UserDao(ctx).login("customer1", "customer123")!!
        val product = ProductDao(ctx).getAll().first { it.quantity > 0 && !it.isDiscontinued }
        assertTrue(CartDao(ctx).addToCart(customer.id, product.id, 1).first)

        resetFlow("customer1", "customer123", "customer1@gmail.com")
        assertEquals(1, CartDao(ctx).getCartCount(customer.id))
    }

    @Test
    fun unknownEmail_isRejectedWithoutSending() {
        val r = repo.requestOtp("khongtontai@gmail.com")
        assertTrue(r is OtpRequestResult.Error)
        assertTrue(sentCodes.isEmpty())
    }

    @Test
    fun expiredCode_isRejected() {
        val email = "customer1@gmail.com"
        repo.requestOtp(email)
        now += OtpUtils.OTP_VALIDITY_MS + 1
        val r = repo.verifyOtp(email, sentCodes.getValue(email))
        assertTrue(r is OtpVerifyResult.Error && r.mustRequestNew)
    }

    @Test
    fun tooManyWrongAttempts_invalidatesCode() {
        val email = "customer1@gmail.com"
        repo.requestOtp(email)
        repeat(OtpUtils.MAX_FAILED_ATTEMPTS - 1) {
            assertTrue(repo.verifyOtp(email, wrongCode(email)) is OtpVerifyResult.WrongCode)
        }
        assertTrue(repo.verifyOtp(email, wrongCode(email)) is OtpVerifyResult.Error)
        val r = repo.verifyOtp(email, sentCodes.getValue(email))
        assertTrue(r is OtpVerifyResult.Error && r.mustRequestNew)
    }

    @Test
    fun resend_respectsCooldown_andReplacesOldCode() {
        val email = "customer1@gmail.com"
        repo.requestOtp(email)
        val first = sentCodes.getValue(email)
        assertTrue(repo.requestOtp(email) is OtpRequestResult.Error)

        now += OtpUtils.RESEND_COOLDOWN_MS
        assertTrue(repo.requestOtp(email) is OtpRequestResult.Sent)
        val second = sentCodes.getValue(email)
        if (first != second) {
            assertTrue(repo.verifyOtp(email, first) is OtpVerifyResult.WrongCode)
        }
        assertTrue(repo.verifyOtp(email, second) is OtpVerifyResult.Verified)
    }

    @Test
    fun resetToken_expires() {
        val email = "customer1@gmail.com"
        repo.requestOtp(email)
        val token = (repo.verifyOtp(email, sentCodes.getValue(email)) as OtpVerifyResult.Verified).resetToken
        now += OtpUtils.RESET_TOKEN_VALIDITY_MS + 1
        assertNotNull(repo.resetPassword(email, token, "matkhau1", "matkhau1"))
        assertNotNull(UserDao(ctx).login("customer1", "customer123"))
    }
}
