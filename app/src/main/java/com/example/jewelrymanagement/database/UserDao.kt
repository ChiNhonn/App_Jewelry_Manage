package com.example.jewelrymanagement.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.jewelrymanagement.models.User
import com.example.jewelrymanagement.utils.PasswordUtils

class UserDao(context: Context) {
    private val dbHelper = DatabaseHelper.getInstance(context)

    companion object {
        const val ROLE_GUEST = "GUEST"
        private const val GUEST_USERNAME = "__guest__"
        private const val GUEST_EMAIL = "guest@jewelry.local"
        @Volatile private var cachedGuestId: Long = -1
    }

    /** Hidden account that owns carts and orders of shoppers who are not logged in. It can never log in. */
    fun getGuestUserId(): Long {
        if (cachedGuestId > 0) return cachedGuestId
        val db = dbHelper.writableDatabase
        db.insertWithOnConflict(DatabaseHelper.TABLE_USERS, null, ContentValues().apply {
            put(DatabaseHelper.COL_USER_USERNAME, GUEST_USERNAME)
            put(DatabaseHelper.COL_USER_FULL_NAME, "Khách vãng lai")
            put(DatabaseHelper.COL_USER_EMAIL, GUEST_EMAIL)
            put(DatabaseHelper.COL_USER_PASSWORD_HASH, PasswordUtils.hashPassword(java.util.UUID.randomUUID().toString()))
            put(DatabaseHelper.COL_USER_ROLE, ROLE_GUEST)
        }, SQLiteDatabase.CONFLICT_IGNORE)
        db.rawQuery(
            "SELECT ${DatabaseHelper.COL_USER_ID} FROM ${DatabaseHelper.TABLE_USERS} WHERE ${DatabaseHelper.COL_USER_ROLE} = ? AND ${DatabaseHelper.COL_USER_USERNAME} = ?",
            arrayOf(ROLE_GUEST, GUEST_USERNAME)
        ).use { if (it.moveToFirst()) cachedGuestId = it.getLong(0) }
        return cachedGuestId
    }

