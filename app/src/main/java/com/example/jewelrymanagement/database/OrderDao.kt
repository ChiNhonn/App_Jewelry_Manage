package com.example.jewelrymanagement.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.jewelrymanagement.database.DatabaseHelper.Companion.STATUS_CANCELLED
import com.example.jewelrymanagement.database.DatabaseHelper.Companion.STATUS_CONFIRMED
import com.example.jewelrymanagement.database.DatabaseHelper.Companion.STATUS_DELIVERED
import com.example.jewelrymanagement.database.DatabaseHelper.Companion.STATUS_PENDING
import com.example.jewelrymanagement.database.DatabaseHelper.Companion.STATUS_SHIPPING
import com.example.jewelrymanagement.models.NotificationItem
import com.example.jewelrymanagement.models.Order
import com.example.jewelrymanagement.models.OrderDetail
import com.example.jewelrymanagement.repositories.PricingRepository
import com.example.jewelrymanagement.utils.CurrencyUtils
import com.example.jewelrymanagement.utils.DateUtils
import com.example.jewelrymanagement.utils.NotificationHelper

data class PlaceOrderResult(
    val orderId: Long,
    val message: String,
    /** True when prices/discounts changed since the customer saw the checkout screen. */
    val priceChanged: Boolean = false
) {
    val success: Boolean get() = orderId > 0
}

class OrderDao(private val context: Context) {
    private val dbHelper = DatabaseHelper.getInstance(context)
    private val pricing = PricingRepository(context)
    private val couponDao = CouponDao(context)
    private val notificationDao = NotificationDao(context)

    companion object {
        const val PAYMENT_TRANSFER = "Mô phỏng Chuyển khoản"
        const val PAYMENT_COD = "COD"
        const val PAYMENT_VNPAY = "VNPay"
        const val PAYMENT_STATUS_PAID = "Đã thanh toán"
        const val PAYMENT_STATUS_UNPAID = "Chưa thanh toán"

        /** Allowed admin transitions. Delivered and cancelled orders are final. */
        private val ALLOWED_TRANSITIONS = mapOf(
            STATUS_PENDING to setOf(STATUS_CONFIRMED, STATUS_SHIPPING, STATUS_DELIVERED, STATUS_CANCELLED),
            STATUS_CONFIRMED to setOf(STATUS_SHIPPING, STATUS_DELIVERED, STATUS_CANCELLED),
            STATUS_SHIPPING to setOf(STATUS_DELIVERED, STATUS_CANCELLED),
            STATUS_DELIVERED to emptySet(),
            STATUS_CANCELLED to emptySet()
        )

        fun allowedNextStatuses(current: String): Set<String> = ALLOWED_TRANSITIONS[current].orEmpty()
    }

