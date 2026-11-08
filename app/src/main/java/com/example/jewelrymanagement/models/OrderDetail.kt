package com.example.jewelrymanagement.models

import java.io.Serializable

data class OrderDetail(
    val id: Long = 0,
    val orderId: Long,
    /** 0 when the product row no longer exists. */
    val productId: Long,
    val productName: String,
    val quantity: Int,
    /** Price charged per unit (after promotion). */
    val unitPrice: Double,
    val subtotal: Double,
    val originalUnitPrice: Double = unitPrice,
    val promotionDiscount: Double = 0.0,
    val promotionName: String = ""
) : Serializable
