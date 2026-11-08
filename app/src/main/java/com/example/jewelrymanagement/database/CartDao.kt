package com.example.jewelrymanagement.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.jewelrymanagement.models.CartItem

class CartDao(context: Context) {
    private val dbHelper = DatabaseHelper.getInstance(context)

    fun addToCart(userId: Long, productId: Long, addQuantity: Int): Pair<Boolean, String> {
        val db = dbHelper.writableDatabase

        // 1. Check current stock of the product
        val prodCursor = db.rawQuery(
            "SELECT ${DatabaseHelper.COL_PROD_QUANTITY}, ${DatabaseHelper.COL_PROD_NAME} FROM ${DatabaseHelper.TABLE_PRODUCTS} WHERE ${DatabaseHelper.COL_PROD_ID} = ?",
            arrayOf(productId.toString())
        )
        if (!prodCursor.moveToFirst()) {
            prodCursor.close()
            return Pair(false, "Sản phẩm không tồn tại!")
        }
        val availableStock = prodCursor.getInt(0)
        val prodName = prodCursor.getString(1)
        prodCursor.close()

        if (addQuantity <= 0) {
            return Pair(false, "Số lượng không hợp lệ!")
        }
        if (prodName.startsWith(com.example.jewelrymanagement.models.Product.DISCONTINUED_PREFIX)) {
            return Pair(false, "Sản phẩm này đã ngừng kinh doanh!")
        }
        if (availableStock <= 0) {
            return Pair(false, "Sản phẩm '$prodName' hiện đã hết hàng!")
        }

        // 2. Check if item already exists in user's cart
        val cartCursor = db.rawQuery(
            "SELECT ${DatabaseHelper.COL_CART_ID}, ${DatabaseHelper.COL_CART_QUANTITY} FROM ${DatabaseHelper.TABLE_CART} WHERE ${DatabaseHelper.COL_CART_USER_ID} = ? AND ${DatabaseHelper.COL_CART_PROD_ID} = ?",
            arrayOf(userId.toString(), productId.toString())
        )

        var result = false
        var message = ""

        if (cartCursor.moveToFirst()) {
            val cartId = cartCursor.getLong(0)
            val currentQtyInCart = cartCursor.getInt(1)
            val newQty = currentQtyInCart + addQuantity

            if (newQty > availableStock) {
                cartCursor.close()
                return Pair(false, "Kho chỉ còn $availableStock sản phẩm, trong giỏ bạn đã có $currentQtyInCart!")
            }

            val cv = ContentValues().apply {
                put(DatabaseHelper.COL_CART_QUANTITY, newQty)
            }
            val affected = db.update(
                DatabaseHelper.TABLE_CART,
                cv,
                "${DatabaseHelper.COL_CART_ID} = ?",
                arrayOf(cartId.toString())
            )
            result = affected > 0
            message = "Đã cập nhật số lượng trong giỏ hàng ($newQty)!"
        } else {
            if (addQuantity > availableStock) {
                cartCursor.close()
                return Pair(false, "Kho chỉ còn $availableStock sản phẩm khả dụng!")
            }

            val cv = ContentValues().apply {
                put(DatabaseHelper.COL_CART_USER_ID, userId)
                put(DatabaseHelper.COL_CART_PROD_ID, productId)
                put(DatabaseHelper.COL_CART_QUANTITY, addQuantity)
            }
            val id = db.insert(DatabaseHelper.TABLE_CART, null, cv)
            result = id > 0
            message = "Đã thêm '$prodName' vào giỏ hàng!"
        }
        cartCursor.close()

        return Pair(result, message)
    }

