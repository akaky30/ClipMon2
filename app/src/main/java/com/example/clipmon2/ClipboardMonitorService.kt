package com.example.clipmon2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.text.TextUtils
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * 复制时监控剪贴板变化：
 * - 写入“最近事件”到 EventBus（用于主界面展示与导出）
 * - 保留扩展点：检测到敏感数据后如需弹窗/通知，可在 hasSensitive 分支添加
 */
class ClipboardMonitorService : Service(), ClipboardManager.OnPrimaryClipChangedListener {

    companion object {
        private const val TAG = "ClipMon-ClipService"
        private const val CHANNEL_ID = "clipmon_foreground"
        private const val CHANNEL_NAME = "ClipMon 前台服务"
        private const val NOTIF_ID = 10001
        private const val DUP_WINDOW_MS = 1500L // 1.5 秒内相同文本不重复记一条
    }

    private lateinit var clipboard: ClipboardManager
    private var lastText: String? = null
    private var lastTs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener(this)
        startForegroundInternal()
        Log.d(TAG, "service created & startedForeground")
    }

    override fun onDestroy() {
        super.onDestroy()
        kotlin.runCatching { clipboard.removePrimaryClipChangedListener(this) }
        Log.d(TAG, "service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 粘性：被系统回收后尽量重启
        return START_STICKY
    }

    // 剪贴板变化回调
    override fun onPrimaryClipChanged() {
        val clip: ClipData = clipboard.primaryClip ?: return
        if (clip.itemCount <= 0) return

        val textStr = clip.getItemAt(0).coerceToText(this)?.toString()?.trim() ?: ""
        if (textStr.isEmpty()) return

        // 简单防抖（同内容短时间重复复制就不再写事件）
        val now = System.currentTimeMillis()
        if (TextUtils.equals(textStr, lastText) && (now - lastTs) < DUP_WINDOW_MS) return
        lastText = textStr
        lastTs = now

        // 敏感判定（对应你项目里的 SensitiveDetector）
        val containsPhone = SensitiveDetector.containsPhone11(textStr)
        val containsId18 = SensitiveDetector.containsId18(textStr)
        val containsBank19 = SensitiveDetector.containsBank19(textStr)
        val hasSensitive = containsPhone || containsId18 || containsBank19

        // === 正确写入事件：使用 EventBus.push(ClipEvent) ===
        try {
            EventBus.push(
                ClipEvent(
                    ts = now,
                    // 你在 ClipEvent.kt 里备注了 "WRITE" / "READ"，复制=WRITE 更贴切
                    type = if (hasSensitive) "WRITE(含敏感)" else "WRITE",
                    preview = textStr.take(120),
                    rawLen = textStr.length,
                    containsPhone = containsPhone,
                    containsId = containsId18,
                    topApp = null // 如需记录前台 App，可扩展 UsageStats 获取
                )
            )
        } catch (t: Throwable) {
            Log.w(TAG, "EventBus.push failed: ${t.message}")
        }

        // 如需复制后也弹交互（清空/打码），可在此添加：
        // if (hasSensitive) { startActivity(...) 或 发通知 }
    }

    // —————————— 前台服务 ——————————
    private fun startForegroundInternal() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ClipMon 正在监控剪贴板")
            .setContentText("复制敏感信息时会提示，并记录最近事件")
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)
    }
}
