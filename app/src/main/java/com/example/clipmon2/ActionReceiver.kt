package com.example.clipmon2

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val cm = context.getSystemService(ClipboardManager::class.java)

        when (intent.action) {
            ACTION_MASK_COPY -> {
                val masked = intent.getStringExtra(EXTRA_MASKED_TEXT).orEmpty()
                cm.setPrimaryClip(ClipData.newPlainText("", masked))
                Toast.makeText(context, "已复制打码文本", Toast.LENGTH_SHORT).show()
            }
            ACTION_CLEAR -> {
                cm.setPrimaryClip(ClipData.newPlainText("", ""))
                Toast.makeText(context, "已清空剪贴板", Toast.LENGTH_SHORT).show()
            }
            ACTION_CLEAR_IN_30S -> {
                // 通过前台服务安排 30s 清空
                ClipboardMonitorService.enqueueAutoClear(context, 30)
                Toast.makeText(context, "已设置 30 秒后清空", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val ACTION_MASK_COPY = "com.example.clipmon2.ACTION_MASK_COPY"
        const val ACTION_CLEAR = "com.example.clipmon2.ACTION_CLEAR"
        const val ACTION_CLEAR_IN_30S = "com.example.clipmon2.ACTION_CLEAR_IN_30S"

        const val EXTRA_MASKED_TEXT = "masked"
    }
}