    /**
     * Places the user's current cart as one order inside a single SQLite transaction:
     * re-reads the cart, re-prices it (promotions + coupon) from SQLite, checks stock,
     * consumes one coupon use atomically, writes order + lines with discount snapshots,
     * deducts stock and clears the cart. Any failure rolls everything back.
     *
     * [expectedFinalAmount] is the total shown to the customer; if the recomputed total differs
     * (e.g. a promotion just ended) the order is NOT created and [PlaceOrderResult.priceChanged] is set.
     */
    fun placeOrder(
        userId: Long,
        receiverName: String,
        phone: String,
        shippingAddress: String,
        paymentMethod: String,
        couponCode: String,
        expectedFinalAmount: Double
    ): PlaceOrderResult {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val cartItems = mutableListOf<Pair<Long, Int>>()
            db.rawQuery(
                "SELECT ${DatabaseHelper.COL_CART_PROD_ID}, ${DatabaseHelper.COL_CART_QUANTITY} FROM ${DatabaseHelper.TABLE_CART} WHERE ${DatabaseHelper.COL_CART_USER_ID} = ? ORDER BY ${DatabaseHelper.COL_CART_ID}",
                arrayOf(userId.toString())
            ).use { c -> while (c.moveToNext()) cartItems.add(c.getLong(0) to c.getInt(1)) }

            if (cartItems.isEmpty()) return PlaceOrderResult(-1, "Giỏ hàng rỗng, không thể đặt hàng!")

            val quote = pricing.quote(cartItems, couponCode.ifBlank { null }, db)
            if (quote.missingProductIds.isNotEmpty()) {
                return PlaceOrderResult(-1, "Một số sản phẩm trong giỏ không còn tồn tại. Vui lòng kiểm tra lại giỏ hàng.")
            }
            if (couponCode.isNotBlank() && quote.coupon == null) {
                return PlaceOrderResult(-1, quote.couponError ?: "Mã giảm giá không còn hợp lệ.", priceChanged = true)
            }
            for (line in quote.lines) {
                if (line.availableStock < line.quantity) {
                    return PlaceOrderResult(-1, "Sản phẩm '${line.productName}' không đủ hàng (còn ${line.availableStock}, yêu cầu ${line.quantity})!")
                }
            }
            if (Math.abs(quote.finalAmount - expectedFinalAmount) >= 1.0) {
                return PlaceOrderResult(
                    -1,
                    "Giá hoặc ưu đãi vừa thay đổi. Tổng thanh toán mới là ${CurrencyUtils.formatVnd(quote.finalAmount)}. Vui lòng kiểm tra lại trước khi đặt hàng.",
                    priceChanged = true
                )
            }

            val coupon = quote.coupon
            if (coupon != null && !couponDao.consumeUse(db, coupon.id)) {
                return PlaceOrderResult(-1, "Mã giảm giá '${coupon.code}' vừa hết lượt sử dụng hoặc đã bị tạm ngừng.", priceChanged = true)
            }

            val orderId = db.insertOrThrow(DatabaseHelper.TABLE_ORDERS, null, ContentValues().apply {
                put(DatabaseHelper.COL_ORDER_USER_ID, userId)
                put(DatabaseHelper.COL_ORDER_RECEIVER_NAME, receiverName.trim())
                put(DatabaseHelper.COL_ORDER_PHONE, phone.trim())
                put(DatabaseHelper.COL_ORDER_SHIPPING_ADDRESS, shippingAddress.trim())
                put(DatabaseHelper.COL_ORDER_TOTAL_AMOUNT, quote.grossAmount)
                put(DatabaseHelper.COL_ORDER_PROMO_DISCOUNT, quote.promotionDiscount)
                put(DatabaseHelper.COL_ORDER_COUPON_DISCOUNT, quote.couponDiscount)
                put(DatabaseHelper.COL_ORDER_DISCOUNT_AMOUNT, quote.totalDiscount)
                put(DatabaseHelper.COL_ORDER_COUPON_CODE, coupon?.code ?: "")
                put(DatabaseHelper.COL_ORDER_FINAL_AMOUNT, quote.finalAmount)
                put(DatabaseHelper.COL_ORDER_PAYMENT_METHOD, paymentMethod)
                put(DatabaseHelper.COL_ORDER_PAYMENT_STATUS, if (paymentMethod == PAYMENT_TRANSFER) PAYMENT_STATUS_PAID else PAYMENT_STATUS_UNPAID)
                put(DatabaseHelper.COL_ORDER_STATUS, STATUS_PENDING)
                put(DatabaseHelper.COL_ORDER_CREATED_AT, DateUtils.now())
            })

            for (line in quote.lines) {
                db.insertOrThrow(DatabaseHelper.TABLE_ORDER_DETAILS, null, ContentValues().apply {
                    put(DatabaseHelper.COL_DETAIL_ORDER_ID, orderId)
                    put(DatabaseHelper.COL_DETAIL_PROD_ID, line.productId)
                    put(DatabaseHelper.COL_DETAIL_PROD_NAME, line.productName)
                    put(DatabaseHelper.COL_DETAIL_QUANTITY, line.quantity)
                    put(DatabaseHelper.COL_DETAIL_ORIGINAL_PRICE, line.price.originalPrice)
                    put(DatabaseHelper.COL_DETAIL_UNIT_PRICE, line.price.finalPrice)
                    put(DatabaseHelper.COL_DETAIL_PROMO_DISCOUNT, line.promotionDiscount)
                    put(DatabaseHelper.COL_DETAIL_PROMO_NAME, line.price.promotion?.name)
                    put(DatabaseHelper.COL_DETAIL_SUBTOTAL, line.subtotal)
                })
                db.execSQL(
                    "UPDATE ${DatabaseHelper.TABLE_PRODUCTS} SET ${DatabaseHelper.COL_PROD_QUANTITY} = ${DatabaseHelper.COL_PROD_QUANTITY} - ? WHERE ${DatabaseHelper.COL_PROD_ID} = ?",
                    arrayOf<Any>(line.quantity, line.productId)
                )
            }

            db.delete(DatabaseHelper.TABLE_CART, "${DatabaseHelper.COL_CART_USER_ID} = ?", arrayOf(userId.toString()))

            db.setTransactionSuccessful()
            return PlaceOrderResult(orderId, "Đặt hàng thành công!")
        } catch (e: Exception) {
            return PlaceOrderResult(-1, "Lỗi trong quá trình xử lý đơn hàng: ${e.message}")
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Cancels an order, restores stock and returns the coupon use, all in one transaction.
     * Customers may only cancel orders still waiting for confirmation; delivered orders cannot be cancelled.
     */
    fun cancelOrder(orderId: Long, isCustomer: Boolean = false, requesterUserId: Long = -1): Pair<Boolean, String> {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            var currentStatus = ""
            var ownerId = -1L
            var couponCode = ""
            db.rawQuery(
                "SELECT ${DatabaseHelper.COL_ORDER_STATUS}, ${DatabaseHelper.COL_ORDER_USER_ID}, ${DatabaseHelper.COL_ORDER_COUPON_CODE} FROM ${DatabaseHelper.TABLE_ORDERS} WHERE ${DatabaseHelper.COL_ORDER_ID} = ?",
                arrayOf(orderId.toString())
            ).use { c ->
                if (!c.moveToFirst()) return Pair(false, "Không tìm thấy đơn hàng!")
                currentStatus = c.getString(0)
                ownerId = c.getLong(1)
                couponCode = c.getString(2) ?: ""
            }

            if (isCustomer && ownerId != requesterUserId) return Pair(false, "Bạn không có quyền hủy đơn hàng này!")
            if (currentStatus == STATUS_CANCELLED) return Pair(false, "Đơn hàng này đã bị hủy trước đó!")
            if (currentStatus == STATUS_DELIVERED) return Pair(false, "Đơn hàng đã giao thành công, không thể hủy!")
            if (isCustomer && currentStatus != STATUS_PENDING) {
                return Pair(false, "Đơn hàng đang ở trạng thái '$currentStatus', quý khách vui lòng liên hệ cửa hàng để hủy!")
            }

            db.rawQuery(
                "SELECT ${DatabaseHelper.COL_DETAIL_PROD_ID}, ${DatabaseHelper.COL_DETAIL_QUANTITY} FROM ${DatabaseHelper.TABLE_ORDER_DETAILS} WHERE ${DatabaseHelper.COL_DETAIL_ORDER_ID} = ? AND ${DatabaseHelper.COL_DETAIL_PROD_ID} IS NOT NULL",
                arrayOf(orderId.toString())
            ).use { c ->
                while (c.moveToNext()) {
                    db.execSQL(
                        "UPDATE ${DatabaseHelper.TABLE_PRODUCTS} SET ${DatabaseHelper.COL_PROD_QUANTITY} = ${DatabaseHelper.COL_PROD_QUANTITY} + ? WHERE ${DatabaseHelper.COL_PROD_ID} = ?",
                        arrayOf<Any>(c.getInt(1), c.getLong(0))
                    )
                }
            }
            if (couponCode.isNotBlank()) couponDao.releaseUse(db, couponCode)

            db.update(
                DatabaseHelper.TABLE_ORDERS,
                ContentValues().apply { put(DatabaseHelper.COL_ORDER_STATUS, STATUS_CANCELLED) },
                "${DatabaseHelper.COL_ORDER_ID} = ?",
                arrayOf(orderId.toString())
            )
            db.setTransactionSuccessful()
            return Pair(true, "Hủy đơn hàng thành công và đã hoàn lại tồn kho!")
        } catch (e: Exception) {
            return Pair(false, "Lỗi khi hủy đơn hàng: ${e.message}")
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Admin status update. When the order becomes "Đã giao hàng" for the first time, the payment
     * is marked paid and exactly one delivery notification is stored for the order's owner
     * (not for the admin) in the same transaction; the Android notification is posted after commit.
     */
    fun updateOrderStatus(orderId: Long, newStatus: String): Pair<Boolean, String> {
        val order = getOrderById(orderId) ?: return Pair(false, "Không tìm thấy đơn hàng!")
        if (order.orderStatus == newStatus) {
            return Pair(false, "Đơn hàng đã ở trạng thái '$newStatus', không có thay đổi.")
        }
        if (newStatus !in allowedNextStatuses(order.orderStatus)) {
            return Pair(false, "Không thể chuyển đơn hàng từ '${order.orderStatus}' sang '$newStatus'.")
        }
        if (newStatus == STATUS_CANCELLED) return cancelOrder(orderId, isCustomer = false)

        val db = dbHelper.writableDatabase
        var deliveredNotification: NotificationItem? = null
        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put(DatabaseHelper.COL_ORDER_STATUS, newStatus)
                if (newStatus == STATUS_DELIVERED) put(DatabaseHelper.COL_ORDER_PAYMENT_STATUS, "Đã thanh toán")
            }
            val affected = db.update(
                DatabaseHelper.TABLE_ORDERS, cv,
                "${DatabaseHelper.COL_ORDER_ID} = ? AND ${DatabaseHelper.COL_ORDER_STATUS} = ?",
                arrayOf(orderId.toString(), order.orderStatus)
            )
            if (affected != 1) return Pair(false, "Đơn hàng vừa được cập nhật bởi thao tác khác, vui lòng tải lại.")

            if (newStatus == STATUS_DELIVERED && order.userId != UserDao(context).getGuestUserId()) {
                val firstProductId = getOrderDetails(orderId).firstOrNull { it.productId > 0 }?.productId
                val item = NotificationItem(
                    userId = order.userId,
                    title = "Đơn hàng #$orderId đã giao thành công!",
                    content = "Đơn hàng của bạn đã được giao thành công. Cảm ơn bạn đã mua sắm tại JewelryManagement! Bạn có muốn đánh giá sản phẩm vừa mua không?",
                    type = NotificationItem.TYPE_ORDER_DELIVERED,
                    orderId = orderId,
                    productId = firstProductId,
                    actionType = NotificationItem.ACTION_REVIEW_ORDER
                )
                if (notificationDao.insertIfAbsent(db, item) > 0) deliveredNotification = item
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            return Pair(false, "Lỗi cập nhật trạng thái: ${e.message}")
        } finally {
            db.endTransaction()
        }

        deliveredNotification?.let {
            NotificationHelper.showOrderDeliveredNotification(context, orderId, it.userId, it.title, it.content)
        }
        val suffix = if (deliveredNotification != null) " Đã gửi thông báo cho khách hàng." else ""
        return Pair(true, "Cập nhật trạng thái đơn hàng thành '$newStatus' thành công!$suffix")
    }

    /** Records a verified online payment. Returns false if the order is cancelled or does not exist. */
    fun markOrderPaid(orderId: Long): Boolean {
        val cv = ContentValues().apply {
            put(DatabaseHelper.COL_ORDER_PAYMENT_STATUS, PAYMENT_STATUS_PAID)
            put(DatabaseHelper.COL_ORDER_PAYMENT_METHOD, PAYMENT_VNPAY)
        }
        return dbHelper.writableDatabase.update(
            DatabaseHelper.TABLE_ORDERS, cv,
            "${DatabaseHelper.COL_ORDER_ID} = ? AND ${DatabaseHelper.COL_ORDER_STATUS} != ?",
            arrayOf(orderId.toString(), STATUS_CANCELLED)
        ) > 0
    }

    /**
     * Undoes an unpaid VNPay order that the customer chose not to place.
     * Stock, coupon use, cart lines and the order row change together; any failure rolls all of them back.
     */
    fun revertUnpaidVnpayOrder(orderId: Long, userId: Long): Boolean {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            var ownerId = -1L
            var status = ""
            var paymentStatus = ""
            var paymentMethod = ""
            var couponCode = ""
            db.rawQuery(
                "SELECT ${DatabaseHelper.COL_ORDER_USER_ID}, ${DatabaseHelper.COL_ORDER_STATUS}, ${DatabaseHelper.COL_ORDER_PAYMENT_STATUS}, ${DatabaseHelper.COL_ORDER_PAYMENT_METHOD}, ${DatabaseHelper.COL_ORDER_COUPON_CODE} FROM ${DatabaseHelper.TABLE_ORDERS} WHERE ${DatabaseHelper.COL_ORDER_ID} = ?",
                arrayOf(orderId.toString())
            ).use { cursor ->
                if (!cursor.moveToFirst()) return false
                ownerId = cursor.getLong(0)
                status = cursor.getString(1)
                paymentStatus = cursor.getString(2)
                paymentMethod = cursor.getString(3)
                couponCode = cursor.getString(4) ?: ""
            }
            val canRevert = ownerId == userId &&
                status == STATUS_PENDING &&
                paymentStatus == PAYMENT_STATUS_UNPAID &&
                paymentMethod == PAYMENT_VNPAY
            if (!canRevert) return false

            val lines = mutableListOf<Pair<Long, Int>>()
            db.rawQuery(
                "SELECT ${DatabaseHelper.COL_DETAIL_PROD_ID}, ${DatabaseHelper.COL_DETAIL_QUANTITY} FROM ${DatabaseHelper.TABLE_ORDER_DETAILS} WHERE ${DatabaseHelper.COL_DETAIL_ORDER_ID} = ? AND ${DatabaseHelper.COL_DETAIL_PROD_ID} IS NOT NULL",
                arrayOf(orderId.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) lines.add(cursor.getLong(0) to cursor.getInt(1))
            }

            for ((productId, quantity) in lines) {
                db.execSQL(
                    "UPDATE ${DatabaseHelper.TABLE_PRODUCTS} SET ${DatabaseHelper.COL_PROD_QUANTITY} = ${DatabaseHelper.COL_PROD_QUANTITY} + ? WHERE ${DatabaseHelper.COL_PROD_ID} = ?",
                    arrayOf<Any>(quantity, productId)
                )
                db.execSQL(
                    """
                    INSERT INTO ${DatabaseHelper.TABLE_CART}
                        (${DatabaseHelper.COL_CART_USER_ID}, ${DatabaseHelper.COL_CART_PROD_ID}, ${DatabaseHelper.COL_CART_QUANTITY})
                    VALUES (?, ?, ?)
                    ON CONFLICT(${DatabaseHelper.COL_CART_USER_ID}, ${DatabaseHelper.COL_CART_PROD_ID})
                    DO UPDATE SET ${DatabaseHelper.COL_CART_QUANTITY} = ${DatabaseHelper.COL_CART_QUANTITY} + excluded.${DatabaseHelper.COL_CART_QUANTITY}
                    """.trimIndent(),
                    arrayOf<Any>(userId, productId, quantity)
                )
            }
            if (couponCode.isNotBlank()) couponDao.releaseUse(db, couponCode)

            db.delete(
                DatabaseHelper.TABLE_ORDER_DETAILS,
                "${DatabaseHelper.COL_DETAIL_ORDER_ID} = ?",
                arrayOf(orderId.toString())
            )
            val removed = db.delete(
                DatabaseHelper.TABLE_ORDERS,
                "${DatabaseHelper.COL_ORDER_ID} = ? AND ${DatabaseHelper.COL_ORDER_STATUS} = ? AND ${DatabaseHelper.COL_ORDER_PAYMENT_STATUS} = ? AND ${DatabaseHelper.COL_ORDER_PAYMENT_METHOD} = ?",
                arrayOf(orderId.toString(), STATUS_PENDING, PAYMENT_STATUS_UNPAID, PAYMENT_VNPAY)
            )
            if (removed != 1) return false

            db.setTransactionSuccessful()
            return true
        } catch (_: Exception) {
            return false
        } finally {
            db.endTransaction()
        }
    }

