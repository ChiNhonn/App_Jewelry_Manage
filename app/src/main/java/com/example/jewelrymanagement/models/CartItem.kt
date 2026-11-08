package com.example.jewelrymanagement.models

import java.io.Serializable

data class CartItem(
    val id: Long = 0,
    val userId: Long,
    val productId: Long,
    var quantity: Int,
    val productName: String = "",
    val productPrice: Double = 0.0,
    val productImage: String = "img_jewelry_placeholder",
    val availableStock: Int = 0
) : Serializable {
    val subtotal: Double
        get() = productPrice * quantity
}
