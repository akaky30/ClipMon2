package com.example.clipmon2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ClipboardMonitorService : Service() {

    private lateinit var clipboard: ClipboardManager
    private val tag = "ClipMon"

    private val channelId = "clipmon_monitor"
    private val channelName = "剪贴板监控"
    private val notifIdForeground = 1001
    private val notifIdAlert = 1002

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        try {
            val clip: ClipData? = clipboard.primaryClip
            val item = clip?.getItemAt(0)
            val textStr = item?.coerceToText(this)?.toString() ?: return@OnPrimaryClipChangedListener

            val hasSensitive = SensitiveDetector.containsSensitive(textStr)
            if (hasSensitive) {
                Log.d(tag, "Clipboard sensitive: ${textStr.take(60)}")
                val masked = SensitiveDetector.maskWithin(textStr)
                showSensitiveNotification(masked)
            } else {
                Log.d(tag, "Clipboard normal: ${textStr.take(60)}")
            }
        } catch (t: Throwable) {
            Log.e(tag, "onPrimaryClipChanged error", t)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener(listener)
        Log.d(tag, "ClipboardMonitorService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(notifIdForeground, buildForegroundNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try { clipboard.removePrimaryClipChangedListener(listener) } catch (_: Throwable) {}
        NotificationManagerCompat.from(this).cancel(notifIdAlert)
        Log.d(tag, "ClipboardMonitorService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            ch.enableLights(false)
            ch.enableVibration(false)
            ch.lightColor = Color.BLUE
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("ClipMon 正在监控")
            .setContentText("监控复制事件与输入中的敏感信息")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun showSensitiveNotification(maskedText: String) {
        val nm = getSystemService(NotificationManager::class.java)

        val maskIntent = Intent(this, ActionReceiver::class.java)
            .setAction(ActionReceiver.ACTION_MASK)
            .putExtra(ActionReceiver.EXTRA_MASKED_TEXT, maskedText)

        val clearIntent = Intent(this, ActionReceiver::class.java)
            .setAction(ActionReceiver.ACTION_CLEAR)

        val maskPI = PendingIntent.getBroadcast(
            this, 1, maskIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val clearPI = PendingIntent.getBroadcast(
            this, 2, clearIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("检测到可能的敏感内容")
            .setContentText("你可以选择打码复制或清空剪贴板")
            .addAction(0, "打码复制", maskPI)
            .addAction(0, "清空", clearPI)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        nm.notify(notifIdAlert, notif)
    }
}
