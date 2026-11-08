package com.example.jewelrymanagement.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.jewelrymanagement.models.Product
import com.example.jewelrymanagement.utils.DateUtils
import com.example.jewelrymanagement.utils.ProductImageUtils

class ProductDao(context: Context) {
    private val appContext = context.applicationContext
    private val dbHelper = DatabaseHelper.getInstance(context)

    companion object {
        private val SELECT_WITH_CATEGORY = """
            SELECT p.*, c.${DatabaseHelper.COL_CAT_NAME} AS category_name
            FROM ${DatabaseHelper.TABLE_PRODUCTS} p
            LEFT JOIN ${DatabaseHelper.TABLE_CATEGORIES} c ON p.${DatabaseHelper.COL_PROD_CATEGORY_ID} = c.${DatabaseHelper.COL_CAT_ID}
        """.trimIndent()

        fun fromCursor(cursor: Cursor): Product {
            fun str(col: String): String {
                val i = cursor.getColumnIndex(col)
                return if (i >= 0 && !cursor.isNull(i)) cursor.getString(i) else ""
            }
            return Product(
                id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROD_ID)),
                name = str(DatabaseHelper.COL_PROD_NAME),
                description = str(DatabaseHelper.COL_PROD_DESCRIPTION),
                price = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROD_PRICE)),
                quantity = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROD_QUANTITY)),
                categoryId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROD_CATEGORY_ID)),
                categoryName = str("category_name"),
                material = str(DatabaseHelper.COL_PROD_MATERIAL),
                weight = str(DatabaseHelper.COL_PROD_WEIGHT),
                imageName = str(DatabaseHelper.COL_PROD_IMAGE_NAME).ifBlank { "img_jewelry_placeholder" },
                createdAt = str(DatabaseHelper.COL_PROD_CREATED_AT),
                gemType = str(DatabaseHelper.COL_PROD_GEM_TYPE),
                purity = str(DatabaseHelper.COL_PROD_PURITY),
                size = str(DatabaseHelper.COL_PROD_SIZE),
                warranty = str(DatabaseHelper.COL_PROD_WARRANTY)
            )
        }
    }

    fun insert(product: Product): Long {
        val cv = toValues(product).apply { put(DatabaseHelper.COL_PROD_CREATED_AT, DateUtils.now()) }
        return dbHelper.writableDatabase.insert(DatabaseHelper.TABLE_PRODUCTS, null, cv)
    }

    fun update(product: Product): Boolean =
        dbHelper.writableDatabase.update(
            DatabaseHelper.TABLE_PRODUCTS, toValues(product),
            "${DatabaseHelper.COL_PROD_ID} = ?",
            arrayOf(product.id.toString())
        ) > 0

    /**
     * Products that appear in past orders are kept (renamed "[Ngừng kinh doanh] ..." with stock 0)
     * so order history, reviews and favorites stay valid. Others are deleted. Either way the product
     * is removed from every cart.
     */
    fun delete(productId: Long): Pair<Boolean, String> {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val product = getById(productId) ?: return Pair(false, "Không tìm thấy sản phẩm!")
            var orderCount = 0
            db.rawQuery(
                "SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_ORDER_DETAILS} WHERE ${DatabaseHelper.COL_DETAIL_PROD_ID} = ?",
                arrayOf(productId.toString())
            ).use { c -> if (c.moveToFirst()) orderCount = c.getInt(0) }

            db.delete(DatabaseHelper.TABLE_CART, "${DatabaseHelper.COL_CART_PROD_ID} = ?", arrayOf(productId.toString()))

            val message = if (orderCount > 0) {
                val cv = ContentValues().apply {
                    put(DatabaseHelper.COL_PROD_QUANTITY, 0)
                    if (!product.isDiscontinued) put(DatabaseHelper.COL_PROD_NAME, "${Product.DISCONTINUED_PREFIX} ${product.name}")
                }
                db.update(DatabaseHelper.TABLE_PRODUCTS, cv, "${DatabaseHelper.COL_PROD_ID} = ?", arrayOf(productId.toString()))
                "Sản phẩm đã có trong $orderCount đơn hàng, hệ thống chuyển sang trạng thái [Ngừng kinh doanh, hết hàng] để bảo toàn lịch sử hóa đơn!"
            } else {
                db.delete(DatabaseHelper.TABLE_PRODUCTS, "${DatabaseHelper.COL_PROD_ID} = ?", arrayOf(productId.toString()))
                "Xóa sản phẩm thành công!"
            }
            db.setTransactionSuccessful()
            if (orderCount == 0) ProductImageUtils.delete(appContext, product.imageName)
            return Pair(true, message)
        } catch (e: Exception) {
            return Pair(false, "Không thể xóa sản phẩm: ${e.message}")
        } finally {
            db.endTransaction()
        }
    }

    fun getById(id: Long): Product? =
        query("$SELECT_WITH_CATEGORY WHERE p.${DatabaseHelper.COL_PROD_ID} = ?", arrayOf(id.toString())).firstOrNull()

    fun getByIds(ids: List<Long>): List<Product> {
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        val found = query(
            "$SELECT_WITH_CATEGORY WHERE p.${DatabaseHelper.COL_PROD_ID} IN ($placeholders)",
            ids.map { it.toString() }.toTypedArray()
        ).associateBy { it.id }
        return ids.mapNotNull { found[it] }
    }

    fun getAll(): List<Product> =
        query("$SELECT_WITH_CATEGORY ORDER BY p.${DatabaseHelper.COL_PROD_ID} DESC", null)

    fun searchAndFilter(
        nameQuery: String = "",
        categoryId: Long = 0,
        stockFilter: String = "ALL", // "ALL", "IN_STOCK", "OUT_OF_STOCK", "LOW_STOCK"
        sortByPrice: String = "NONE" // "NONE", "ASC", "DESC"
    ): List<Product> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()

        if (nameQuery.isNotBlank()) {
            conditions.add("p.${DatabaseHelper.COL_PROD_NAME} LIKE ?")
            args.add("%${nameQuery.trim()}%")
        }
        if (categoryId > 0) {
            conditions.add("p.${DatabaseHelper.COL_PROD_CATEGORY_ID} = ?")
            args.add(categoryId.toString())
        }
        when (stockFilter) {
            "IN_STOCK" -> conditions.add("p.${DatabaseHelper.COL_PROD_QUANTITY} > 0")
            "OUT_OF_STOCK" -> conditions.add("p.${DatabaseHelper.COL_PROD_QUANTITY} <= 0")
            "LOW_STOCK" -> conditions.add("p.${DatabaseHelper.COL_PROD_QUANTITY} > 0 AND p.${DatabaseHelper.COL_PROD_QUANTITY} <= 5")
        }

        val where = if (conditions.isNotEmpty()) "WHERE " + conditions.joinToString(" AND ") else ""
        val order = when (sortByPrice) {
            "ASC" -> "ORDER BY p.${DatabaseHelper.COL_PROD_PRICE} ASC"
            "DESC" -> "ORDER BY p.${DatabaseHelper.COL_PROD_PRICE} DESC"
            else -> "ORDER BY p.${DatabaseHelper.COL_PROD_ID} DESC"
        }
        return query("$SELECT_WITH_CATEGORY $where $order", if (args.isEmpty()) null else args.toTypedArray())
    }

    fun getLowStockProducts(threshold: Int = 5): List<Product> =
        query(
            "$SELECT_WITH_CATEGORY WHERE p.${DatabaseHelper.COL_PROD_QUANTITY} <= ? ORDER BY p.${DatabaseHelper.COL_PROD_QUANTITY} ASC",
            arrayOf(threshold.toString())
        )

    /** Units sold in non-cancelled orders. */
    fun getBestSellers(limit: Int = 5): List<Pair<Product, Int>> {
        val list = mutableListOf<Pair<Product, Int>>()
        val sql = """
            SELECT p.*, c.${DatabaseHelper.COL_CAT_NAME} AS category_name, SUM(d.${DatabaseHelper.COL_DETAIL_QUANTITY}) AS total_sold
            FROM ${DatabaseHelper.TABLE_ORDER_DETAILS} d
            INNER JOIN ${DatabaseHelper.TABLE_ORDERS} o ON o.${DatabaseHelper.COL_ORDER_ID} = d.${DatabaseHelper.COL_DETAIL_ORDER_ID}
            INNER JOIN ${DatabaseHelper.TABLE_PRODUCTS} p ON d.${DatabaseHelper.COL_DETAIL_PROD_ID} = p.${DatabaseHelper.COL_PROD_ID}
            LEFT JOIN ${DatabaseHelper.TABLE_CATEGORIES} c ON p.${DatabaseHelper.COL_PROD_CATEGORY_ID} = c.${DatabaseHelper.COL_CAT_ID}
            WHERE o.${DatabaseHelper.COL_ORDER_STATUS} <> ?
            GROUP BY p.${DatabaseHelper.COL_PROD_ID}
            ORDER BY total_sold DESC
            LIMIT ?
        """.trimIndent()
        dbHelper.readableDatabase.rawQuery(sql, arrayOf(DatabaseHelper.STATUS_CANCELLED, limit.toString())).use { c ->
            while (c.moveToNext()) list.add(fromCursor(c) to c.getInt(c.getColumnIndexOrThrow("total_sold")))
        }
        return list
    }

    fun updateStock(productId: Long, newStock: Int): Boolean {
        val cv = ContentValues().apply { put(DatabaseHelper.COL_PROD_QUANTITY, newStock.coerceAtLeast(0)) }
        return dbHelper.writableDatabase.update(
            DatabaseHelper.TABLE_PRODUCTS, cv,
            "${DatabaseHelper.COL_PROD_ID} = ?",
            arrayOf(productId.toString())
        ) > 0
    }

    fun getProductCount(): Int {
        dbHelper.readableDatabase.rawQuery("SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_PRODUCTS}", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun getAllProducts(): List<Product> = getAll()
    fun getProductById(id: Long): Product? = getById(id)

    private fun query(sql: String, args: Array<String>?): List<Product> {
        val list = mutableListOf<Product>()
        dbHelper.readableDatabase.rawQuery(sql, args).use { c -> while (c.moveToNext()) list.add(fromCursor(c)) }
        return list
    }

    private fun toValues(product: Product) = ContentValues().apply {
        put(DatabaseHelper.COL_PROD_NAME, product.name.trim())
        put(DatabaseHelper.COL_PROD_DESCRIPTION, product.description.trim())
        put(DatabaseHelper.COL_PROD_PRICE, product.price)
        put(DatabaseHelper.COL_PROD_QUANTITY, product.quantity)
        put(DatabaseHelper.COL_PROD_CATEGORY_ID, product.categoryId)
        put(DatabaseHelper.COL_PROD_MATERIAL, product.material.trim())
        put(DatabaseHelper.COL_PROD_WEIGHT, product.weight.trim())
        put(DatabaseHelper.COL_PROD_IMAGE_NAME, product.imageName)
        put(DatabaseHelper.COL_PROD_GEM_TYPE, product.gemType.trim())
        put(DatabaseHelper.COL_PROD_PURITY, product.purity.trim())
        put(DatabaseHelper.COL_PROD_SIZE, product.size.trim())
        put(DatabaseHelper.COL_PROD_WARRANTY, product.warranty.trim())
    }
}
