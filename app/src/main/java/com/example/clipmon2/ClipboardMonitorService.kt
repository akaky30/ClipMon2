package com.example.clipmon2

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket

class ClipboardMonitorService : Service() {
    private lateinit var clipboard: ClipboardManager
    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    @Volatile private var autoClearSecs: Long = 30
    private var udpJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannelIfNeed()
        startForeground(NOTI_ID, buildNotification("监控已启动"))

        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        listener = ClipboardManager.OnPrimaryClipChangedListener { handleClipboardWrite() }
        clipboard.addPrimaryClipChangedListener(listener!!)
        udpJob = startUdpReadEventServer()
    }

    private fun handleClipboardWrite() {
        val clip = try { clipboard.primaryClip } catch (_: SecurityException) { null }
        val text = clip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()

        val (hasPhone, hasId, masked) = SensitiveDetector.analyze(text)
        val top = getTopAppPackage(this)
        val ev = ClipEvent(
            ts = System.currentTimeMillis(),
            type = "WRITE",
            preview = masked.take(120),
            rawLen = text.length,
            containsPhone = hasPhone,
            containsId = hasId,
            topApp = top
        )
        EventBus.push(ev)

        // ★ 新增：敏感时弹窗/通知分支
        if (text.isNotEmpty() && (hasPhone || hasId)) {
            val isForeground = top == packageName
            if (isForeground) {
                // App 在前台：直接启动弹窗 Activity
                val i = Intent(this, SensitivePromptActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(SensitivePromptActivity.EXTRA_ORIGIN_TEXT, text)
                    .putExtra(SensitivePromptActivity.EXTRA_MASKED_TEXT, masked)
                    .putExtra(SensitivePromptActivity.EXTRA_HAS_PHONE, hasPhone)
                    .putExtra(SensitivePromptActivity.EXTRA_HAS_ID, hasId)
                startActivity(i)
            } else {
                // App 不在前台：发通知提供 Action
                showSensitiveActionsNotification(masked = masked)
            }
        }

        if (autoClearSecs > 0 && text.isNotEmpty()) scheduleAutoClear(autoClearSecs)
        updateNotificationSummary(ev)
    }

    private fun scheduleAutoClear(seconds: Long) {
        scope.launch(Dispatchers.Main) {
            delay(seconds * 1000)
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    private fun updateNotificationSummary(ev: ClipEvent) {
        val msg = when {
            ev.containsPhone && ev.containsId -> "含手机号与身份证：已记录/可定时清空"
            ev.containsPhone -> "含手机号：已记录/可定时清空"
            ev.containsId -> "含身份证：已记录/可定时清空"
            else -> "长度 ${ev.rawLen} 字：已记录"
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTI_ID, buildNotification(msg))
    }

    private fun showSensitiveActionsNotification(masked: String) {
        val maskIntent = Intent(this, ActionReceiver::class.java).apply {
            action = ActionReceiver.ACTION_MASK_COPY
            putExtra(ActionReceiver.EXTRA_MASKED_TEXT, masked)
        }
        val clearIntent = Intent(this, ActionReceiver::class.java).apply {
            action = ActionReceiver.ACTION_CLEAR
        }
        val clearDelayIntent = Intent(this, ActionReceiver::class.java).apply {
            action = ActionReceiver.ACTION_CLEAR_IN_30S
        }

        val pMask = PendingIntent.getBroadcast(
            this, 1, maskIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pClear = PendingIntent.getBroadcast(
            this, 2, clearIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pClearDelay = PendingIntent.getBroadcast(
            this, 3, clearDelayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val noti = NotificationCompat.Builder(this, NOTI_CH)
            .setContentTitle("检测到敏感复制")
            .setContentText("可选择打码复制 / 清空 / 30 秒后清空")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .addAction(0, "打码复制", pMask)
            .addAction(0, "清空", pClear)
            .addAction(0, "30 秒后清空", pClearDelay)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTI_SENSITIVE_ID, noti)
    }

    private fun startUdpReadEventServer() = scope.launch(Dispatchers.IO) {
        // 注意：真机/模拟器有时限制端口，失败就终止协程（不崩溃）
        try {
            val socket = DatagramSocket(33333)
            val buf = ByteArray(8 * 1024)
            while (isActive) {
                val packet = DatagramPacket(buf, buf.size)
                socket.receive(packet)
                val s = String(packet.data, 0, packet.length, Charsets.UTF_8)
                val top = getTopAppPackage(this@ClipboardMonitorService)
                EventBus.push(
                    ClipEvent(
                        ts = System.currentTimeMillis(),
                        type = "READ",
                        preview = s.take(120),
                        rawLen = s.length,
                        containsPhone = false,
                        containsId = false,
                        topApp = top
                    )
                )
                updateNotificationSummary(
                    ClipEvent(System.currentTimeMillis(), "READ", "", 0, false, false, top)
                )
            }
            // socket.close() 由系统回收
        } catch (_: Exception) {
            // 忽略网络错误，避免崩溃
        }
    }

    override fun onDestroy() {
        listener?.let { clipboard.removePrimaryClipChangedListener(it) }
        udpJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannelIfNeed() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 &&
            nm.getNotificationChannel(NOTI_CH) == null) {
            nm.createNotificationChannel(
                NotificationChannel(NOTI_CH, "剪贴板监控", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, NOTI_CH)
            .setContentTitle("ClipMon 运行中")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun getTopAppPackage(ctx: Context): String? {
        return try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val begin = end - 5_000
            val events = usm.queryEvents(begin, end)

            var lastPkg: String? = null
            val ev = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                if (ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastPkg = ev.packageName
                }
            }
            lastPkg
        } catch (_: Exception) { null }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTI_CH = "clipmon"
        private const val NOTI_ID = 1
        private const val NOTI_SENSITIVE_ID = 2

        // 给通知 Action 用：安排 30s 后清空
        fun enqueueAutoClear(context: Context, seconds: Long) {
            val app = context.applicationContext
            val i = Intent(app, ClipboardMonitorService::class.java)
            // 简化起见，这里直接用当前前台服务的定时逻辑
            // 实际项目可用 WorkManager 更稳
            app.startService(i) // 确保服务在
            // 直接拿到服务实例并不容易，这里退而求其次：发广播已在 Service 中默认 30s 清空
            // 如果你希望严格 seconds 可配，也可以把秒数写进 SharedPreferences，由 Service 读取。
        }
    }
}
