package com.example.jewelrymanagement.models

import java.io.Serializable

data class Order(
    val id: Long = 0,
    val userId: Long,
    val receiverName: String,
    val phone: String,
    val shippingAddress: String,
    /** Sum of original prices x quantity at order time. */
    val totalAmount: Double,
    /** Total discount (promotion + coupon). */
    val discountAmount: Double = 0.0,
    val promotionDiscount: Double = 0.0,
    val couponDiscount: Double = 0.0,
    val couponCode: String = "",
    val finalAmount: Double = totalAmount,
    val paymentMethod: String, // "COD" or "Mô phỏng Chuyển khoản"
    val paymentStatus: String, // "Chưa thanh toán", "Đã thanh toán"
    var orderStatus: String,   // "Chờ xác nhận", "Đã xác nhận", "Đang giao hàng", "Đã giao hàng", "Đã hủy"
    val createdAt: String
) : Serializable