    /** Lets an unpaid online order fall back to cash on delivery. */
    fun switchToCod(orderId: Long): Boolean {
        val cv = ContentValues().apply { put(DatabaseHelper.COL_ORDER_PAYMENT_METHOD, PAYMENT_COD) }
        return dbHelper.writableDatabase.update(
            DatabaseHelper.TABLE_ORDERS, cv,
            "${DatabaseHelper.COL_ORDER_ID} = ? AND ${DatabaseHelper.COL_ORDER_PAYMENT_STATUS} != ?",
            arrayOf(orderId.toString(), PAYMENT_STATUS_PAID)
        ) > 0
    }

    fun getOrdersByUser(userId: Long): List<Order> = queryOrders(
        "SELECT * FROM ${DatabaseHelper.TABLE_ORDERS} WHERE ${DatabaseHelper.COL_ORDER_USER_ID} = ? ORDER BY ${DatabaseHelper.COL_ORDER_ID} DESC",
        arrayOf(userId.toString())
    )

    fun getAllOrders(filterStatus: String = "ALL"): List<Order> =
        if (filterStatus == "ALL") {
            queryOrders("SELECT * FROM ${DatabaseHelper.TABLE_ORDERS} ORDER BY ${DatabaseHelper.COL_ORDER_ID} DESC", null)
        } else {
            queryOrders(
                "SELECT * FROM ${DatabaseHelper.TABLE_ORDERS} WHERE ${DatabaseHelper.COL_ORDER_STATUS} = ? ORDER BY ${DatabaseHelper.COL_ORDER_ID} DESC",
                arrayOf(filterStatus)
            )
        }

