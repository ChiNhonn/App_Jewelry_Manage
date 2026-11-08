package com.example.jewelrymanagement.models

import java.io.Serializable

data class NotificationItem(
    val id: Long = 0,
    /** Recipient: the customer who owns the order, never the admin who changed it. */
    val userId: Long,
    val title: String,
    val content: String,
    val type: String = TYPE_ORDER_DELIVERED,
    val orderId: Long? = null,
    val productId: Long? = null,
    val actionType: String = ACTION_OPEN_ORDER,
    var isRead: Boolean = false,
    val createdAt: String = ""
) : Serializable {
    companion object {
        const val TYPE_ORDER_DELIVERED = "ORDER_DELIVERED"
        const val TYPE_PROMOTION = "PROMOTION"
        const val TYPE_GENERAL = "GENERAL"

        /** Opening the notification asks "Đánh giá ngay" / "Để sau". */
        const val ACTION_REVIEW_ORDER = "REVIEW_ORDER"
        const val ACTION_OPEN_ORDER = "OPEN_ORDER"
        const val ACTION_NONE = "NONE"
    }
}
