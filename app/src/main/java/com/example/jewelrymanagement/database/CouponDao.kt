package com.example.jewelrymanagement.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.jewelrymanagement.models.Coupon
import com.example.jewelrymanagement.utils.DateUtils

class CouponDao(context: Context) {
    private val dbHelper = DatabaseHelper.getInstance(context)

    fun insert(coupon: Coupon): Long {
        val cv = toContentValues(coupon).apply {
            put(DatabaseHelper.COL_COUPON_USED_COUNT, 0)
            put(DatabaseHelper.COL_COUPON_CREATED_AT, DateUtils.now())
        }
        return dbHelper.writableDatabase.insert(DatabaseHelper.TABLE_COUPONS, null, cv)
    }

    /** Updates the definition only; used_count is owned by order placement and never overwritten here. */
    fun update(coupon: Coupon): Boolean {
        return dbHelper.writableDatabase.update(
            DatabaseHelper.TABLE_COUPONS,
            toContentValues(coupon),
            "${DatabaseHelper.COL_COUPON_ID} = ?",
            arrayOf(coupon.id.toString())
        ) > 0
    }

    fun delete(id: Long): Boolean {
        return dbHelper.writableDatabase.delete(
            DatabaseHelper.TABLE_COUPONS, "${DatabaseHelper.COL_COUPON_ID} = ?", arrayOf(id.toString())
        ) > 0
    }

    fun toggleStatus(id: Long, isActive: Boolean): Boolean {
        val cv = ContentValues().apply { put(DatabaseHelper.COL_COUPON_IS_ACTIVE, if (isActive) 1 else 0) }
        return dbHelper.writableDatabase.update(
            DatabaseHelper.TABLE_COUPONS, cv, "${DatabaseHelper.COL_COUPON_ID} = ?", arrayOf(id.toString())
        ) > 0
    }

    fun getAllCoupons(): List<Coupon> = query(
        dbHelper.readableDatabase,
        "SELECT * FROM ${DatabaseHelper.TABLE_COUPONS} ORDER BY ${DatabaseHelper.COL_COUPON_ID} DESC",
        null
    )

    fun getCouponById(id: Long): Coupon? = query(
        dbHelper.readableDatabase,
        "SELECT * FROM ${DatabaseHelper.TABLE_COUPONS} WHERE ${DatabaseHelper.COL_COUPON_ID} = ?",
        arrayOf(id.toString())
    ).firstOrNull()

    fun getCouponByCode(code: String, db: SQLiteDatabase = dbHelper.readableDatabase): Coupon? = query(
        db,
        "SELECT * FROM ${DatabaseHelper.TABLE_COUPONS} WHERE ${DatabaseHelper.COL_COUPON_CODE} = ?",
        arrayOf(code.trim().uppercase())
    ).firstOrNull()

