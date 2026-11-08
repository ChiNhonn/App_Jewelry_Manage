package com.example.jewelrymanagement.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.jewelrymanagement.activities.NotificationActivity

/**
 * Local (on-device) notifications only. The SQLite notification row is the source of truth;
 * the system notification is a convenience shown on this device and is skipped silently when
 * POST_NOTIFICATIONS is not granted.
 */
object NotificationHelper {
    const val CHANNEL_ID = "jewelry_delivery_channel"
    private const val CHANNEL_NAME = "Thông báo đơn hàng"
    private const val CHANNEL_DESC = "Thông báo khi đơn hàng trang sức được giao thành công"

    const val EXTRA_ORDER_ID = "ORDER_ID"
    const val EXTRA_TARGET_USER_ID = "TARGET_USER_ID"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showOrderDeliveredNotification(context: Context, orderId: Long, targetUserId: Long, title: String, content: String) {
        val appContext = context.applicationContext
        createNotificationChannel(appContext)
        if (!canPostNotifications(appContext)) return

        val intent = Intent(appContext, NotificationActivity::class.java).apply {
            putExtra(EXTRA_ORDER_ID, orderId)
            putExtra(EXTRA_TARGET_USER_ID, targetUserId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val requestCode = (orderId % Int.MAX_VALUE).toInt()
        val pendingIntent = PendingIntent.getActivity(
            appContext, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(appContext).notify(requestCode, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and notify(); the in-app notification is still stored.
        }
    }
}
