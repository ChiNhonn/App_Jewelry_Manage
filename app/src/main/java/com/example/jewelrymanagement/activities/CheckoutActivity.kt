package com.example.jewelrymanagement.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.jewelrymanagement.R
import com.example.jewelrymanagement.database.CartDao
import com.example.jewelrymanagement.database.OrderDao
import com.example.jewelrymanagement.databinding.ActivityCheckoutBinding
import com.example.jewelrymanagement.databinding.ItemOrderDetailBinding
import com.example.jewelrymanagement.repositories.CheckoutQuote
import com.example.jewelrymanagement.repositories.PricingRepository
import com.example.jewelrymanagement.utils.CurrencyUtils
import com.example.jewelrymanagement.utils.LocationAddressHelper
import com.example.jewelrymanagement.utils.SessionManager
import com.example.jewelrymanagement.utils.ValidationUtils
import com.example.jewelrymanagement.utils.showEnableLocationDialog
import kotlinx.coroutines.launch

class CheckoutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCheckoutBinding
    private lateinit var orderDao: OrderDao
    private lateinit var cartDao: CartDao
    private lateinit var pricing: PricingRepository
    private lateinit var sessionManager: SessionManager

    private var quote: CheckoutQuote? = null
    private var appliedCouponCode: String = ""
    private var retryLocationOnResume = false

    private var placedOrderId: Long = -1
    private var placedAmount: Double = 0.0
    private var placedPhone: String = ""

    private val vnPayLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        onVnPayResult(
            paid = data?.getBooleanExtra(VnPayActivity.EXTRA_PAID, false) == true,
            message = data?.getStringExtra(VnPayActivity.EXTRA_MESSAGE) ?: "Bạn đã dừng thanh toán."
        )
    }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.any { it }) {
                fillAddressFromLocation()
            } else {
                Toast.makeText(this, "Chưa được cấp quyền vị trí, vui lòng nhập địa chỉ thủ công.", Toast.LENGTH_LONG).show()
            }
        }

    companion object {
        private const val DEFAULT_ADDRESS_HINT = "Nhấn biểu tượng vị trí để tự điền địa chỉ hiện tại"
        private const val STATE_ORDER_ID = "placed_order_id"
        private const val STATE_AMOUNT = "placed_amount"
        private const val STATE_PHONE = "placed_phone"
        private const val DETECTED_ADDRESS_HINT = "Địa chỉ lấy từ vị trí hiện tại, vui lòng kiểm tra số nhà trước khi đặt"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        orderDao = OrderDao(this)
        cartDao = CartDao(this)
        pricing = PricingRepository(this)
        sessionManager = SessionManager(this)

        savedInstanceState?.let {
            placedOrderId = it.getLong(STATE_ORDER_ID, -1)
            placedAmount = it.getDouble(STATE_AMOUNT, 0.0)
            placedPhone = it.getString(STATE_PHONE).orEmpty()
        }
        prefillCustomerInfo()
        // Once the order exists the cart is empty, so the summary is not rebuilt while paying.
        if (placedOrderId <= 0) loadOrderSummary()
        setupListeners()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_ORDER_ID, placedOrderId)
        outState.putDouble(STATE_AMOUNT, placedAmount)
        outState.putString(STATE_PHONE, placedPhone)
    }

    override fun onResume() {
        super.onResume()
        if (retryLocationOnResume && LocationAddressHelper.isLocationEnabled(this)) {
            retryLocationOnResume = false
            fillAddressFromLocation()
        }
    }

    private fun prefillCustomerInfo() {
        val user = sessionManager.getUser() ?: return
        binding.etCheckoutName.setText(user.fullName)
        binding.etCheckoutPhone.setText(user.phone)
        val detected = LocationAddressHelper.getSavedAddress(this)
        if (user.address.isNotBlank()) {
            binding.etCheckoutAddress.setText(user.address)
        } else if (detected.isNotBlank()) {
            binding.etCheckoutAddress.setText(detected)
            binding.tilCheckoutAddress.helperText = DETECTED_ADDRESS_HINT
        }
    }

    private fun onUseCurrentLocation() {
        if (LocationAddressHelper.hasPermission(this)) {
            fillAddressFromLocation()
        } else {
            locationPermissionLauncher.launch(LocationAddressHelper.PERMISSIONS)
        }
    }

    private fun fillAddressFromLocation() {
        binding.tilCheckoutAddress.isEndIconVisible = false
        binding.tilCheckoutAddress.helperText = "Đang lấy địa chỉ hiện tại..."
        lifecycleScope.launch {
            val result = LocationAddressHelper.detectAddress(this@CheckoutActivity)
            binding.tilCheckoutAddress.isEndIconVisible = true
            binding.tilCheckoutAddress.helperText = DEFAULT_ADDRESS_HINT
            when (result) {
                is LocationAddressHelper.Result.Success -> {
                    binding.etCheckoutAddress.setText(result.address)
                    binding.etCheckoutAddress.setSelection(result.address.length)
                    binding.tilCheckoutAddress.error = null
                    binding.tilCheckoutAddress.helperText = DETECTED_ADDRESS_HINT
                }
                LocationAddressHelper.Result.LocationDisabled -> {
                    retryLocationOnResume = true
                    showEnableLocationDialog()
                }
                LocationAddressHelper.Result.NoLocation ->
                    Toast.makeText(this@CheckoutActivity, "Chưa xác định được vị trí. Hãy thử lại ở nơi thoáng hơn.", Toast.LENGTH_LONG).show()
                LocationAddressHelper.Result.GeocodeFailed ->
                    Toast.makeText(this@CheckoutActivity, "Không đổi được vị trí thành địa chỉ. Kiểm tra kết nối mạng rồi thử lại.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Re-reads the cart and re-prices it from SQLite (promotions + applied coupon). */
    private fun loadOrderSummary() {
        val cartItems = cartDao.getCartItems(sessionManager.getShopperId())
        if (cartItems.isEmpty()) {
            Toast.makeText(this, "Giỏ hàng rỗng!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val newQuote = pricing.quote(cartItems.map { it.productId to it.quantity }, appliedCouponCode.ifBlank { null })
        if (appliedCouponCode.isNotBlank() && newQuote.coupon == null) {
            showCouponMessage(newQuote.couponError ?: "Mã giảm giá không còn hợp lệ.", success = false)
            appliedCouponCode = ""
        }
        quote = newQuote

        binding.layoutCheckoutItems.removeAllViews()
        for (line in newQuote.lines) {
            val row = ItemOrderDetailBinding.inflate(LayoutInflater.from(this), binding.layoutCheckoutItems, false)
            row.tvDetailProdName.text = line.price.promotion?.let { "${line.productName}\n🏷 ${it.name} (-${line.price.discountPercent}%)" }
                ?: line.productName
            row.tvDetailQuantityPrice.text = if (line.price.hasDiscount) {
                "${line.quantity} x ${CurrencyUtils.formatVnd(line.price.finalPrice)} (giá gốc ${CurrencyUtils.formatVnd(line.price.originalPrice)})"
            } else {
                "${line.quantity} x ${CurrencyUtils.formatVnd(line.price.originalPrice)}"
            }
            row.tvDetailSubtotal.text = CurrencyUtils.formatVnd(line.subtotal)
            binding.layoutCheckoutItems.addView(row.root)
        }
        updatePriceBreakdown(newQuote)
    }

    private fun updatePriceBreakdown(q: CheckoutQuote) {
        binding.tvSubtotalAmount.text = CurrencyUtils.formatVnd(q.grossAmount)

        binding.layoutPromoDiscountRow.visibility = if (q.promotionDiscount > 0) View.VISIBLE else View.GONE
        binding.tvPromoDiscountAmount.text = "-${CurrencyUtils.formatVnd(q.promotionDiscount)}"

        val coupon = q.coupon
        if (coupon != null && q.couponDiscount > 0) {
            binding.layoutDiscountRow.visibility = View.VISIBLE
            binding.tvDiscountLabel.text = "Mã giảm giá (${coupon.code}):"
            binding.tvDiscountAmount.text = "-${CurrencyUtils.formatVnd(q.couponDiscount)}"
        } else {
            binding.layoutDiscountRow.visibility = View.GONE
        }
        binding.tvCheckoutTotal.text = CurrencyUtils.formatVnd(q.finalAmount)
    }

    private fun setupListeners() {
        binding.btnBackCheckout.setOnClickListener { finish() }
        binding.btnApplyCoupon.setOnClickListener { applyCouponCode() }
        binding.btnConfirmOrder.setOnClickListener { processOrderPlacement() }
        binding.tilCheckoutAddress.setEndIconOnClickListener { onUseCurrentLocation() }
    }

    private fun applyCouponCode() {
        val code = binding.etCouponCode.text?.toString()?.trim()?.uppercase().orEmpty()
        if (code.isBlank()) {
            if (appliedCouponCode.isNotBlank()) {
                appliedCouponCode = ""
                loadOrderSummary()
                showCouponMessage("Đã bỏ mã giảm giá.", success = true)
            } else {
                showCouponMessage("Vui lòng nhập mã giảm giá!", success = false)
            }
            return
        }

        val current = quote ?: return
        val result = pricing.quote(current.lines.map { it.productId to it.quantity }, code)
        if (result.coupon != null) {
            appliedCouponCode = result.coupon.code
            showCouponMessage(
                "Áp dụng mã '${result.coupon.code}' thành công! Bạn được giảm ${CurrencyUtils.formatVnd(result.couponDiscount)} " +
                    "trên ${CurrencyUtils.formatVnd(result.couponEligibleAmount)} tiền hàng không khuyến mãi.",
                success = true
            )
        } else {
            appliedCouponCode = ""
            showCouponMessage(result.couponError ?: "Mã giảm giá không hợp lệ.", success = false)
        }
        loadOrderSummary()
    }

    private fun showCouponMessage(message: String, success: Boolean) {
        binding.tvCouponMessage.visibility = View.VISIBLE
        binding.tvCouponMessage.setTextColor(ContextCompat.getColor(this, if (success) R.color.stock_in else R.color.stock_out))
        binding.tvCouponMessage.text = message
    }

    private fun processOrderPlacement() {
        val receiverName = binding.etCheckoutName.text?.toString()?.trim().orEmpty()
        val phone = binding.etCheckoutPhone.text?.toString()?.trim().orEmpty()
        val address = binding.etCheckoutAddress.text?.toString()?.trim().orEmpty()
        val note = binding.etCheckoutNote.text?.toString()?.trim().orEmpty()

        binding.tilCheckoutName.error = null
        binding.tilCheckoutPhone.error = null
        binding.tilCheckoutAddress.error = null

        var hasError = false
        if (receiverName.isEmpty()) {
            binding.tilCheckoutName.error = "Vui lòng nhập tên người nhận"
            hasError = true
        }
        if (!ValidationUtils.isValidPhone(phone)) {
            binding.tilCheckoutPhone.error = "Số điện thoại không hợp lệ (10 số, bắt đầu bằng 0)"
            hasError = true
        }
        if (address.isEmpty()) {
            binding.tilCheckoutAddress.error = "Vui lòng nhập địa chỉ giao hàng"
            hasError = true
        }
        val currentQuote = quote
        if (hasError || currentQuote == null) return

        val paymentMethod = if (binding.rbCOD.isChecked) OrderDao.PAYMENT_COD else OrderDao.PAYMENT_VNPAY
        val fullAddress = if (note.isNotBlank()) "$address (Ghi chú: $note)" else address

        binding.btnConfirmOrder.isEnabled = false
        val result = orderDao.placeOrder(
            userId = sessionManager.getShopperId(),
            receiverName = receiverName,
            phone = phone,
            shippingAddress = fullAddress,
            paymentMethod = paymentMethod,
            couponCode = appliedCouponCode,
            expectedFinalAmount = currentQuote.finalAmount
        )
        binding.btnConfirmOrder.isEnabled = true

        if (result.success) {
            placedOrderId = result.orderId
            placedAmount = currentQuote.finalAmount
            placedPhone = phone
            if (paymentMethod == OrderDao.PAYMENT_VNPAY) {
                vnPayLauncher.launch(VnPayActivity.intent(this, result.orderId))
            } else {
                showCheckoutResult(
                    title = "Đặt hàng thành công!",
                    detail = "Thanh toán khi nhận hàng (COD)."
                )
            }
        } else {
            if (result.priceChanged) loadOrderSummary()
            AlertDialog.Builder(this)
                .setTitle(if (result.priceChanged) "Ưu đãi đã thay đổi" else "Đặt hàng thất bại")
                .setMessage(result.message)
                .setPositiveButton("Đóng", null)
                .show()
        }
    }

    private fun onVnPayResult(paid: Boolean, message: String) {
        if (paid) {
            showCheckoutResult(title = "Đặt hàng thành công!", detail = message)
            return
        }
        showUnpaidChoices(message)
    }

    private fun showUnpaidChoices(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Chưa thanh toán được")
            .setMessage("$message\n\nĐơn hàng #$placedOrderId chưa được thanh toán. Hàng vẫn đang được giữ.")
            .setCancelable(false)
            .setPositiveButton("Thanh toán lại") { _, _ ->
                vnPayLauncher.launch(VnPayActivity.intent(this, placedOrderId))
            }
            .setNegativeButton("Đổi sang COD") { _, _ -> confirmSwitchToCod(message) }
            .setNeutralButton("Để sau") { dialog, _ ->
                dialog.dismiss()
                leaveOrderUnpaid()
            }
            .show()
    }

    /** Does not change the order until the user presses Xác nhận. Dừng returns to the previous choices. */
    private fun confirmSwitchToCod(unpaidMessage: String) {
        AlertDialog.Builder(this)
            .setTitle("Chuyển sang COD?")
            .setMessage("Đơn hàng #$placedOrderId sẽ đổi sang thanh toán khi nhận hàng. Bấm Xác nhận để đồng ý, hoặc Dừng để giữ nguyên.")
            .setCancelable(false)
            .setPositiveButton("Xác nhận") { _, _ ->
                orderDao.switchToCod(placedOrderId)
                Toast.makeText(this, "Đã chuyển đơn #$placedOrderId sang thanh toán khi nhận hàng.", Toast.LENGTH_LONG).show()
                finish()
            }
            .setNegativeButton("Dừng") { _, _ -> showUnpaidChoices(unpaidMessage) }
            .show()
    }

    /** Closes only the dialog. The unpaid VNPay order is removed and the cart is restored in one transaction. */
    private fun leaveOrderUnpaid() {
        val orderId = placedOrderId
        val reverted = orderId > 0 && orderDao.revertUnpaidVnpayOrder(orderId, sessionManager.getShopperId())
        if (!reverted) {
            Toast.makeText(this, "Chưa hủy được đơn. Đơn vẫn chưa thanh toán.", Toast.LENGTH_LONG).show()
            return
        }
        placedOrderId = -1
        placedAmount = 0.0
        loadOrderSummary()
        Toast.makeText(this, "Chưa đặt hàng. Sản phẩm vẫn nằm trong giỏ.", Toast.LENGTH_LONG).show()
    }

    private fun showCheckoutResult(title: String, detail: String) {
        val orderId = placedOrderId
        val total = CurrencyUtils.formatVnd(placedAmount)
        val message = "Đơn hàng #$orderId trị giá $total.\n$detail"
        if (!sessionManager.isLoggedIn()) {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("$message\nCửa hàng sẽ liên hệ số $placedPhone để xác nhận giao hàng.\n\nTạo tài khoản để theo dõi đơn và nhận thông báo cho những lần mua sau.")
                .setCancelable(false)
                .setPositiveButton("Về cửa hàng") { _, _ -> finish() }
                .setNegativeButton("Tạo tài khoản") { _, _ ->
                    startActivity(Intent(this, RegisterActivity::class.java))
                    finish()
                }
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Xem chi tiết đơn hàng") { _, _ ->
                    startActivity(Intent(this, OrderDetailActivity::class.java).putExtra(OrderDetailActivity.EXTRA_ORDER_ID, orderId))
                    finish()
                }
                .show()
        }
    }
}
