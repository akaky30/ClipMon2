package com.example.clipmon2

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private var monitoring by mutableStateOf(false)

    private val requestPostNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                ensureUsageAccess {
                    startMonitorServiceSafely()
                }
            } else {
                Toast.makeText(this, "未授予通知权限，无法开启前台服务", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val events by EventBus.events.collectAsState()
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Row {
                        Button(onClick = { onStartMonitorClicked() }, enabled = !monitoring) {
                            Text("开始监控")
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = { stopMonitor() }, enabled = monitoring) {
                            Text("停止")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Button(onClick = { openUsageAccessSettings() }) { Text("授予使用情况访问") }
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = { exportCsv() }) { Text("导出CSV") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("最近事件（上新在前）", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(events) { e ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        "${DateFormat.format("MM-dd HH:mm:ss", e.ts)}  ${e.type}" +
                                                (e.topApp?.let { "  top=$it" } ?: "")
                                    )
                                    val flags = buildList {
                                        if (e.containsPhone) add("含手机号")
                                        if (e.containsId) add("含身份证")
                                    }.joinToString(" / ")
                                    if (flags.isNotEmpty()) Text(flags, color = Color.Red)
                                    Text("len=${e.rawLen}")
                                    Text(e.preview)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onStartMonitorClicked() {
        ensurePostNotificationPermission {
            ensureUsageAccess {
                startMonitorServiceSafely()
            }
        }
    }

    private fun ensurePostNotificationPermission(onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT < 33) {
            onGranted(); return
        }
        val nm = getSystemService(NotificationManager::class.java)
        val enabled = nm.areNotificationsEnabled()
        val granted = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (enabled && granted) onGranted()
        else requestPostNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun ensureUsageAccess(onGranted: () -> Unit) {
        if (hasUsageAccess()) onGranted()
        else {
            openUsageAccessSettings()
            Toast.makeText(this, "请授予“使用情况访问”后再开启监控", Toast.LENGTH_LONG).show()
        }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java)
        return try {
            // 兼容旧系统：优先用 checkOpNoThrow（API19+稳定存在）
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) {
            // 兜底：如果方法不存在/异常，就返回 false
            false
        }
    }

    private fun openUsageAccessSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun startMonitorServiceSafely() {
        val intent = Intent(this, ClipboardMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        monitoring = true
    }

    private fun stopMonitor() {
        stopService(Intent(this, ClipboardMonitorService::class.java))
        monitoring = false
    }

    private fun exportCsv() {
        val f = EventBus.exportCsv(this)
        Toast.makeText(this, "已导出：${f.absolutePath}", Toast.LENGTH_LONG).show()
    }
}
