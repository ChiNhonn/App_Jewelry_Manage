package com.example.jewelrymanagement.models

import java.io.Serializable

data class Product(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val price: Double = 0.0,
    val quantity: Int = 0,
    val categoryId: Long,
    val categoryName: String = "",
    val material: String = "",
    val weight: String = "",
    val imageName: String = "img_jewelry_placeholder",
    val createdAt: String = "",
    val gemType: String = "",
    val purity: String = "",
    val size: String = "",
    val warranty: String = ""
) : Serializable {

    /** Products removed by the admin but kept for order history are renamed with this prefix. */
    val isDiscontinued: Boolean
        get() = name.startsWith(DISCONTINUED_PREFIX)

    companion object {
        const val DISCONTINUED_PREFIX = "[Ngừng kinh doanh]"
    }
}
