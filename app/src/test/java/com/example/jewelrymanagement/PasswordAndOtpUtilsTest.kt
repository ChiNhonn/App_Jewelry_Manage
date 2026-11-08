package com.example.jewelrymanagement

import com.example.jewelrymanagement.utils.OtpUtils
import com.example.jewelrymanagement.utils.PasswordUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class PasswordAndOtpUtilsTest {

    @Test
    fun pbkdf2_roundTripAndRandomSalt() {
        val a = PasswordUtils.hashPassword("matkhau123")
        val b = PasswordUtils.hashPassword("matkhau123")
        assertTrue(a.startsWith("pbkdf2_sha256$"))
        assertNotEquals(a, b)
        assertTrue(PasswordUtils.verifyPassword("matkhau123", a))
        assertFalse(PasswordUtils.verifyPassword("matkhau124", a))
        assertFalse(PasswordUtils.needsRehash(a))
    }

    @Test
    fun legacySha256_stillVerifiesAndNeedsRehash() {
        val legacy = MessageDigest.getInstance("SHA-256").digest("admin123".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertTrue(PasswordUtils.verifyPassword("admin123", legacy))
        assertFalse(PasswordUtils.verifyPassword("admin124", legacy))
        assertTrue(PasswordUtils.needsRehash(legacy))
    }

    @Test
    fun malformedHash_isRejected() {
        assertFalse(PasswordUtils.verifyPassword("x", "pbkdf2_sha256\$abc\$\$"))
    }

    @Test
    fun otp_isSixDigits() {
        repeat(200) {
            val otp = OtpUtils.generateOtp()
            assertTrue(otp, OtpUtils.isValidFormat(otp))
        }
        assertFalse(OtpUtils.isValidFormat("12345"))
        assertFalse(OtpUtils.isValidFormat("1234567"))
        assertFalse(OtpUtils.isValidFormat("12a456"))
    }

    @Test
    fun otpHash_isSaltedAndMatches() {
        val salt = OtpUtils.newSalt()
        val hash = OtpUtils.hash("123456", salt)
        assertTrue(OtpUtils.matches("123456", salt, hash))
        assertFalse(OtpUtils.matches("123457", salt, hash))
        assertNotEquals(hash, OtpUtils.hash("123456", OtpUtils.newSalt()))
    }

    @Test
    fun formatRemaining() {
        assertEquals("05:00", OtpUtils.formatRemaining(5 * 60_000L))
        assertEquals("00:01", OtpUtils.formatRemaining(1))
        assertEquals("00:00", OtpUtils.formatRemaining(-5))
        assertEquals("01:30", OtpUtils.formatRemaining(90_000))
    }
}
