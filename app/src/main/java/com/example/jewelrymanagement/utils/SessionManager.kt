package com.example.jewelrymanagement.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.jewelrymanagement.database.UserDao
import com.example.jewelrymanagement.models.User

class SessionManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "JewelryUserSession"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_EMAIL = "email"
        private const val KEY_PHONE = "phone"
        private const val KEY_ROLE = "role"
        private const val KEY_ADDRESS = "address"
    }

    fun saveUser(user: User) {
        val editor = prefs.edit()
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.putLong(KEY_USER_ID, user.id)
        editor.putString(KEY_USERNAME, user.username)
        editor.putString(KEY_FULL_NAME, user.fullName)
        editor.putString(KEY_EMAIL, user.email)
        editor.putString(KEY_PHONE, user.phone)
        editor.putString(KEY_ROLE, user.role)
        editor.putString(KEY_ADDRESS, user.address)
        editor.apply()
    }

    fun updateUser(fullName: String, phone: String, address: String) {
        val editor = prefs.edit()
        editor.putString(KEY_FULL_NAME, fullName)
        editor.putString(KEY_PHONE, phone)
        editor.putString(KEY_ADDRESS, address)
        editor.apply()
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun getUserId(): Long {
        return prefs.getLong(KEY_USER_ID, -1)
    }

    /** Owner of the cart and of new orders: the logged-in user, or the shared guest account. */
    fun getShopperId(): Long = if (isLoggedIn()) getUserId() else UserDao(appContext).getGuestUserId()

    fun getUserRole(): String {
        return prefs.getString(KEY_ROLE, "CUSTOMER") ?: "CUSTOMER"
    }

    fun isAdmin(): Boolean {
        return getUserRole() == "ADMIN"
    }

    fun getUser(): User? {
        if (!isLoggedIn()) return null
        return User(
            id = prefs.getLong(KEY_USER_ID, -1),
            username = prefs.getString(KEY_USERNAME, "") ?: "",
            fullName = prefs.getString(KEY_FULL_NAME, "") ?: "",
            email = prefs.getString(KEY_EMAIL, "") ?: "",
            phone = prefs.getString(KEY_PHONE, "") ?: "",
            passwordHash = "", // Do not store password in plain session
            role = prefs.getString(KEY_ROLE, "CUSTOMER") ?: "CUSTOMER",
            address = prefs.getString(KEY_ADDRESS, "") ?: ""
        )
    }

    fun clearSession() {
        val editor = prefs.edit()
        editor.clear()
        editor.apply()
    }
}
