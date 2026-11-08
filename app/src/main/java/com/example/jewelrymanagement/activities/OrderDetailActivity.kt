package com.example.jewelrymanagement.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.jewelrymanagement.R
import com.example.jewelrymanagement.database.DatabaseHelper
import com.example.jewelrymanagement.database.OrderDao
import com.example.jewelrymanagement.database.ReviewDao
import com.example.jewelrymanagement.databinding.ActivityOrderDetailBinding
import com.example.jewelrymanagement.databinding.ItemOrderDetailBinding
import com.example.jewelrymanagement.models.Order
import com.example.jewelrymanagement.utils.CurrencyUtils
import com.example.jewelrymanagement.utils.DateUtils
import com.example.jewelrymanagement.utils.SessionManager
import com.google.android.material.button.MaterialButton

class OrderDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ORDER_ID = "ORDER_ID"
    }

    private lateinit var binding: ActivityOrderDetailBinding
    private lateinit var orderDao: OrderDao
    private lateinit var reviewDao: ReviewDao
    private lateinit var sessionManager: SessionManager

    private var orderId: Long = -1

    private val vnPayLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@registerForActivityResult
        val paid = data.getBooleanExtra(VnPayActivity.EXTRA_PAID, false)
        AlertDialog.Builder(this)
            .setTitle(if (paid) "Thanh toán thành công" else "Chưa thanh toán được")
            .setMessage(data.getStringExtra(VnPayActivity.EXTRA_MESSAGE).orEmpty())
            .setPositiveButton("Đóng", null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        orderDao = OrderDao(this)
        reviewDao = ReviewDao(this)
        sessionManager = SessionManager(this)

        orderId = intent.getLongExtra(EXTRA_ORDER_ID, -1)
        if (orderId <= 0 || !sessionManager.isLoggedIn()) {
            Toast.makeText(this, "Không tìm thấy mã đơn hàng!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding.btnBackOrderDetail.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        if (orderId > 0) render()
    }

    private fun render() {
        val order = orderDao.getOrderById(orderId)
        if (order == null) {
            Toast.makeText(this, "Đơn hàng không tồn tại!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (!sessionManager.isAdmin() && order.userId != sessionManager.getUserId()) {
            Toast.makeText(this, "Bạn không có quyền xem đơn hàng này!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        bindOrderInfo(order)
        bindItems(order)
        if (sessionManager.isAdmin()) setupAdminControls(order) else setupCustomerControls(order)
    }

    private fun bindOrderInfo(order: Order) {
        binding.tvOrderDetailTitle.text = "Chi Tiết Đơn Hàng #${order.id}"
        binding.tvDetailCreatedAt.text = "Thời gian đặt: ${DateUtils.toDisplay(order.createdAt)}"
        binding.tvDetailReceiver.text = order.receiverName
        binding.tvDetailPhone.text = "Số điện thoại: ${order.phone}"
        binding.tvDetailAddress.text = "Địa chỉ: ${order.shippingAddress}"
        binding.tvDetailPayment.text = "Phương thức: ${order.paymentMethod} • Trạng thái: ${order.paymentStatus}"

        val breakdown = buildList {
            add("Tổng tiền hàng (giá gốc): ${CurrencyUtils.formatVnd(order.totalAmount)}")
            if (order.promotionDiscount > 0) add("Giảm giá sự kiện: -${CurrencyUtils.formatVnd(order.promotionDiscount)}")
            if (order.couponDiscount > 0) add("Mã giảm giá (${order.couponCode}): -${CurrencyUtils.formatVnd(order.couponDiscount)}")
            if (order.discountAmount > 0 && order.promotionDiscount <= 0 && order.couponDiscount <= 0) {
                add("Giảm giá: -${CurrencyUtils.formatVnd(order.discountAmount)}")
            }
        }
        binding.tvDetailPriceBreakdown.text = breakdown.joinToString("\n")
        binding.tvDetailTotalAmount.text = CurrencyUtils.formatVnd(order.finalAmount)

        binding.tvDetailOrderStatusBadge.text = order.orderStatus
        val (bg, color) = when (order.orderStatus) {
            DatabaseHelper.STATUS_CONFIRMED -> R.drawable.bg_status_confirmed to R.color.status_confirmed_text
            DatabaseHelper.STATUS_SHIPPING -> R.drawable.bg_status_shipping to R.color.status_shipping_text
            DatabaseHelper.STATUS_DELIVERED -> R.drawable.bg_status_delivered to R.color.status_delivered_text
            DatabaseHelper.STATUS_CANCELLED -> R.drawable.bg_status_cancelled to R.color.status_cancelled_text
            else -> R.drawable.bg_status_pending to R.color.status_pending_text
        }
        binding.tvDetailOrderStatusBadge.setBackgroundResource(bg)
        binding.tvDetailOrderStatusBadge.setTextColor(ContextCompat.getColor(this, color))
    }

    private fun bindItems(order: Order) {
        binding.layoutDetailItemsContainer.removeAllViews()
        for (item in orderDao.getOrderDetails(order.id)) {
            val row = ItemOrderDetailBinding.inflate(LayoutInflater.from(this), binding.layoutDetailItemsContainer, false)
            row.tvDetailProdName.text = if (item.promotionName.isNotBlank()) "${item.productName}\n🏷 ${item.promotionName}" else item.productName
            row.tvDetailQuantityPrice.text = if (item.originalUnitPrice > item.unitPrice) {
                "${item.quantity} x ${CurrencyUtils.formatVnd(item.unitPrice)} (giá gốc ${CurrencyUtils.formatVnd(item.originalUnitPrice)})"
            } else {
                "${item.quantity} x ${CurrencyUtils.formatVnd(item.unitPrice)}"
            }
            row.tvDetailSubtotal.text = CurrencyUtils.formatVnd(item.subtotal)
            binding.layoutDetailItemsContainer.addView(row.root)
        }
    }

    private fun setupAdminControls(order: Order) {
        binding.btnCustomerCancelOrder.visibility = View.GONE
        binding.btnCustomerPayVnpay.visibility = View.GONE
        binding.cardReviewSection.visibility = View.GONE

        val nextStatuses = OrderDao.allowedNextStatuses(order.orderStatus).toList()
        if (nextStatuses.isEmpty()) {
            binding.cardAdminStatusControl.visibility = View.GONE
            return
        }
        binding.cardAdminStatusControl.visibility = View.VISIBLE
        val ordered = DatabaseHelper.ORDER_STATUSES.filter { it in nextStatuses }
        val adapter = ArrayAdapter(this, R.layout.item_spinner, ordered)
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        binding.spAdminChangeStatus.adapter = adapter

        binding.btnAdminUpdateStatus.setOnClickListener {
            val newStatus = ordered.getOrNull(binding.spAdminChangeStatus.selectedItemPosition) ?: return@setOnClickListener
            val extra = when (newStatus) {
                DatabaseHelper.STATUS_DELIVERED -> "\n\nKhách hàng sẽ nhận được thông báo mời đánh giá sản phẩm."
                DatabaseHelper.STATUS_CANCELLED -> "\n\nTồn kho và lượt dùng mã giảm giá sẽ được hoàn lại."
                else -> ""
            }
            AlertDialog.Builder(this)
                .setTitle("Cập nhật trạng thái")
                .setMessage("Chuyển đơn hàng #${order.id} từ '${order.orderStatus}' sang '$newStatus'?$extra")
                .setPositiveButton("Xác nhận") { _, _ ->
                    val result = orderDao.updateOrderStatus(order.id, newStatus)
                    Toast.makeText(this, result.second, Toast.LENGTH_LONG).show()
                    render()
                }
                .setNegativeButton("Hủy", null)
                .show()
        }
    }

    private fun setupCustomerControls(order: Order) {
        binding.cardAdminStatusControl.visibility = View.GONE

        val canPayOnline = order.paymentMethod == OrderDao.PAYMENT_VNPAY &&
            order.paymentStatus != OrderDao.PAYMENT_STATUS_PAID &&
            order.orderStatus != DatabaseHelper.STATUS_CANCELLED &&
            order.orderStatus != DatabaseHelper.STATUS_DELIVERED
        binding.btnCustomerPayVnpay.visibility = if (canPayOnline) View.VISIBLE else View.GONE
        binding.btnCustomerPayVnpay.setOnClickListener { vnPayLauncher.launch(VnPayActivity.intent(this, order.id)) }

        if (order.orderStatus == DatabaseHelper.STATUS_PENDING) {
            binding.btnCustomerCancelOrder.visibility = View.VISIBLE
            binding.btnCustomerCancelOrder.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Hủy đơn hàng")
                    .setMessage("Bạn có chắc chắn muốn hủy đơn hàng #${order.id}? Tồn kho sản phẩm sẽ được tự động hoàn lại.")
                    .setPositiveButton("Hủy đơn") { _, _ ->
                        val result = orderDao.cancelOrder(order.id, isCustomer = true, requesterUserId = sessionManager.getUserId())
                        Toast.makeText(this, result.second, Toast.LENGTH_LONG).show()
                        render()
                    }
                    .setNegativeButton("Không", null)
                    .show()
            }
        } else {
            binding.btnCustomerCancelOrder.visibility = View.GONE
        }

        bindReviewSection(order)
    }

    private fun bindReviewSection(order: Order) {
        binding.layoutReviewItems.removeAllViews()
        if (order.orderStatus != DatabaseHelper.STATUS_DELIVERED) {
            binding.cardReviewSection.visibility = View.GONE
            return
        }
        val items = reviewDao.getReviewableItems(order.id, sessionManager.getUserId())
        if (items.isEmpty()) {
            binding.cardReviewSection.visibility = View.GONE
            return
        }
        binding.cardReviewSection.visibility = View.VISIBLE
        for (item in items) {
            val button = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                isAllCaps = false
                text = if (item.alreadyReviewed) "✓ Đã đánh giá: ${item.productName}" else "★ Đánh giá: ${item.productName}"
                isEnabled = !item.alreadyReviewed
                setOnClickListener {
                    startActivity(ReviewProductActivity.intent(this@OrderDetailActivity, order.id, item.productId, item.productName, item.imageName))
                }
            }
            binding.layoutReviewItems.addView(button)
        }
    }
}
