package com.example.jewelrymanagement.models

import java.io.Serializable

data class ProductReview(
    val id: Long = 0,
    val productId: Long,
    val orderId: Long,
    val userId: Long,
    val userName: String,
    val rating: Int, // 1 to 5
    val comment: String = "",
    val isVisible: Boolean = true,
    val createdAt: String = "",
    val productName: String = ""
) : Serializable
