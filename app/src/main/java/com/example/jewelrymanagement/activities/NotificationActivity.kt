package com.example.jewelrymanagement.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.jewelrymanagement.adapters.NotificationAdapter
import com.example.jewelrymanagement.database.NotificationDao
import com.example.jewelrymanagement.database.ReviewDao
import com.example.jewelrymanagement.databinding.ActivityNotificationBinding
import com.example.jewelrymanagement.models.NotificationItem
import com.example.jewelrymanagement.utils.NotificationHelper
import com.example.jewelrymanagement.utils.SessionManager

class NotificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationBinding
    private lateinit var notificationDao: NotificationDao
    private lateinit var reviewDao: ReviewDao
    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: NotificationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        notificationDao = NotificationDao(this)
        reviewDao = ReviewDao(this)
        sessionManager = SessionManager(this)

        if (!sessionManager.isLoggedIn()) {
            Toast.makeText(this, "Vui lòng đăng nhập để xem thông báo.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        setupRecyclerView()
        binding.swipeRefresh.setOnRefreshListener { loadNotifications() }

        if (savedInstanceState == null) handleLaunchIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) loadNotifications()
    }

    /** Launched from the Android notification: only the order owner may act on it. */
    private fun handleLaunchIntent(intent: Intent) {
        val orderId = intent.getLongExtra(NotificationHelper.EXTRA_ORDER_ID, -1)
        if (orderId <= 0) return
        val targetUserId = intent.getLongExtra(NotificationHelper.EXTRA_TARGET_USER_ID, -1)
        if (targetUserId != sessionManager.getUserId()) {
            AlertDialog.Builder(this)
                .setTitle("Thông báo dành cho tài khoản khác")
                .setMessage("Thông báo về đơn hàng #$orderId thuộc về tài khoản khách hàng đã đặt đơn. Vui lòng đăng xuất và đăng nhập đúng tài khoản đó để xem và đánh giá.")
                .setPositiveButton("Đã hiểu", null)
                .show()
            return
        }
        val item = notificationDao.getNotificationsForUser(targetUserId)
            .firstOrNull { it.orderId == orderId && it.type == NotificationItem.TYPE_ORDER_DELIVERED }
        if (item != null) openNotification(item)
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter(
            context = this,
            notifications = emptyList(),
            onNotificationClick = { openNotification(it) },
            onMarkReadClick = { item ->
                notificationDao.markAsRead(item.id, sessionManager.getUserId())
                loadNotifications()
                Toast.makeText(this, "Đã đánh dấu đã đọc", Toast.LENGTH_SHORT).show()
            },
            onReviewNowClick = { item ->
                notificationDao.markAsRead(item.id, sessionManager.getUserId())
                loadNotifications()
                item.orderId?.let { reviewOrder(it) }
            }
        )
        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        binding.rvNotifications.adapter = adapter
    }

    private fun openNotification(item: NotificationItem) {
        if (!item.isRead) {
            notificationDao.markAsRead(item.id, sessionManager.getUserId())
            loadNotifications()
        }
        val orderId = item.orderId ?: return
        if (item.actionType == NotificationItem.ACTION_REVIEW_ORDER) {
            AlertDialog.Builder(this)
                .setTitle(item.title)
                .setMessage(item.content)
                .setPositiveButton("Đánh giá ngay") { _, _ -> reviewOrder(orderId) }
                .setNegativeButton("Để sau", null)
                .setNeutralButton("Xem đơn hàng") { _, _ -> openOrder(orderId) }
                .show()
        } else {
            openOrder(orderId)
        }
    }

    /** Opens the review form directly, or lets the customer pick a product when the order has several. */
    private fun reviewOrder(orderId: Long) {
        val pending = reviewDao.getReviewableItems(orderId, sessionManager.getUserId()).filter { !it.alreadyReviewed }
        when {
            pending.isEmpty() -> {
                Toast.makeText(this, "Bạn đã đánh giá tất cả sản phẩm của đơn hàng #$orderId hoặc sản phẩm không còn kinh doanh.", Toast.LENGTH_LONG).show()
                openOrder(orderId)
            }
            pending.size == 1 -> {
                val p = pending.first()
                startActivity(ReviewProductActivity.intent(this, orderId, p.productId, p.productName, p.imageName))
            }
            else -> AlertDialog.Builder(this)
                .setTitle("Chọn sản phẩm muốn đánh giá")
                .setItems(pending.map { it.productName }.toTypedArray()) { _, which ->
                    val p = pending[which]
                    startActivity(ReviewProductActivity.intent(this, orderId, p.productId, p.productName, p.imageName))
                }
                .setNegativeButton("Để sau", null)
                .show()
        }
    }

    private fun openOrder(orderId: Long) {
        startActivity(Intent(this, OrderDetailActivity::class.java).putExtra(OrderDetailActivity.EXTRA_ORDER_ID, orderId))
    }

    private fun loadNotifications() {
        val list = notificationDao.getNotificationsForUser(sessionManager.getUserId())
        adapter.updateData(list)
        binding.layoutEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.swipeRefresh.isRefreshing = false
    }
}
