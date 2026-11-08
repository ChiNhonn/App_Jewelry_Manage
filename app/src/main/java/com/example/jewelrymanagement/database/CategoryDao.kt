package com.example.jewelrymanagement.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.jewelrymanagement.models.Category

class CategoryDao(context: Context) {
    private val dbHelper = DatabaseHelper.getInstance(context)

    fun insert(category: Category): Long {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put(DatabaseHelper.COL_CAT_NAME, category.name.trim())
            put(DatabaseHelper.COL_CAT_DESCRIPTION, category.description.trim())
        }
        return db.insert(DatabaseHelper.TABLE_CATEGORIES, null, cv)
    }

    fun update(category: Category): Boolean {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put(DatabaseHelper.COL_CAT_NAME, category.name.trim())
            put(DatabaseHelper.COL_CAT_DESCRIPTION, category.description.trim())
        }
        val affected = db.update(
            DatabaseHelper.TABLE_CATEGORIES,
            cv,
            "${DatabaseHelper.COL_CAT_ID} = ?",
            arrayOf(category.id.toString())
        )
        return affected > 0
    }

    fun delete(categoryId: Long): Pair<Boolean, String> {
        val db = dbHelper.writableDatabase

        // Check if there are products belonging to this category
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_PRODUCTS} WHERE ${DatabaseHelper.COL_PROD_CATEGORY_ID} = ?",
            arrayOf(categoryId.toString())
        )
        var productCount = 0
        if (cursor.moveToFirst()) {
            productCount = cursor.getInt(0)
        }
        cursor.close()

        if (productCount > 0) {
            return Pair(false, "Không thể xóa danh mục này vì đang có $productCount sản phẩm trực thuộc!")
        }

        val affected = db.delete(
            DatabaseHelper.TABLE_CATEGORIES,
            "${DatabaseHelper.COL_CAT_ID} = ?",
            arrayOf(categoryId.toString())
        )
        return if (affected > 0) {
            Pair(true, "Xóa danh mục thành công!")
        } else {
            Pair(false, "Không tìm thấy danh mục cần xóa!")
        }
    }

    fun getAll(): List<Category> {
        val list = mutableListOf<Category>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM ${DatabaseHelper.TABLE_CATEGORIES} ORDER BY ${DatabaseHelper.COL_CAT_NAME} ASC",
            null
        )
        while (cursor.moveToNext()) {
            list.add(cursorToCategory(cursor))
        }
        cursor.close()
        return list
    }

    fun getAllCategories(): List<Category> = getAll()

    fun getById(id: Long): Category? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM ${DatabaseHelper.TABLE_CATEGORIES} WHERE ${DatabaseHelper.COL_CAT_ID} = ?",
            arrayOf(id.toString())
        )
        var category: Category? = null
        if (cursor.moveToFirst()) {
            category = cursorToCategory(cursor)
        }
        cursor.close()
        return category
    }

    fun getCategoryById(id: Long): Category? = getById(id)

    fun getCategoryCount(): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_CATEGORIES}", null)
        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()
        return count
    }

    private fun cursorToCategory(cursor: Cursor): Category {
        return Category(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CAT_ID)),
            name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CAT_NAME)),
            description = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CAT_DESCRIPTION)) ?: ""
        )
    }
}
