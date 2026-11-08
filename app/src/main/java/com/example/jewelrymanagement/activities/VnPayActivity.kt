package com.example.jewelrymanagement.activities

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.jewelrymanagement.database.OrderDao
import com.example.jewelrymanagement.databinding.ActivityVnpayBinding
import com.example.jewelrymanagement.utils.CurrencyUtils
import com.example.jewelrymanagement.utils.VnPayHelper
import kotlin.math.roundToLong

/** Hosts the VNPay sandbox page and reports the verified result to the caller. */
class VnPayActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_ORDER_ID = "vnpay_order_id"
        const val EXTRA_PAID = "vnpay_paid"
        const val EXTRA_MESSAGE = "vnpay_message"

        fun intent(context: Context, orderId: Long): Intent =
            Intent(context, VnPayActivity::class.java).putExtra(EXTRA_ORDER_ID, orderId)

        private val WEB_SCHEMES = setOf("http", "https", "about", "blob", "data", "javascript")
        private val BLOCKED_SCHEMES = setOf("file", "content", "javascript", "data", "about")
    }

    private lateinit var binding: ActivityVnpayBinding
    private lateinit var orderDao: OrderDao
    private var orderId: Long = -1
    private var expectedAmount: Long = 0
    private var handled = false
    private var lastExternalAt = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVnpayBinding.inflate(layoutInflater)
        setContentView(binding.root)
        orderDao = OrderDao(this)

        orderId = intent.getLongExtra(EXTRA_ORDER_ID, -1)
        val order = orderDao.getOrderById(orderId)
        if (order == null || !VnPayHelper.isConfigured()) {
            finishWith(false, if (order == null) "Không tìm thấy đơn hàng." else "Chưa cấu hình VNPay (VNPAY_TMN_CODE / VNPAY_HASH_SECRET).")
            return
        }
        if (order.paymentStatus == OrderDao.PAYMENT_STATUS_PAID) {
            finishWith(true, "Đơn hàng #$orderId đã được thanh toán trước đó.")
            return
        }
        expectedAmount = order.finalAmount.roundToLong()
        binding.tvVnpayTitle.text = "Thanh toán đơn #$orderId • ${CurrencyUtils.formatVnd(order.finalAmount)}"

        binding.btnBackVnpay.setOnClickListener { confirmCancel() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webVnpay.canGoBack()) binding.webVnpay.goBack() else confirmCancel()
            }
        })

        binding.webVnpay.settings.javaScriptEnabled = true
        binding.webVnpay.settings.domStorageEnabled = true
        binding.webVnpay.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                handleNavigation(request.url.toString())

            @Suppress("OVERRIDE_DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = handleNavigation(url)

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (isBankLink(url)) {
                    view.stopLoading()
                    openBankApp(url)
                    return
                }
                if (interceptReturn(url)) {
                    view.stopLoading()
                    return
                }
                binding.progressVnpay.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                binding.progressVnpay.visibility = View.GONE
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                val url = request.url.toString()
                if (!isBankLink(url)) return
                val stayedOnFallback = openBankApp(url)
                if (stayedOnFallback) return
                view.post {
                    if (view.canGoBack()) view.goBack()
                    else view.loadUrl(VnPayHelper.buildPaymentUrl(orderId, expectedAmount))
                }
            }
        }
        if (savedInstanceState == null) {
            binding.webVnpay.loadUrl(VnPayHelper.buildPaymentUrl(orderId, expectedAmount))
        } else {
            binding.webVnpay.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webVnpay.saveState(outState)
    }

    /** http(s) stays in the page. Bank apps use intent:// and custom schemes, which WebView cannot load. */
    private fun handleNavigation(url: String): Boolean {
        if (interceptReturn(url)) return true
        if (!isBankLink(url)) return false
        openBankApp(url)
        return true
    }

    private fun isWebUrl(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")

    /** intent:// and bank-app schemes. about:blank and http(s) are normal page loads. */
    private fun isBankLink(url: String): Boolean {
        val scheme = url.substringBefore(':', "").lowercase()
        return scheme.isNotEmpty() && scheme !in WEB_SCHEMES
    }

    /** @return true when the WebView itself navigated to a fallback page. */
    private fun openBankApp(url: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastExternalAt < 800) return false
        lastExternalAt = now

        val launch = runCatching { externalIntent(url) }.getOrNull()
        if (launch != null && startExternal(launch)) {
            Toast.makeText(this, "Đã mở ứng dụng ngân hàng. Thanh toán xong hãy quay lại đây.", Toast.LENGTH_LONG).show()
            return false
        }

        val fallback = launch?.getStringExtra("browser_fallback_url")
        if (!fallback.isNullOrBlank() && isWebUrl(fallback)) {
            binding.webVnpay.loadUrl(fallback)
            return true
        }
        showBankAppMissing(launch?.`package`)
        return false
    }

    private fun startExternal(intent: Intent): Boolean {
        try {
            startActivity(intent)
            return true
        } catch (_: ActivityNotFoundException) {
        }
        if (intent.`package` == null && intent.component == null) return false
        val implicit = Intent(intent).apply {
            `package` = null
            component = null
        }
        return try {
            startActivity(implicit)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun externalIntent(url: String): Intent {
        val intent = if (url.startsWith("intent:", ignoreCase = true)) {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        }
        intent.selector = null
        val grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        intent.flags = intent.flags and grantFlags.inv()
        if (intent.`package` == packageName || intent.component?.packageName == packageName) {
            throw IllegalArgumentException("blocked")
        }
        val scheme = intent.data?.scheme?.lowercase()
        if (scheme != null && scheme in BLOCKED_SCHEMES) throw IllegalArgumentException("blocked")
        return intent
    }

    private fun showBankAppMissing(packageName: String?) {
        AlertDialog.Builder(this)
            .setTitle("Không mở được app ngân hàng")
            .setMessage(
                "Ngân hàng này thanh toán bằng ứng dụng riêng, nhưng máy chưa cài app đó.\n\n" +
                    "Bạn quay lại và chọn \"Thẻ ATM và tài khoản ngân hàng\" để nhập thẻ ngay trên trang. " +
                    "Thẻ thử nghiệm NCB: 9704198526191432198, tên NGUYEN VAN A, ngày phát hành 07/15, OTP 123456."
            )
            .setPositiveButton("Đã hiểu", null)
            .apply {
                if (!packageName.isNullOrBlank()) {
                    setNegativeButton("Cài ứng dụng") { _, _ -> openStore(packageName) }
                }
            }
            .show()
    }

    private fun openStore(packageName: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        try {
            startActivity(market)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        }
    }

    private fun interceptReturn(url: String): Boolean {
        if (!VnPayHelper.isReturnUrl(url)) return false
        if (handled) return true
        handled = true

        val result = VnPayHelper.parseReturn(url)
        when {
            !result.signatureValid ->
                finishWith(false, "Không xác thực được kết quả từ VNPay (sai chữ ký). Đơn hàng chưa được thanh toán.")
            result.orderId != orderId || result.amountVnd != expectedAmount ->
                finishWith(false, "Kết quả thanh toán không khớp với đơn hàng #$orderId.")
            result.success -> {
                orderDao.markOrderPaid(orderId)
                val bank = if (result.bankCode.isNotBlank()) " qua ${result.bankCode}" else ""
                finishWith(true, "Đã thanh toán ${CurrencyUtils.formatVnd(expectedAmount.toDouble())}$bank. Mã giao dịch VNPay: ${result.transactionNo}.")
            }
            else -> finishWith(false, VnPayHelper.describeResponseCode(result.responseCode))
        }
        return true
    }

    private fun confirmCancel() {
        AlertDialog.Builder(this)
            .setTitle("Dừng thanh toán?")
            .setMessage("Đơn hàng #$orderId vẫn được giữ ở trạng thái chưa thanh toán.")
            .setPositiveButton("Dừng") { _, _ -> finishWith(false, "Bạn đã dừng thanh toán.") }
            .setNegativeButton("Tiếp tục thanh toán", null)
            .show()
    }

    private fun finishWith(paid: Boolean, message: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_PAID, paid).putExtra(EXTRA_MESSAGE, message))
        finish()
    }

    override fun onDestroy() {
        if (::binding.isInitialized) binding.webVnpay.destroy()
        super.onDestroy()
    }
}
