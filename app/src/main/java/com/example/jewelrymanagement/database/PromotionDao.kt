package com.example.jewelrymanagement.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.jewelrymanagement.models.Promotion
import com.example.jewelrymanagement.utils.DateUtils

class PromotionDao(context: Context) {
    private val dbHelper = DatabaseHelper.getInstance(context)

    /** Inserts the event and its targets atomically. Returns the new id or -1. */
    fun insert(promo: Promotion): Long {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val cv = toContentValues(promo).apply { put(DatabaseHelper.COL_PROMO_CREATED_AT, DateUtils.now()) }
            val id = db.insert(DatabaseHelper.TABLE_PROMOTIONS, null, cv)
            if (id <= 0) return -1
            writeTargets(db, id, promo)
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    fun update(promo: Promotion): Boolean {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val affected = db.update(
                DatabaseHelper.TABLE_PROMOTIONS,
                toContentValues(promo),
                "${DatabaseHelper.COL_PROMO_ID} = ?",
                arrayOf(promo.id.toString())
            )
            if (affected <= 0) return false
            db.delete(DatabaseHelper.TABLE_PROMO_TARGETS, "${DatabaseHelper.COL_PT_PROMO_ID} = ?", arrayOf(promo.id.toString()))
            writeTargets(db, promo.id, promo)
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    fun delete(id: Long): Boolean {
        val affected = dbHelper.writableDatabase.delete(
            DatabaseHelper.TABLE_PROMOTIONS,
            "${DatabaseHelper.COL_PROMO_ID} = ?",
            arrayOf(id.toString())
        )
        return affected > 0
    }

    fun toggleStatus(id: Long, isActive: Boolean): Boolean {
        val cv = ContentValues().apply { put(DatabaseHelper.COL_PROMO_IS_ACTIVE, if (isActive) 1 else 0) }
        return dbHelper.writableDatabase.update(
            DatabaseHelper.TABLE_PROMOTIONS, cv, "${DatabaseHelper.COL_PROMO_ID} = ?", arrayOf(id.toString())
        ) > 0
    }

    fun getAllPromotions(): List<Promotion> = queryPromotions(dbHelper.readableDatabase, null, null)

    fun getPromotionById(id: Long): Promotion? =
        queryPromotions(dbHelper.readableDatabase, "p.${DatabaseHelper.COL_PROMO_ID} = ?", arrayOf(id.toString())).firstOrNull()

    /**
     * Events that are enabled and whose [start, end] window contains the current time.
     * Dates are stored as sortable strings, so the window check happens in SQL.
     */
    fun getRunningPromotions(db: SQLiteDatabase = dbHelper.readableDatabase): List<Promotion> {
        val now = DateUtils.now()
        return queryPromotions(
            db,
            "p.${DatabaseHelper.COL_PROMO_IS_ACTIVE} = 1 AND p.${DatabaseHelper.COL_PROMO_START_DATE} <= ? AND p.${DatabaseHelper.COL_PROMO_END_DATE} >= ?",
            arrayOf(now, now)
        )
    }

    /** Promotions whose validity window overlaps [startDay 00:00, endDay 23:59:59]. */
    fun getPromotionsOverlapping(startDay: String, endDay: String): List<Promotion> = queryPromotions(
        dbHelper.readableDatabase,
        "p.${DatabaseHelper.COL_PROMO_START_DATE} <= ? AND p.${DatabaseHelper.COL_PROMO_END_DATE} >= ?",
        arrayOf("$endDay 23:59:59", "$startDay 00:00:00")
    )

    fun isCodeExists(code: String, excludeId: Long = 0): Boolean {
        dbHelper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_PROMOTIONS} WHERE ${DatabaseHelper.COL_PROMO_CODE} = ? AND ${DatabaseHelper.COL_PROMO_ID} != ?",
            arrayOf(code.trim().uppercase(), excludeId.toString())
        ).use { c -> return c.moveToFirst() && c.getInt(0) > 0 }
    }

    private fun toContentValues(promo: Promotion) = ContentValues().apply {
        put(DatabaseHelper.COL_PROMO_CODE, promo.code.trim().uppercase())
        put(DatabaseHelper.COL_PROMO_NAME, promo.name.trim())
        put(DatabaseHelper.COL_PROMO_DESCRIPTION, promo.description.trim())
        put(DatabaseHelper.COL_PROMO_DISCOUNT_TYPE, promo.discountType)
        put(DatabaseHelper.COL_PROMO_DISCOUNT_VALUE, promo.discountValue)
        put(DatabaseHelper.COL_PROMO_START_DATE, promo.startDate)
        put(DatabaseHelper.COL_PROMO_END_DATE, promo.endDate)
        put(DatabaseHelper.COL_PROMO_TARGET_TYPE, promo.targetType)
        put(DatabaseHelper.COL_PROMO_IS_ACTIVE, if (promo.isActive) 1 else 0)
    }

