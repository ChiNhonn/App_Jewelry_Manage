package com.example.jewelrymanagement.database

import android.content.ContentValues
import android.content.Context
import com.example.jewelrymanagement.models.Product
import com.example.jewelrymanagement.utils.DateUtils

class FavoriteDao(context: Context) {
    private val dbHelper = DatabaseHelper.getInstance(context)

    /** Unique (user_id, product_id) index makes repeated adds a no-op. */
    fun addFavorite(userId: Long, productId: Long): Boolean {
        if (isFavorite(userId, productId)) return true
        val cv = ContentValues().apply {
            put(DatabaseHelper.COL_FAV_USER_ID, userId)
            put(DatabaseHelper.COL_FAV_PROD_ID, productId)
            put(DatabaseHelper.COL_FAV_CREATED_AT, DateUtils.now())
        }
        return dbHelper.writableDatabase.insertWithOnConflict(
            DatabaseHelper.TABLE_FAVORITES, null, cv,
            android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
        ) > 0 || isFavorite(userId, productId)
    }

    fun removeFavorite(userId: Long, productId: Long): Boolean =
        dbHelper.writableDatabase.delete(
            DatabaseHelper.TABLE_FAVORITES,
            "${DatabaseHelper.COL_FAV_USER_ID} = ? AND ${DatabaseHelper.COL_FAV_PROD_ID} = ?",
            arrayOf(userId.toString(), productId.toString())
        ) > 0

    fun isFavorite(userId: Long, productId: Long): Boolean {
        dbHelper.readableDatabase.rawQuery(
            "SELECT 1 FROM ${DatabaseHelper.TABLE_FAVORITES} WHERE ${DatabaseHelper.COL_FAV_USER_ID} = ? AND ${DatabaseHelper.COL_FAV_PROD_ID} = ? LIMIT 1",
            arrayOf(userId.toString(), productId.toString())
        ).use { c -> return c.moveToFirst() }
    }

    fun getFavoriteIds(userId: Long): Set<Long> {
        val ids = mutableSetOf<Long>()
        dbHelper.readableDatabase.rawQuery(
            "SELECT ${DatabaseHelper.COL_FAV_PROD_ID} FROM ${DatabaseHelper.TABLE_FAVORITES} WHERE ${DatabaseHelper.COL_FAV_USER_ID} = ?",
            arrayOf(userId.toString())
        ).use { c -> while (c.moveToNext()) ids.add(c.getLong(0)) }
        return ids
    }

    /** Returns the new state: true when the product is now a favorite. */
    fun toggleFavorite(userId: Long, productId: Long): Boolean =
        if (isFavorite(userId, productId)) {
            removeFavorite(userId, productId)
            false
        } else {
            addFavorite(userId, productId)
        }

    fun getFavoriteProducts(userId: Long): List<Product> {
        val list = mutableListOf<Product>()
        val sql = """
            SELECT p.*, c.${DatabaseHelper.COL_CAT_NAME} AS category_name
            FROM ${DatabaseHelper.TABLE_FAVORITES} f
            INNER JOIN ${DatabaseHelper.TABLE_PRODUCTS} p ON p.${DatabaseHelper.COL_PROD_ID} = f.${DatabaseHelper.COL_FAV_PROD_ID}
            LEFT JOIN ${DatabaseHelper.TABLE_CATEGORIES} c ON c.${DatabaseHelper.COL_CAT_ID} = p.${DatabaseHelper.COL_PROD_CATEGORY_ID}
            WHERE f.${DatabaseHelper.COL_FAV_USER_ID} = ?
            ORDER BY f.${DatabaseHelper.COL_FAV_ID} DESC
        """.trimIndent()
        dbHelper.readableDatabase.rawQuery(sql, arrayOf(userId.toString())).use { c ->
            while (c.moveToNext()) list.add(ProductDao.fromCursor(c))
        }
        return list
    }
}
