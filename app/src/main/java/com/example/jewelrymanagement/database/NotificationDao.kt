package com.example.jewelrymanagement.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.jewelrymanagement.models.NotificationItem
import com.example.jewelrymanagement.utils.DateUtils

class NotificationDao(context: Context) {
    private val dbHelper = DatabaseHelper.getInstance(context)

    /**
     * Inserts the notification unless one already exists for the same user, type and order.
     * Pass the caller's transaction [db] so the check and the insert are atomic.
     * Returns the new id, or -1 when it already existed.
     */
    fun insertIfAbsent(db: SQLiteDatabase, item: NotificationItem): Long {
        if (item.orderId != null && exists(db, item.userId, item.type, item.orderId)) return -1
        return db.insertOrThrow(DatabaseHelper.TABLE_NOTIFICATIONS, null, toValues(item))
    }

    fun insert(item: NotificationItem): Long = insertIfAbsent(dbHelper.writableDatabase, item)

    fun existsForOrder(orderId: Long, userId: Long, type: String = NotificationItem.TYPE_ORDER_DELIVERED): Boolean =
        exists(dbHelper.readableDatabase, userId, type, orderId)

    fun getNotificationsForUser(userId: Long): List<NotificationItem> {
        val list = mutableListOf<NotificationItem>()
        dbHelper.readableDatabase.rawQuery(
            "SELECT * FROM ${DatabaseHelper.TABLE_NOTIFICATIONS} WHERE ${DatabaseHelper.COL_NOTIF_USER_ID} = ? ORDER BY ${DatabaseHelper.COL_NOTIF_CREATED_AT} DESC, ${DatabaseHelper.COL_NOTIF_ID} DESC",
            arrayOf(userId.toString())
        ).use { c -> while (c.moveToNext()) list.add(cursorToNotification(c)) }
        return list
    }

    fun getById(notificationId: Long): NotificationItem? {
        dbHelper.readableDatabase.rawQuery(
            "SELECT * FROM ${DatabaseHelper.TABLE_NOTIFICATIONS} WHERE ${DatabaseHelper.COL_NOTIF_ID} = ?",
            arrayOf(notificationId.toString())
        ).use { c -> return if (c.moveToFirst()) cursorToNotification(c) else null }
    }

    fun getUnreadCount(userId: Long): Int {
        dbHelper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_NOTIFICATIONS} WHERE ${DatabaseHelper.COL_NOTIF_USER_ID} = ? AND ${DatabaseHelper.COL_NOTIF_IS_READ} = 0",
            arrayOf(userId.toString())
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    /** Only the recipient can mark their own notification as read. */
    fun markAsRead(notificationId: Long, userId: Long): Boolean {
        val cv = ContentValues().apply { put(DatabaseHelper.COL_NOTIF_IS_READ, 1) }
        return dbHelper.writableDatabase.update(
            DatabaseHelper.TABLE_NOTIFICATIONS, cv,
            "${DatabaseHelper.COL_NOTIF_ID} = ? AND ${DatabaseHelper.COL_NOTIF_USER_ID} = ?",
            arrayOf(notificationId.toString(), userId.toString())
        ) > 0
    }

    fun markAllAsRead(userId: Long): Boolean {
        val cv = ContentValues().apply { put(DatabaseHelper.COL_NOTIF_IS_READ, 1) }
        return dbHelper.writableDatabase.update(
            DatabaseHelper.TABLE_NOTIFICATIONS, cv,
            "${DatabaseHelper.COL_NOTIF_USER_ID} = ? AND ${DatabaseHelper.COL_NOTIF_IS_READ} = 0",
            arrayOf(userId.toString())
        ) > 0
    }

    fun delete(notificationId: Long, userId: Long): Boolean =
        dbHelper.writableDatabase.delete(
            DatabaseHelper.TABLE_NOTIFICATIONS,
            "${DatabaseHelper.COL_NOTIF_ID} = ? AND ${DatabaseHelper.COL_NOTIF_USER_ID} = ?",
            arrayOf(notificationId.toString(), userId.toString())
        ) > 0

    private fun exists(db: SQLiteDatabase, userId: Long, type: String, orderId: Long): Boolean {
        db.rawQuery(
            "SELECT 1 FROM ${DatabaseHelper.TABLE_NOTIFICATIONS} WHERE ${DatabaseHelper.COL_NOTIF_USER_ID} = ? AND ${DatabaseHelper.COL_NOTIF_TYPE} = ? AND ${DatabaseHelper.COL_NOTIF_ORDER_ID} = ? LIMIT 1",
            arrayOf(userId.toString(), type, orderId.toString())
        ).use { c -> return c.moveToFirst() }
    }

    private fun toValues(item: NotificationItem) = ContentValues().apply {
        put(DatabaseHelper.COL_NOTIF_USER_ID, item.userId)
        put(DatabaseHelper.COL_NOTIF_TITLE, item.title.trim())
        put(DatabaseHelper.COL_NOTIF_CONTENT, item.content.trim())
        put(DatabaseHelper.COL_NOTIF_TYPE, item.type)
        if (item.orderId != null) put(DatabaseHelper.COL_NOTIF_ORDER_ID, item.orderId) else putNull(DatabaseHelper.COL_NOTIF_ORDER_ID)
        if (item.productId != null) put(DatabaseHelper.COL_NOTIF_PROD_ID, item.productId) else putNull(DatabaseHelper.COL_NOTIF_PROD_ID)
        put(DatabaseHelper.COL_NOTIF_ACTION, item.actionType)
        put(DatabaseHelper.COL_NOTIF_IS_READ, if (item.isRead) 1 else 0)
        put(DatabaseHelper.COL_NOTIF_CREATED_AT, item.createdAt.ifBlank { DateUtils.now() })
    }

    private fun cursorToNotification(cursor: Cursor): NotificationItem {
        val orderCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_NOTIF_ORDER_ID)
        val prodCol = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_NOTIF_PROD_ID)
        val actionCol = cursor.getColumnIndex(DatabaseHelper.COL_NOTIF_ACTION)
        return NotificationItem(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_NOTIF_ID)),
            userId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_NOTIF_USER_ID)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_NOTIF_TITLE)),
            content = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_NOTIF_CONTENT)),
            type = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_NOTIF_TYPE)),
            orderId = if (cursor.isNull(orderCol)) null else cursor.getLong(orderCol),
            productId = if (cursor.isNull(prodCol)) null else cursor.getLong(prodCol),
            actionType = if (actionCol >= 0 && !cursor.isNull(actionCol)) cursor.getString(actionCol) else NotificationItem.ACTION_OPEN_ORDER,
            isRead = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_NOTIF_IS_READ)) == 1,
            createdAt = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_NOTIF_CREATED_AT))
        )
    }
}