    private fun writeTargets(db: SQLiteDatabase, promoId: Long, promo: Promotion) {
        when (promo.targetType) {
            Promotion.TARGET_CATEGORY -> promo.targetCategoryIds.distinct().forEach { cid ->
                db.insertOrThrow(DatabaseHelper.TABLE_PROMO_TARGETS, null, ContentValues().apply {
                    put(DatabaseHelper.COL_PT_PROMO_ID, promoId)
                    put(DatabaseHelper.COL_PT_CAT_ID, cid)
                })
            }
            Promotion.TARGET_PRODUCT -> promo.targetProductIds.distinct().forEach { pid ->
                db.insertOrThrow(DatabaseHelper.TABLE_PROMO_TARGETS, null, ContentValues().apply {
                    put(DatabaseHelper.COL_PT_PROMO_ID, promoId)
                    put(DatabaseHelper.COL_PT_PROD_ID, pid)
                })
            }
        }
    }

    private fun queryPromotions(db: SQLiteDatabase, where: String?, args: Array<String>?): List<Promotion> {
        val base = mutableListOf<Promotion>()
        val sql = "SELECT p.* FROM ${DatabaseHelper.TABLE_PROMOTIONS} p" +
            (if (where != null) " WHERE $where" else "") +
            " ORDER BY p.${DatabaseHelper.COL_PROMO_START_DATE} DESC, p.${DatabaseHelper.COL_PROMO_ID} DESC"
        db.rawQuery(sql, args).use { c -> while (c.moveToNext()) base.add(cursorToPromotion(c)) }
        if (base.isEmpty()) return base

        val categoryTargets = mutableMapOf<Long, MutableList<Pair<Long, String>>>()
        val productTargets = mutableMapOf<Long, MutableList<Pair<Long, String>>>()
        val placeholders = base.joinToString(",") { "?" }
        db.rawQuery(
            """
            SELECT t.${DatabaseHelper.COL_PT_PROMO_ID}, t.${DatabaseHelper.COL_PT_CAT_ID}, c.${DatabaseHelper.COL_CAT_NAME},
                   t.${DatabaseHelper.COL_PT_PROD_ID}, pr.${DatabaseHelper.COL_PROD_NAME}
            FROM ${DatabaseHelper.TABLE_PROMO_TARGETS} t
            LEFT JOIN ${DatabaseHelper.TABLE_CATEGORIES} c ON c.${DatabaseHelper.COL_CAT_ID} = t.${DatabaseHelper.COL_PT_CAT_ID}
            LEFT JOIN ${DatabaseHelper.TABLE_PRODUCTS} pr ON pr.${DatabaseHelper.COL_PROD_ID} = t.${DatabaseHelper.COL_PT_PROD_ID}
            WHERE t.${DatabaseHelper.COL_PT_PROMO_ID} IN ($placeholders)
            ORDER BY t.${DatabaseHelper.COL_PT_ID}
            """.trimIndent(),
            base.map { it.id.toString() }.toTypedArray()
        ).use { c ->
            while (c.moveToNext()) {
                val promoId = c.getLong(0)
                if (!c.isNull(1)) {
                    categoryTargets.getOrPut(promoId) { mutableListOf() }.add(c.getLong(1) to (c.getString(2) ?: "#${c.getLong(1)}"))
                } else if (!c.isNull(3)) {
                    productTargets.getOrPut(promoId) { mutableListOf() }.add(c.getLong(3) to (c.getString(4) ?: "#${c.getLong(3)}"))
                }
            }
        }

        return base.map { p ->
            val cats = categoryTargets[p.id].orEmpty()
            val prods = productTargets[p.id].orEmpty()
            p.copy(
                targetCategoryIds = cats.map { it.first },
                targetProductIds = prods.map { it.first },
                targetNames = when (p.targetType) {
                    Promotion.TARGET_CATEGORY -> cats.map { it.second }
                    Promotion.TARGET_PRODUCT -> prods.map { it.second }
                    else -> emptyList()
                }
            )
        }
    }

    private fun cursorToPromotion(cursor: Cursor): Promotion = Promotion(
        id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROMO_ID)),
        code = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROMO_CODE)),
        name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROMO_NAME)),
        description = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROMO_DESCRIPTION)) ?: "",
        discountType = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROMO_DISCOUNT_TYPE)),
        discountValue = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROMO_DISCOUNT_VALUE)),
        startDate = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROMO_START_DATE)),
        endDate = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROMO_END_DATE)),
        targetType = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROMO_TARGET_TYPE)) ?: Promotion.TARGET_ALL,
        isActive = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROMO_IS_ACTIVE)) == 1,
        createdAt = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROMO_CREATED_AT)) ?: ""
    )
}