    fun updateQuantity(cartId: Long, newQuantity: Int): Pair<Boolean, String> {
        val db = dbHelper.writableDatabase

        if (newQuantity <= 0) {
            val deleted = db.delete(
                DatabaseHelper.TABLE_CART,
                "${DatabaseHelper.COL_CART_ID} = ?",
                arrayOf(cartId.toString())
            )
            return Pair(deleted > 0, "Đã xóa sản phẩm khỏi giỏ!")
        }

        // Check product stock limit
        val cursor = db.rawQuery(
            """
            SELECT p.${DatabaseHelper.COL_PROD_QUANTITY}
            FROM ${DatabaseHelper.TABLE_CART} c
            INNER JOIN ${DatabaseHelper.TABLE_PRODUCTS} p ON c.${DatabaseHelper.COL_CART_PROD_ID} = p.${DatabaseHelper.COL_PROD_ID}
            WHERE c.${DatabaseHelper.COL_CART_ID} = ?
            """.trimIndent(),
            arrayOf(cartId.toString())
        )
        if (cursor.moveToFirst()) {
            val stock = cursor.getInt(0)
            if (newQuantity > stock) {
                cursor.close()
                return Pair(false, "Số lượng yêu cầu vượt quá tồn kho khả dụng ($stock)!")
            }
        }
        cursor.close()

        val cv = ContentValues().apply {
            put(DatabaseHelper.COL_CART_QUANTITY, newQuantity)
        }
        val affected = db.update(
            DatabaseHelper.TABLE_CART,
            cv,
            "${DatabaseHelper.COL_CART_ID} = ?",
            arrayOf(cartId.toString())
        )
        return Pair(affected > 0, "Đã cập nhật số lượng!")
    }

    fun removeFromCart(cartId: Long): Boolean {
        val db = dbHelper.writableDatabase
        val affected = db.delete(
            DatabaseHelper.TABLE_CART,
            "${DatabaseHelper.COL_CART_ID} = ?",
            arrayOf(cartId.toString())
        )
        return affected > 0
    }

    fun clearCart(userId: Long): Boolean {
        val db = dbHelper.writableDatabase
        val affected = db.delete(
            DatabaseHelper.TABLE_CART,
            "${DatabaseHelper.COL_CART_USER_ID} = ?",
            arrayOf(userId.toString())
        )
        return affected >= 0
    }

    /** Moves items into [toUserId]'s cart, keeping stock limits; items that no longer fit are dropped. */
    fun mergeCart(fromUserId: Long, toUserId: Long) {
        if (fromUserId == toUserId || fromUserId <= 0 || toUserId <= 0) return
        getCartItems(fromUserId).forEach { addToCart(toUserId, it.productId, it.quantity) }
        clearCart(fromUserId)
    }

    fun getCartItems(userId: Long): List<CartItem> {
        val list = mutableListOf<CartItem>()
        val db = dbHelper.readableDatabase
        val query = """
            SELECT c.${DatabaseHelper.COL_CART_ID}, c.${DatabaseHelper.COL_CART_USER_ID}, c.${DatabaseHelper.COL_CART_PROD_ID}, c.${DatabaseHelper.COL_CART_QUANTITY},
                   p.${DatabaseHelper.COL_PROD_NAME}, p.${DatabaseHelper.COL_PROD_PRICE}, p.${DatabaseHelper.COL_PROD_IMAGE_NAME}, p.${DatabaseHelper.COL_PROD_QUANTITY} AS available_stock
            FROM ${DatabaseHelper.TABLE_CART} c
            INNER JOIN ${DatabaseHelper.TABLE_PRODUCTS} p ON c.${DatabaseHelper.COL_CART_PROD_ID} = p.${DatabaseHelper.COL_PROD_ID}
            WHERE c.${DatabaseHelper.COL_CART_USER_ID} = ?
            ORDER BY c.${DatabaseHelper.COL_CART_ID} DESC
        """.trimIndent()

        val cursor = db.rawQuery(query, arrayOf(userId.toString()))
        while (cursor.moveToNext()) {
            list.add(
                CartItem(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CART_ID)),
                    userId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CART_USER_ID)),
                    productId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CART_PROD_ID)),
                    quantity = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CART_QUANTITY)),
                    productName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROD_NAME)),
                    productPrice = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROD_PRICE)),
                    productImage = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROD_IMAGE_NAME)) ?: "img_jewelry_placeholder",
                    availableStock = cursor.getInt(cursor.getColumnIndexOrThrow("available_stock"))
                )
            )
        }
        cursor.close()
        return list
    }

    fun getCartTotal(userId: Long): Double {
        val items = getCartItems(userId)
        return items.sumOf { it.subtotal }
    }

    fun getCartCount(userId: Long): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT SUM(${DatabaseHelper.COL_CART_QUANTITY}) FROM ${DatabaseHelper.TABLE_CART} WHERE ${DatabaseHelper.COL_CART_USER_ID} = ?",
            arrayOf(userId.toString())
        )
        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()
        return count
    }
}
