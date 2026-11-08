package com.example.jewelrymanagement.models

import java.io.Serializable

data class User(
    val id: Long = 0,
    val username: String,
    val fullName: String,
    val email: String,
    val phone: String = "",
    val passwordHash: String,
    val role: String, // "ADMIN" or "CUSTOMER"
    val address: String = "",
    val isLocked: Boolean = false
) : Serializable
