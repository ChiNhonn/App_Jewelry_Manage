package com.example.jewelrymanagement.models

import java.io.Serializable

data class Category(
    val id: Long = 0,
    val name: String,
    val description: String = ""
) : Serializable {
    // Override toString so ArrayAdapter in Spinner displays the category name directly
    override fun toString(): String {
        return name
    }
}