    fun registerCustomer(user: User): Long {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put(DatabaseHelper.COL_USER_USERNAME, user.username.trim())
            put(DatabaseHelper.COL_USER_FULL_NAME, user.fullName.trim())
            put(DatabaseHelper.COL_USER_EMAIL, user.email.trim())
            put(DatabaseHelper.COL_USER_PHONE, user.phone.trim())
            put(DatabaseHelper.COL_USER_PASSWORD_HASH, PasswordUtils.hashPassword(user.passwordHash))
            put(DatabaseHelper.COL_USER_ROLE, "CUSTOMER")
            put(DatabaseHelper.COL_USER_ADDRESS, user.address.trim())
        }
        return db.insert(DatabaseHelper.TABLE_USERS, null, cv)
    }

    fun isUsernameExists(username: String): Boolean {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT ${DatabaseHelper.COL_USER_ID} FROM ${DatabaseHelper.TABLE_USERS} WHERE ${DatabaseHelper.COL_USER_USERNAME} = ?",
            arrayOf(username.trim())
        )
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    fun isEmailExists(email: String): Boolean {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT ${DatabaseHelper.COL_USER_ID} FROM ${DatabaseHelper.TABLE_USERS} WHERE ${DatabaseHelper.COL_USER_EMAIL} = ?",
            arrayOf(email.trim())
        )
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    fun login(usernameOrEmail: String, passwordRaw: String): User? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM ${DatabaseHelper.TABLE_USERS} WHERE (${DatabaseHelper.COL_USER_USERNAME} = ? OR ${DatabaseHelper.COL_USER_EMAIL} = ?) AND ${DatabaseHelper.COL_USER_ROLE} != '$ROLE_GUEST'",
            arrayOf(usernameOrEmail.trim(), usernameOrEmail.trim())
        )
        var user: User? = null
        var upgradeHash = false
        if (cursor.moveToFirst()) {
            val storedHash = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_USER_PASSWORD_HASH))
            if (PasswordUtils.verifyPassword(passwordRaw, storedHash)) {
                user = cursorToUser(cursor)
                upgradeHash = PasswordUtils.needsRehash(storedHash)
            }
        }
        cursor.close()
        // Legacy SHA-256 / plain rows are migrated to salted PBKDF2 the first time the user logs in.
        if (user != null && upgradeHash) updatePassword(user.id, PasswordUtils.hashPassword(passwordRaw))
        return user
    }

    fun getUserById(id: Long): User? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM ${DatabaseHelper.TABLE_USERS} WHERE ${DatabaseHelper.COL_USER_ID} = ?",
            arrayOf(id.toString())
        )
        var user: User? = null
        if (cursor.moveToFirst()) {
            user = cursorToUser(cursor)
        }
        cursor.close()
        return user
    }

    fun updateProfile(userId: Long, fullName: String, phone: String, address: String): Boolean {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put(DatabaseHelper.COL_USER_FULL_NAME, fullName.trim())
            put(DatabaseHelper.COL_USER_PHONE, phone.trim())
            put(DatabaseHelper.COL_USER_ADDRESS, address.trim())
        }
        val affected = db.update(
            DatabaseHelper.TABLE_USERS,
            cv,
            "${DatabaseHelper.COL_USER_ID} = ?",
            arrayOf(userId.toString())
        )
        return affected > 0
    }

    fun getAllCustomers(): List<User> {
        val list = mutableListOf<User>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM ${DatabaseHelper.TABLE_USERS} WHERE ${DatabaseHelper.COL_USER_ROLE} = 'CUSTOMER' ORDER BY ${DatabaseHelper.COL_USER_ID} DESC",
            null
        )
        while (cursor.moveToNext()) {
            list.add(cursorToUser(cursor))
        }
        cursor.close()
        return list
    }

    fun searchCustomers(query: String): List<User> {
        val list = mutableListOf<User>()
        val db = dbHelper.readableDatabase
        val pattern = "%${query.trim()}%"
        val cursor = db.rawQuery(
            """
            SELECT * FROM ${DatabaseHelper.TABLE_USERS} 
            WHERE ${DatabaseHelper.COL_USER_ROLE} = 'CUSTOMER' 
            AND (${DatabaseHelper.COL_USER_FULL_NAME} LIKE ? OR ${DatabaseHelper.COL_USER_EMAIL} LIKE ? OR ${DatabaseHelper.COL_USER_PHONE} LIKE ?)
            ORDER BY ${DatabaseHelper.COL_USER_ID} DESC
            """.trimIndent(),
            arrayOf(pattern, pattern, pattern)
        )
        while (cursor.moveToNext()) {
            list.add(cursorToUser(cursor))
        }
        cursor.close()
        return list
    }

    fun getCustomerCount(): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_USERS} WHERE ${DatabaseHelper.COL_USER_ROLE} = 'CUSTOMER'",
            null
        )
        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()
        return count
    }

    /**
     * Find a user by their email address (case-insensitive).
     * Used by the forgot-password flow to verify the account exists before sending OTP.
     */
    fun getUserByEmail(email: String): User? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM ${DatabaseHelper.TABLE_USERS} WHERE LOWER(${DatabaseHelper.COL_USER_EMAIL}) = LOWER(?) AND ${DatabaseHelper.COL_USER_ROLE} != '$ROLE_GUEST'",
            arrayOf(email.trim())
        )
        var user: User? = null
        if (cursor.moveToFirst()) {
            user = cursorToUser(cursor)
        }
        cursor.close()
        return user
    }

    /**
     * Update the password hash of one user. Only the password column changes, so profile,
     * cart, favorites and order history are preserved. Pass [db] to run inside a caller's transaction.
     */
    fun updatePassword(
        userId: Long,
        newPasswordHash: String,
        db: SQLiteDatabase = dbHelper.writableDatabase
    ): Boolean {
        val cv = ContentValues().apply {
            put(DatabaseHelper.COL_USER_PASSWORD_HASH, newPasswordHash)
        }
        val affected = db.update(
            DatabaseHelper.TABLE_USERS,
            cv,
            "${DatabaseHelper.COL_USER_ID} = ?",
            arrayOf(userId.toString())
        )
        return affected > 0
    }

    /** Returns false when [currentPassword] does not match the stored password; nothing is changed then. */
    fun changePassword(userId: Long, currentPassword: String, newPassword: String): Boolean {
        val storedHash = dbHelper.readableDatabase.rawQuery(
            "SELECT ${DatabaseHelper.COL_USER_PASSWORD_HASH} FROM ${DatabaseHelper.TABLE_USERS} WHERE ${DatabaseHelper.COL_USER_ID} = ?",
            arrayOf(userId.toString())
        ).use { if (it.moveToFirst()) it.getString(0) else null } ?: return false
        if (!PasswordUtils.verifyPassword(currentPassword, storedHash)) return false
        return updatePassword(userId, PasswordUtils.hashPassword(newPassword))
    }

    fun isLocked(userId: Long): Boolean {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT ${DatabaseHelper.COL_USER_IS_LOCKED} FROM ${DatabaseHelper.TABLE_USERS} WHERE ${DatabaseHelper.COL_USER_ID} = ?",
            arrayOf(userId.toString())
        )
        val locked = cursor.use { it.moveToFirst() && it.getInt(0) == 1 }
        return locked
    }

    /** Locks or unlocks a customer account. Admin and guest accounts cannot be changed here. */
    fun setLocked(userId: Long, locked: Boolean): Boolean {
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_USER_IS_LOCKED, if (locked) 1 else 0)
        }
        return dbHelper.writableDatabase.update(
            DatabaseHelper.TABLE_USERS,
            values,
            "${DatabaseHelper.COL_USER_ID} = ? AND ${DatabaseHelper.COL_USER_ROLE} = 'CUSTOMER'",
            arrayOf(userId.toString())
        ) > 0
    }

    private fun cursorToUser(cursor: Cursor): User {
        return User(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_USER_ID)),
            username = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_USER_USERNAME)),
            fullName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_USER_FULL_NAME)),
            email = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_USER_EMAIL)),
            phone = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_USER_PHONE)) ?: "",
            passwordHash = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_USER_PASSWORD_HASH)),
            role = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_USER_ROLE)),
            address = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_USER_ADDRESS)) ?: "",
            isLocked = cursor.getColumnIndex(DatabaseHelper.COL_USER_IS_LOCKED).let { index ->
                index >= 0 && cursor.getInt(index) == 1
            }
        )
    }
}
