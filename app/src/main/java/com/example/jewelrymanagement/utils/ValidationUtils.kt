package com.example.jewelrymanagement.utils

import android.util.Patterns

object ValidationUtils {
    fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun isValidPhone(phone: String): Boolean {
        // Vietnamese phone format typically 10 digits starting with 0
        val regex = Regex("^0[0-9]{9,10}$")
        return phone.isNotBlank() && regex.matches(phone.trim())
    }

    fun isValidUsername(username: String): Boolean {
        return username.isNotBlank() && username.trim().length >= 3
    }

    fun isValidPassword(password: String): Boolean {
        return password.length >= 6
    }
}
