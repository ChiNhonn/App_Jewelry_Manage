package com.example.jewelrymanagement.models

import java.io.Serializable

data class Favorite(
    val id: Long = 0,
    val userId: Long,
    val productId: Long,
    val createdAt: String = "",
    val product: Product? = null
) : Serializable
