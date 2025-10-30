package com.example.clipmon2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationUtils {
    const val CHANNEL_ID_MONITOR = "clipmon_monitor"
    const val CHANNEL_NAME_MONITOR = "剪贴板监控"
    const val NOTIF_ID_FOREGROUND = 1001
    const val NOTIF_ID_ALERT = 1002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(CHANNEL_ID_MONITOR, CHANNEL_NAME_MONITOR, NotificationManager.IMPORTANCE_DEFAULT)
            ch.enableLights(false)
            ch.enableVibration(false)
            ch.lightColor = Color.BLUE
            nm.createNotificationChannel(ch)
        }
    }

    fun buildForegroundNotification(context: Context): Notification {
        // 点通知返回主界面（可选）
        val pi = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        return NotificationCompat.Builder(context, CHANNEL_ID_MONITOR)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("ClipMon 正在监控")
            .setContentText("监控复制事件与输入中的敏感信息")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    fun showSensitiveNotification(context: Context, maskedText: String) {
        val nm = context.getSystemService(NotificationManager::class.java)

        val maskIntent = Intent(context, ActionReceiver::class.java)
            .setAction(ActionReceiver.ACTION_MASK)
            .putExtra(ActionReceiver.EXTRA_MASKED_TEXT, maskedText)

        val clearIntent = Intent(context, ActionReceiver::class.java)
            .setAction(ActionReceiver.ACTION_CLEAR)

        val maskPI = PendingIntent.getBroadcast(
            context, 1, maskIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val clearPI = PendingIntent.getBroadcast(
            context, 2, clearIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID_MONITOR)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("检测到可能的敏感内容")
            .setContentText("你可以选择打码复制或清空剪贴板")
            .addAction(0, "打码复制", maskPI)
            .addAction(0, "清空", clearPI)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 提升展示优先级
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIF_ID_ALERT, notif)
    }
}
