package com.example.jewelrymanagement.utils

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import com.example.jewelrymanagement.activities.LoginActivity

/**
 * Guests can browse products freely; account actions (cart, orders, favorites...) call this first.
 * Returns true when a user is signed in, otherwise opens the login screen and returns false.
 */
fun Activity.requireLogin(message: String = "Vui lòng đăng nhập để tiếp tục"): Boolean {
    if (SessionManager(this).isLoggedIn()) return true
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    startActivity(Intent(this, LoginActivity::class.java))
    return false
}
