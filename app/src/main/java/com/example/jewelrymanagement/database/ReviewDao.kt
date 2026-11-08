package com.example.jewelrymanagement.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.jewelrymanagement.models.ProductReview
import com.example.jewelrymanagement.utils.DateUtils

data class RatingSummary(
    val averageRating: Double,
    val totalReviews: Int,
    val starCounts: Map<Int, Int>
)

/** One purchased line of a delivered order together with its review state. */
data class ReviewableItem(
    val orderId: Long,
    val productId: Long,
    val productName: String,
    val imageName: String,
    val alreadyReviewed: Boolean
)

class ReviewDao(context: Context) {
    private val dbHelper = DatabaseHelper.getInstance(context)

    companion object {
        const val THANK_YOU_MESSAGE = "Cảm ơn bạn đã dành thời gian đánh giá sản phẩm của JewelryManagement!"
    }

    /**
     * Saves a review after verifying, inside one transaction, that the order exists, belongs to the
     * reviewer, is delivered, contains the product and has not been reviewed yet for that product.
     */
    fun insert(review: ProductReview): Pair<Long, String> {
        if (review.rating !in 1..5) return Pair(-1, "Vui lòng chọn số sao từ 1 đến 5!")
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val error = eligibilityError(review.orderId, review.productId, review.userId)
            if (error != null) return Pair(-1, error)

            val id = db.insertOrThrow(DatabaseHelper.TABLE_REVIEWS, null, ContentValues().apply {
                put(DatabaseHelper.COL_REVIEW_PROD_ID, review.productId)
                put(DatabaseHelper.COL_REVIEW_ORDER_ID, review.orderId)
                put(DatabaseHelper.COL_REVIEW_USER_ID, review.userId)
                put(DatabaseHelper.COL_REVIEW_USER_NAME, review.userName.trim())
                put(DatabaseHelper.COL_REVIEW_RATING, review.rating)
                put(DatabaseHelper.COL_REVIEW_COMMENT, review.comment.trim())
                put(DatabaseHelper.COL_REVIEW_IS_VISIBLE, 1)
                put(DatabaseHelper.COL_REVIEW_CREATED_AT, DateUtils.now())
            })
            db.setTransactionSuccessful()
            return Pair(id, THANK_YOU_MESSAGE)
        } catch (e: Exception) {
            return Pair(-1, "Lỗi khi lưu đánh giá, vui lòng thử lại!")
        } finally {
            db.endTransaction()
        }
    }

    /** Null when the user may review this product of this order, otherwise the Vietnamese reason. */
    fun eligibilityError(orderId: Long, productId: Long, userId: Long): String? {
        val db = dbHelper.readableDatabase
        db.rawQuery(
            "SELECT ${DatabaseHelper.COL_ORDER_STATUS}, ${DatabaseHelper.COL_ORDER_USER_ID} FROM ${DatabaseHelper.TABLE_ORDERS} WHERE ${DatabaseHelper.COL_ORDER_ID} = ?",
            arrayOf(orderId.toString())
        ).use { c ->
            if (!c.moveToFirst()) return "Không tìm thấy đơn hàng #$orderId!"
            if (c.getLong(1) != userId) return "Bạn không có quyền đánh giá đơn hàng của người khác!"
            if (c.getString(0) != DatabaseHelper.STATUS_DELIVERED) {
                return "Chỉ có thể đánh giá sản phẩm khi đơn hàng đã được giao thành công!"
            }
        }
        db.rawQuery(
            "SELECT 1 FROM ${DatabaseHelper.TABLE_ORDER_DETAILS} WHERE ${DatabaseHelper.COL_DETAIL_ORDER_ID} = ? AND ${DatabaseHelper.COL_DETAIL_PROD_ID} = ? LIMIT 1",
            arrayOf(orderId.toString(), productId.toString())
        ).use { c -> if (!c.moveToFirst()) return "Sản phẩm không thuộc đơn hàng #$orderId hoặc đã bị xóa khỏi cửa hàng!" }
        if (hasReviewed(orderId, productId, userId)) {
            return "Bạn đã đánh giá sản phẩm này trong đơn hàng #$orderId rồi!"
        }
        return null
    }

    fun hasReviewed(orderId: Long, productId: Long, userId: Long): Boolean {
        dbHelper.readableDatabase.rawQuery(
            "SELECT 1 FROM ${DatabaseHelper.TABLE_REVIEWS} WHERE ${DatabaseHelper.COL_REVIEW_ORDER_ID} = ? AND ${DatabaseHelper.COL_REVIEW_PROD_ID} = ? AND ${DatabaseHelper.COL_REVIEW_USER_ID} = ? LIMIT 1",
            arrayOf(orderId.toString(), productId.toString(), userId.toString())
        ).use { c -> return c.moveToFirst() }
    }

    /** Products of an order that still exist in the catalog, with whether [userId] already reviewed them. */
    fun getReviewableItems(orderId: Long, userId: Long): List<ReviewableItem> {
        val list = mutableListOf<ReviewableItem>()
        val sql = """
            SELECT d.${DatabaseHelper.COL_DETAIL_PROD_ID}, d.${DatabaseHelper.COL_DETAIL_PROD_NAME},
                   p.${DatabaseHelper.COL_PROD_IMAGE_NAME},
                   EXISTS(SELECT 1 FROM ${DatabaseHelper.TABLE_REVIEWS} r
                          WHERE r.${DatabaseHelper.COL_REVIEW_ORDER_ID} = d.${DatabaseHelper.COL_DETAIL_ORDER_ID}
                            AND r.${DatabaseHelper.COL_REVIEW_PROD_ID} = d.${DatabaseHelper.COL_DETAIL_PROD_ID}
                            AND r.${DatabaseHelper.COL_REVIEW_USER_ID} = ?)
            FROM ${DatabaseHelper.TABLE_ORDER_DETAILS} d
            INNER JOIN ${DatabaseHelper.TABLE_PRODUCTS} p ON p.${DatabaseHelper.COL_PROD_ID} = d.${DatabaseHelper.COL_DETAIL_PROD_ID}
            WHERE d.${DatabaseHelper.COL_DETAIL_ORDER_ID} = ?
            GROUP BY d.${DatabaseHelper.COL_DETAIL_PROD_ID}
            ORDER BY MIN(d.${DatabaseHelper.COL_DETAIL_ID})
        """.trimIndent()
        dbHelper.readableDatabase.rawQuery(sql, arrayOf(userId.toString(), orderId.toString())).use { c ->
            while (c.moveToNext()) {
                list.add(
                    ReviewableItem(
                        orderId = orderId,
                        productId = c.getLong(0),
                        productName = c.getString(1),
                        imageName = c.getString(2) ?: "",
                        alreadyReviewed = c.getInt(3) == 1
                    )
                )
            }
        }
        return list
    }

    /** Most recent delivered order of [userId] containing [productId] that has not been reviewed yet. */
    fun findReviewableOrderForProduct(userId: Long, productId: Long): Long? {
        val sql = """
            SELECT o.${DatabaseHelper.COL_ORDER_ID} FROM ${DatabaseHelper.TABLE_ORDERS} o
            INNER JOIN ${DatabaseHelper.TABLE_ORDER_DETAILS} d ON d.${DatabaseHelper.COL_DETAIL_ORDER_ID} = o.${DatabaseHelper.COL_ORDER_ID}
            WHERE o.${DatabaseHelper.COL_ORDER_USER_ID} = ? AND o.${DatabaseHelper.COL_ORDER_STATUS} = ? AND d.${DatabaseHelper.COL_DETAIL_PROD_ID} = ?
              AND NOT EXISTS (SELECT 1 FROM ${DatabaseHelper.TABLE_REVIEWS} r
                              WHERE r.${DatabaseHelper.COL_REVIEW_ORDER_ID} = o.${DatabaseHelper.COL_ORDER_ID}
                                AND r.${DatabaseHelper.COL_REVIEW_PROD_ID} = d.${DatabaseHelper.COL_DETAIL_PROD_ID}
                                AND r.${DatabaseHelper.COL_REVIEW_USER_ID} = o.${DatabaseHelper.COL_ORDER_USER_ID})
            ORDER BY o.${DatabaseHelper.COL_ORDER_ID} DESC LIMIT 1
        """.trimIndent()
        dbHelper.readableDatabase.rawQuery(
            sql, arrayOf(userId.toString(), DatabaseHelper.STATUS_DELIVERED, productId.toString())
        ).use { c -> return if (c.moveToFirst()) c.getLong(0) else null }
    }

    fun getReviewsForProduct(productId: Long, onlyVisible: Boolean = true): List<ProductReview> {
        val visibility = if (onlyVisible) " AND r.${DatabaseHelper.COL_REVIEW_IS_VISIBLE} = 1" else ""
        return queryReviews(
            "WHERE r.${DatabaseHelper.COL_REVIEW_PROD_ID} = ?$visibility",
            arrayOf(productId.toString())
        )
    }

    fun getAllReviews(): List<ProductReview> = queryReviews("", null)

    /** Admins may only hide/show a review; rating and comment are never edited. */
    fun toggleVisibility(reviewId: Long, isVisible: Boolean): Boolean {
        val cv = ContentValues().apply { put(DatabaseHelper.COL_REVIEW_IS_VISIBLE, if (isVisible) 1 else 0) }
        return dbHelper.writableDatabase.update(
            DatabaseHelper.TABLE_REVIEWS, cv,
            "${DatabaseHelper.COL_REVIEW_ID} = ?",
            arrayOf(reviewId.toString())
        ) > 0
    }

    fun getRatingSummaryForProduct(productId: Long): RatingSummary {
        val starCounts = mutableMapOf(1 to 0, 2 to 0, 3 to 0, 4 to 0, 5 to 0)
        dbHelper.readableDatabase.rawQuery(
            "SELECT ${DatabaseHelper.COL_REVIEW_RATING}, COUNT(*) FROM ${DatabaseHelper.TABLE_REVIEWS} " +
                "WHERE ${DatabaseHelper.COL_REVIEW_PROD_ID} = ? AND ${DatabaseHelper.COL_REVIEW_IS_VISIBLE} = 1 " +
                "GROUP BY ${DatabaseHelper.COL_REVIEW_RATING}",
            arrayOf(productId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val star = c.getInt(0)
                if (star in 1..5) starCounts[star] = c.getInt(1)
            }
        }
        val total = starCounts.values.sum()
        val sum = starCounts.entries.sumOf { it.key * it.value }
        return RatingSummary(if (total > 0) sum.toDouble() / total else 0.0, total, starCounts)
    }

    /** Average rating and count of visible reviews for many products in one query. */
    fun getRatingsForProducts(): Map<Long, Pair<Double, Int>> {
        val map = mutableMapOf<Long, Pair<Double, Int>>()
        dbHelper.readableDatabase.rawQuery(
            "SELECT ${DatabaseHelper.COL_REVIEW_PROD_ID}, AVG(${DatabaseHelper.COL_REVIEW_RATING}), COUNT(*) FROM ${DatabaseHelper.TABLE_REVIEWS} " +
                "WHERE ${DatabaseHelper.COL_REVIEW_IS_VISIBLE} = 1 GROUP BY ${DatabaseHelper.COL_REVIEW_PROD_ID}",
            null
        ).use { c -> while (c.moveToNext()) map[c.getLong(0)] = c.getDouble(1) to c.getInt(2) }
        return map
    }

    fun getAverageRating(productId: Long): Pair<Double, Int> {
        val summary = getRatingSummaryForProduct(productId)
        return Pair(summary.averageRating, summary.totalReviews)
    }

    private fun queryReviews(where: String, args: Array<String>?): List<ProductReview> {
        val list = mutableListOf<ProductReview>()
        val sql = "SELECT r.*, p.${DatabaseHelper.COL_PROD_NAME} AS product_name FROM ${DatabaseHelper.TABLE_REVIEWS} r " +
            "LEFT JOIN ${DatabaseHelper.TABLE_PRODUCTS} p ON p.${DatabaseHelper.COL_PROD_ID} = r.${DatabaseHelper.COL_REVIEW_PROD_ID} " +
            "$where ORDER BY r.${DatabaseHelper.COL_REVIEW_CREATED_AT} DESC, r.${DatabaseHelper.COL_REVIEW_ID} DESC"
        dbHelper.readableDatabase.rawQuery(sql, args).use { c -> while (c.moveToNext()) list.add(cursorToReview(c)) }
        return list
    }

    private fun cursorToReview(cursor: Cursor): ProductReview {
        val nameCol = cursor.getColumnIndex("product_name")
        return ProductReview(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_REVIEW_ID)),
            productId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_REVIEW_PROD_ID)),
            orderId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_REVIEW_ORDER_ID)),
            userId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_REVIEW_USER_ID)),
            userName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_REVIEW_USER_NAME)),
            rating = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_REVIEW_RATING)),
            comment = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_REVIEW_COMMENT)) ?: "",
            isVisible = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_REVIEW_IS_VISIBLE)) == 1,
            createdAt = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_REVIEW_CREATED_AT)),
            productName = if (nameCol >= 0 && !cursor.isNull(nameCol)) cursor.getString(nameCol) else "(Sản phẩm đã xóa)"
        )
    }
}
