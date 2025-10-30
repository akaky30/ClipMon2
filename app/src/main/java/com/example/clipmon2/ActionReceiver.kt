// app/src/main/java/com/example/clipmon2/ActionReceiver.kt
package com.example.clipmon2

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MASK -> {
                val masked = intent.getStringExtra(EXTRA_MASKED_TEXT)
                if (!masked.isNullOrEmpty()) {
                    copyToClipboard(context, masked)
                    Toast.makeText(context, "已打码并复制到剪贴板", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w(TAG, "ACTION_MASK missing EXTRA_MASKED_TEXT")
                }
            }
            ACTION_CLEAR -> {
                clearClipboard(context)
                Toast.makeText(context, "已清空剪贴板", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent.action}")
            }
        }
    }

    private fun copyToClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("masked", text))
    }

    private fun clearClipboard(context: Context) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("", ""))
    }

    companion object {
        private const val TAG = "ClipMon-ActionReceiver"
        const val ACTION_MASK = "com.example.clipmon2.ACTION_MASK"
        const val ACTION_CLEAR = "com.example.clipmon2.ACTION_CLEAR"
        const val EXTRA_MASKED_TEXT = "masked_text"
    }
}