    fun isCodeExists(code: String, excludeId: Long = 0): Boolean {
        dbHelper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_COUPONS} WHERE ${DatabaseHelper.COL_COUPON_CODE} = ? AND ${DatabaseHelper.COL_COUPON_ID} != ?",
            arrayOf(code.trim().uppercase(), excludeId.toString())
        ).use { c -> return c.moveToFirst() && c.getInt(0) > 0 }
    }

    /**
     * Atomically consumes one use. Must be called inside the order transaction; returns false
     * if another order used the last slot or the coupon was disabled in the meantime.
     */
    fun consumeUse(db: SQLiteDatabase, couponId: Long): Boolean {
        val stmt = db.compileStatement(
            "UPDATE ${DatabaseHelper.TABLE_COUPONS} SET ${DatabaseHelper.COL_COUPON_USED_COUNT} = ${DatabaseHelper.COL_COUPON_USED_COUNT} + 1 " +
                "WHERE ${DatabaseHelper.COL_COUPON_ID} = ? AND ${DatabaseHelper.COL_COUPON_IS_ACTIVE} = 1 " +
                "AND ${DatabaseHelper.COL_COUPON_USED_COUNT} < ${DatabaseHelper.COL_COUPON_USAGE_LIMIT}"
        )
        stmt.use {
            it.bindLong(1, couponId)
            return it.executeUpdateDelete() == 1
        }
    }

    /** Returns one use when an order that consumed the coupon is cancelled. */
    fun releaseUse(db: SQLiteDatabase, code: String) {
        db.execSQL(
            "UPDATE ${DatabaseHelper.TABLE_COUPONS} SET ${DatabaseHelper.COL_COUPON_USED_COUNT} = ${DatabaseHelper.COL_COUPON_USED_COUNT} - 1 " +
                "WHERE ${DatabaseHelper.COL_COUPON_CODE} = ? AND ${DatabaseHelper.COL_COUPON_USED_COUNT} > 0",
            arrayOf(code.trim().uppercase())
        )
    }

    /** Coupons valid at some point within [startDay, endDay]. */
    fun getCouponsOverlapping(startDay: String, endDay: String): List<Coupon> = query(
        dbHelper.readableDatabase,
        "SELECT * FROM ${DatabaseHelper.TABLE_COUPONS} WHERE ${DatabaseHelper.COL_COUPON_START_DATE} <= ? AND ${DatabaseHelper.COL_COUPON_END_DATE} >= ? ORDER BY ${DatabaseHelper.COL_COUPON_START_DATE} DESC",
        arrayOf("$endDay 23:59:59", "$startDay 00:00:00")
    )

    private fun toContentValues(coupon: Coupon) = ContentValues().apply {
        put(DatabaseHelper.COL_COUPON_CODE, coupon.code.trim().uppercase())
        put(DatabaseHelper.COL_COUPON_TITLE, coupon.title.trim())
        put(DatabaseHelper.COL_COUPON_DISCOUNT_TYPE, coupon.discountType)
        put(DatabaseHelper.COL_COUPON_DISCOUNT_VALUE, coupon.discountValue)
        put(DatabaseHelper.COL_COUPON_MIN_ORDER, coupon.minOrder)
        if (coupon.maxDiscount != null) put(DatabaseHelper.COL_COUPON_MAX_DISCOUNT, coupon.maxDiscount)
        else putNull(DatabaseHelper.COL_COUPON_MAX_DISCOUNT)
        put(DatabaseHelper.COL_COUPON_START_DATE, coupon.startDate)
        put(DatabaseHelper.COL_COUPON_END_DATE, coupon.endDate)
        put(DatabaseHelper.COL_COUPON_USAGE_LIMIT, coupon.usageLimit)
        put(DatabaseHelper.COL_COUPON_IS_ACTIVE, if (coupon.isActive) 1 else 0)
    }

    private fun query(db: SQLiteDatabase, sql: String, args: Array<String>?): List<Coupon> {
        val list = mutableListOf<Coupon>()
        db.rawQuery(sql, args).use { c -> while (c.moveToNext()) list.add(cursorToCoupon(c)) }
        return list
    }

    private fun cursorToCoupon(cursor: Cursor): Coupon {
        val maxCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_COUPON_MAX_DISCOUNT)
        return Coupon(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_COUPON_ID)),
            code = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_COUPON_CODE)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_COUPON_TITLE)),
            discountType = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_COUPON_DISCOUNT_TYPE)),
            discountValue = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_COUPON_DISCOUNT_VALUE)),
            minOrder = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_COUPON_MIN_ORDER)),
            maxDiscount = if (cursor.isNull(maxCol)) null else cursor.getDouble(maxCol),
            startDate = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_COUPON_START_DATE)),
            endDate = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_COUPON_END_DATE)),
            usageLimit = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_COUPON_USAGE_LIMIT)),
            usedCount = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_COUPON_USED_COUNT)),
            isActive = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_COUPON_IS_ACTIVE)) == 1,
            createdAt = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_COUPON_CREATED_AT)) ?: ""
        )
    }
}