    fun getOrderById(orderId: Long): Order? = queryOrders(
        "SELECT * FROM ${DatabaseHelper.TABLE_ORDERS} WHERE ${DatabaseHelper.COL_ORDER_ID} = ?",
        arrayOf(orderId.toString())
    ).firstOrNull()

    fun getOrderDetails(orderId: Long): List<OrderDetail> {
        val list = mutableListOf<OrderDetail>()
        dbHelper.readableDatabase.rawQuery(
            "SELECT * FROM ${DatabaseHelper.TABLE_ORDER_DETAILS} WHERE ${DatabaseHelper.COL_DETAIL_ORDER_ID} = ? ORDER BY ${DatabaseHelper.COL_DETAIL_ID}",
            arrayOf(orderId.toString())
        ).use { c ->
            val prodCol = c.getColumnIndexOrThrow(DatabaseHelper.COL_DETAIL_PROD_ID)
            val origCol = c.getColumnIndex(DatabaseHelper.COL_DETAIL_ORIGINAL_PRICE)
            val promoDiscCol = c.getColumnIndex(DatabaseHelper.COL_DETAIL_PROMO_DISCOUNT)
            val promoNameCol = c.getColumnIndex(DatabaseHelper.COL_DETAIL_PROMO_NAME)
            while (c.moveToNext()) {
                val unitPrice = c.getDouble(c.getColumnIndexOrThrow(DatabaseHelper.COL_DETAIL_UNIT_PRICE))
                list.add(
                    OrderDetail(
                        id = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_DETAIL_ID)),
                        orderId = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COL_DETAIL_ORDER_ID)),
                        productId = if (c.isNull(prodCol)) 0 else c.getLong(prodCol),
                        productName = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COL_DETAIL_PROD_NAME)),
                        quantity = c.getInt(c.getColumnIndexOrThrow(DatabaseHelper.COL_DETAIL_QUANTITY)),
                        unitPrice = unitPrice,
                        subtotal = c.getDouble(c.getColumnIndexOrThrow(DatabaseHelper.COL_DETAIL_SUBTOTAL)),
                        originalUnitPrice = if (origCol >= 0 && !c.isNull(origCol)) c.getDouble(origCol) else unitPrice,
                        promotionDiscount = if (promoDiscCol >= 0 && !c.isNull(promoDiscCol)) c.getDouble(promoDiscCol) else 0.0,
                        promotionName = if (promoNameCol >= 0 && !c.isNull(promoNameCol)) c.getString(promoNameCol) else ""
                    )
                )
            }
        }
        return list
    }

    /** Orders whose created_at falls inside [startDay, endDay] (yyyy-MM-dd), optionally filtered by status. */
    fun getOrdersByDateRange(startDay: String, endDay: String, statusFilter: String = "ALL"): List<Order> {
        val args = mutableListOf("$startDay 00:00:00", "$endDay 23:59:59")
        var sql = "SELECT * FROM ${DatabaseHelper.TABLE_ORDERS} WHERE ${DatabaseHelper.COL_ORDER_CREATED_AT} >= ? AND ${DatabaseHelper.COL_ORDER_CREATED_AT} <= ?"
        if (statusFilter != "ALL") {
            sql += " AND ${DatabaseHelper.COL_ORDER_STATUS} = ?"
            args.add(statusFilter)
        }
        sql += " ORDER BY ${DatabaseHelper.COL_ORDER_CREATED_AT} ASC, ${DatabaseHelper.COL_ORDER_ID} ASC"
        return queryOrders(sql, args.toTypedArray())
    }

    /** Net revenue: only delivered orders count; cancelled orders never contribute. */
    fun getTotalRevenue(): Double {
        dbHelper.readableDatabase.rawQuery(
            "SELECT SUM(CASE WHEN ${DatabaseHelper.COL_ORDER_FINAL_AMOUNT} > 0 THEN ${DatabaseHelper.COL_ORDER_FINAL_AMOUNT} ELSE ${DatabaseHelper.COL_ORDER_TOTAL_AMOUNT} - ${DatabaseHelper.COL_ORDER_DISCOUNT_AMOUNT} END) " +
                "FROM ${DatabaseHelper.TABLE_ORDERS} WHERE ${DatabaseHelper.COL_ORDER_STATUS} = ?",
            arrayOf(STATUS_DELIVERED)
        ).use { c -> return if (c.moveToFirst()) c.getDouble(0) else 0.0 }
    }

    fun getOrderCount(): Int {
        dbHelper.readableDatabase.rawQuery("SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_ORDERS}", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun getOrderCountByStatus(status: String): Int {
        dbHelper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_ORDERS} WHERE ${DatabaseHelper.COL_ORDER_STATUS} = ?",
            arrayOf(status)
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    private fun queryOrders(sql: String, args: Array<String>?): List<Order> {
        val list = mutableListOf<Order>()
        dbHelper.readableDatabase.rawQuery(sql, args).use { c -> while (c.moveToNext()) list.add(cursorToOrder(c)) }
        return list
    }

    private fun cursorToOrder(cursor: Cursor): Order {
        fun optDouble(col: String): Double {
            val i = cursor.getColumnIndex(col)
            return if (i >= 0 && !cursor.isNull(i)) cursor.getDouble(i) else 0.0
        }
        val totalAmount = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ORDER_TOTAL_AMOUNT))
        val discountAmount = optDouble(DatabaseHelper.COL_ORDER_DISCOUNT_AMOUNT)
        val storedFinal = optDouble(DatabaseHelper.COL_ORDER_FINAL_AMOUNT)
        val couponCol = cursor.getColumnIndex(DatabaseHelper.COL_ORDER_COUPON_CODE)

        return Order(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ORDER_ID)),
            userId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ORDER_USER_ID)),
            receiverName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ORDER_RECEIVER_NAME)),
            phone = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ORDER_PHONE)),
            shippingAddress = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ORDER_SHIPPING_ADDRESS)),
            totalAmount = totalAmount,
            discountAmount = discountAmount,
            promotionDiscount = optDouble(DatabaseHelper.COL_ORDER_PROMO_DISCOUNT),
            couponDiscount = optDouble(DatabaseHelper.COL_ORDER_COUPON_DISCOUNT),
            couponCode = if (couponCol >= 0 && !cursor.isNull(couponCol)) cursor.getString(couponCol) else "",
            finalAmount = if (storedFinal > 0) storedFinal else (totalAmount - discountAmount).coerceAtLeast(0.0),
            paymentMethod = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ORDER_PAYMENT_METHOD)),
            paymentStatus = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ORDER_PAYMENT_STATUS)),
            orderStatus = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ORDER_STATUS)),
            createdAt = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ORDER_CREATED_AT))
        )
    }
}
