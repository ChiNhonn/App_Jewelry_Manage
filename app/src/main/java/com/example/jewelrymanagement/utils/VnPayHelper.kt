package com.example.jewelrymanagement.utils

import com.example.jewelrymanagement.BuildConfig
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * VNPay sandbox (API v2.1.0). The hash secret ships inside the APK, which is acceptable only for
 * sandbox demos: a production integration must build and verify these signatures on a server.
 */
object VnPayHelper {

    private const val PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html"
    const val RETURN_URL = "https://jewelrymanagement.app/vnpay-return"
    private const val EXPIRE_MINUTES = 15

    data class ReturnResult(
        val signatureValid: Boolean,
        val success: Boolean,
        val orderId: Long?,
        val amountVnd: Long?,
        val responseCode: String,
        val transactionNo: String,
        val bankCode: String
    )

    fun isConfigured(): Boolean = BuildConfig.VNPAY_TMN_CODE.isNotBlank() && BuildConfig.VNPAY_HASH_SECRET.isNotBlank()

    fun buildPaymentUrl(orderId: Long, amountVnd: Long): String {
        val format = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh") }
        val now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
        val createDate = format.format(now.time)
        now.add(Calendar.MINUTE, EXPIRE_MINUTES)

        val params = sortedMapOf(
            "vnp_Version" to "2.1.0",
            "vnp_Command" to "pay",
            "vnp_TmnCode" to BuildConfig.VNPAY_TMN_CODE,
            "vnp_Amount" to (amountVnd * 100).toString(),
            "vnp_CurrCode" to "VND",
            // TxnRef must be unique per attempt, so retries of the same order get a new suffix.
            "vnp_TxnRef" to "${orderId}_${System.currentTimeMillis() / 1000}",
            "vnp_OrderInfo" to "Thanh toan don hang DH$orderId",
            "vnp_OrderType" to "other",
            "vnp_Locale" to "vn",
            "vnp_ReturnUrl" to RETURN_URL,
            "vnp_IpAddr" to "127.0.0.1",
            "vnp_CreateDate" to createDate,
            "vnp_ExpireDate" to format.format(now.time)
        )
        val query = encode(params)
        return "$PAY_URL?$query&vnp_SecureHash=${hmacSha512(query)}"
    }

    fun isReturnUrl(url: String): Boolean = url.startsWith(RETURN_URL)

    fun parseReturn(url: String): ReturnResult {
        val params = url.substringAfter('?', "").split('&')
            .filter { it.contains('=') }
            .associate { URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8") }

        val receivedHash = params["vnp_SecureHash"].orEmpty()
        val signed = params.filterKeys { it.startsWith("vnp_") && it != "vnp_SecureHash" && it != "vnp_SecureHashType" }
            .filterValues { it.isNotEmpty() }
            .toSortedMap()
        val expectedHash = hmacSha512(encode(signed))
        val signatureValid = receivedHash.isNotEmpty() &&
            MessageDigest.isEqual(expectedHash.toByteArray(), receivedHash.lowercase(Locale.US).toByteArray())

        val responseCode = params["vnp_ResponseCode"].orEmpty()
        return ReturnResult(
            signatureValid = signatureValid,
            success = signatureValid && responseCode == "00" && params["vnp_TransactionStatus"] == "00",
            orderId = params["vnp_TxnRef"]?.substringBefore('_')?.toLongOrNull(),
            amountVnd = params["vnp_Amount"]?.toLongOrNull()?.div(100),
            responseCode = responseCode,
            transactionNo = params["vnp_TransactionNo"].orEmpty(),
            bankCode = params["vnp_BankCode"].orEmpty()
        )
    }

    fun describeResponseCode(code: String): String = when (code) {
        "00" -> "Giao dịch thành công."
        "07" -> "Giao dịch bị nghi ngờ gian lận."
        "09" -> "Thẻ/Tài khoản chưa đăng ký Internet Banking."
        "10" -> "Xác thực thông tin thẻ/tài khoản sai quá 3 lần."
        "11" -> "Đã hết hạn chờ thanh toán."
        "12" -> "Thẻ/Tài khoản bị khóa."
        "13" -> "Nhập sai mật khẩu xác thực (OTP)."
        "24" -> "Bạn đã hủy giao dịch."
        "51" -> "Tài khoản không đủ số dư để thanh toán."
        "65" -> "Tài khoản đã vượt quá hạn mức giao dịch trong ngày."
        "75" -> "Ngân hàng thanh toán đang bảo trì."
        "79" -> "Nhập sai mật khẩu thanh toán quá số lần quy định."
        else -> "Giao dịch không thành công (mã lỗi $code)."
    }

    /** VNPay signs the URL-encoded (US-ASCII, space as '+') query string sorted by key. */
    private fun encode(params: Map<String, String>): String = params.entries.joinToString("&") { (k, v) ->
        "${URLEncoder.encode(k, "US-ASCII")}=${URLEncoder.encode(v, "US-ASCII")}"
    }

    private fun hmacSha512(data: String): String {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(BuildConfig.VNPAY_HASH_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA512"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
