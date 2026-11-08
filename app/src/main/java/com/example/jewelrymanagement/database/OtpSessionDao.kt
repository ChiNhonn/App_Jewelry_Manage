package com.example.jewelrymanagement.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.jewelrymanagement.database.DatabaseHelper.Companion.COL_OTP_EMAIL
import com.example.jewelrymanagement.database.DatabaseHelper.Companion.COL_OTP_EXPIRES_AT
import com.example.jewelrymanagement.database.DatabaseHelper.Companion.COL_OTP_FAILED_ATTEMPTS
import com.example.jewelrymanagement.database.DatabaseHelper.Companion.COL_OTP_HASH
import com.example.jewelrymanagement.database.DatabaseHelper.Companion.COL_OTP_ID
import com.example.jewelrymanagement.database.DatabaseHelper.Companion.COL_OTP_LAST_SENT_AT
import com.example.jewelrymanagement.database.DatabaseHelper.Companion.COL_OTP_PURPOSE
import com.example.jewelrymanagement.database.DatabaseHelper.Companion.COL_OTP_SALT
import com.example.jewelrymanagement.database.DatabaseHelper.Companion.COL_OTP_SEND_COUNT
import com.example.jewelrymanagement.database.DatabaseHelper.Companion.COL_OTP_SEND_WINDOW_START
import com.example.jewelrymanagement.database.DatabaseHelper.Companion.COL_OTP_TOKEN_HASH
import com.example.jewelrymanagement.database.DatabaseHelper.Companion.COL_OTP_USED
import com.example.jewelrymanagement.database.DatabaseHelper.Companion.COL_OTP_USER_ID
import com.example.jewelrymanagement.database.DatabaseHelper.Companion.COL_OTP_VERIFIED_AT
import com.example.jewelrymanagement.database.DatabaseHelper.Companion.TABLE_OTP_SESSIONS
import com.example.jewelrymanagement.models.OtpSession

/** Access to `otp_sessions`: one row per (email, purpose), replaced when a new code is sent. */
class OtpSessionDao(context: Context) {
    private val dbHelper = DatabaseHelper.getInstance(context)

    fun get(email: String, purpose: String = OtpSession.PURPOSE_RESET_PASSWORD): OtpSession? =
        dbHelper.readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_OTP_SESSIONS WHERE $COL_OTP_EMAIL = ? AND $COL_OTP_PURPOSE = ?",
            arrayOf(normalize(email), purpose)
        ).use { c -> if (c.moveToFirst()) fromCursor(c) else null }

    /** Stores a freshly sent code, replacing any previous request for the same email. */
    fun save(session: OtpSession): Long {
        val cv = ContentValues().apply {
            put(COL_OTP_USER_ID, session.userId)
            put(COL_OTP_EMAIL, normalize(session.email))
            put(COL_OTP_PURPOSE, session.purpose)
            put(COL_OTP_HASH, session.otpHash)
            put(COL_OTP_SALT, session.otpSalt)
            put(COL_OTP_EXPIRES_AT, session.expiresAt)
            put(COL_OTP_LAST_SENT_AT, session.lastSentAt)
            put(COL_OTP_SEND_COUNT, session.sendCount)
            put(COL_OTP_SEND_WINDOW_START, session.sendWindowStart)
            put(COL_OTP_FAILED_ATTEMPTS, session.failedAttempts)
            session.verifiedAt?.let { put(COL_OTP_VERIFIED_AT, it) } ?: putNull(COL_OTP_VERIFIED_AT)
            session.resetTokenHash?.let { put(COL_OTP_TOKEN_HASH, it) } ?: putNull(COL_OTP_TOKEN_HASH)
            put(COL_OTP_USED, if (session.used) 1 else 0)
        }
        return dbHelper.writableDatabase.insertWithOnConflict(
            TABLE_OTP_SESSIONS, null, cv, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    /** Returns the new number of failed attempts. */
    fun incrementFailedAttempts(id: Long): Int {
        val db = dbHelper.writableDatabase
        db.execSQL(
            "UPDATE $TABLE_OTP_SESSIONS SET $COL_OTP_FAILED_ATTEMPTS = $COL_OTP_FAILED_ATTEMPTS + 1 WHERE $COL_OTP_ID = ?",
            arrayOf(id)
        )
        db.rawQuery("SELECT $COL_OTP_FAILED_ATTEMPTS FROM $TABLE_OTP_SESSIONS WHERE $COL_OTP_ID = ?", arrayOf(id.toString()))
            .use { c -> return if (c.moveToFirst()) c.getInt(0) else Int.MAX_VALUE }
    }

    /** Marks the code as verified (only once, only if still unused) and stores the reset-token hash. */
    fun markVerified(id: Long, verifiedAt: Long, tokenHash: String): Boolean {
        val cv = ContentValues().apply {
            put(COL_OTP_VERIFIED_AT, verifiedAt)
            put(COL_OTP_TOKEN_HASH, tokenHash)
        }
        return dbHelper.writableDatabase.update(
            TABLE_OTP_SESSIONS, cv,
            "$COL_OTP_ID = ? AND $COL_OTP_USED = 0 AND $COL_OTP_VERIFIED_AT IS NULL",
            arrayOf(id.toString())
        ) == 1
    }

    /** Invalidates the code and token. Returns false if it was already used (prevents double use). */
    fun markUsed(id: Long, db: SQLiteDatabase = dbHelper.writableDatabase): Boolean {
        val cv = ContentValues().apply {
            put(COL_OTP_USED, 1)
            putNull(COL_OTP_TOKEN_HASH)
        }
        return db.update(TABLE_OTP_SESSIONS, cv, "$COL_OTP_ID = ? AND $COL_OTP_USED = 0", arrayOf(id.toString())) == 1
    }

    fun delete(id: Long) {
        dbHelper.writableDatabase.delete(TABLE_OTP_SESSIONS, "$COL_OTP_ID = ?", arrayOf(id.toString()))
    }

    private fun normalize(email: String) = email.trim().lowercase()

    private fun fromCursor(c: Cursor): OtpSession {
        fun long(col: String) = c.getLong(c.getColumnIndexOrThrow(col))
        fun str(col: String) = c.getString(c.getColumnIndexOrThrow(col))
        val verifiedIdx = c.getColumnIndexOrThrow(COL_OTP_VERIFIED_AT)
        val tokenIdx = c.getColumnIndexOrThrow(COL_OTP_TOKEN_HASH)
        return OtpSession(
            id = long(COL_OTP_ID),
            userId = long(COL_OTP_USER_ID),
            email = str(COL_OTP_EMAIL),
            purpose = str(COL_OTP_PURPOSE),
            otpHash = str(COL_OTP_HASH),
            otpSalt = str(COL_OTP_SALT),
            expiresAt = long(COL_OTP_EXPIRES_AT),
            lastSentAt = long(COL_OTP_LAST_SENT_AT),
            sendCount = c.getInt(c.getColumnIndexOrThrow(COL_OTP_SEND_COUNT)),
            sendWindowStart = long(COL_OTP_SEND_WINDOW_START),
            failedAttempts = c.getInt(c.getColumnIndexOrThrow(COL_OTP_FAILED_ATTEMPTS)),
            verifiedAt = if (c.isNull(verifiedIdx)) null else c.getLong(verifiedIdx),
            resetTokenHash = if (c.isNull(tokenIdx)) null else c.getString(tokenIdx),
            used = c.getInt(c.getColumnIndexOrThrow(COL_OTP_USED)) == 1
        )
    }
}
