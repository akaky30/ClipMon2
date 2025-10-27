package com.example.clipmon2

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class SensitivePromptActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 透明主题也行，这里复用你的 Theme.ClipMon2
        super.onCreate(savedInstanceState)

        val origin = intent.getStringExtra(EXTRA_ORIGIN_TEXT).orEmpty()
        val masked = intent.getStringExtra(EXTRA_MASKED_TEXT).orEmpty()
        val hasPhone = intent.getBooleanExtra(EXTRA_HAS_PHONE, false)
        val hasId = intent.getBooleanExtra(EXTRA_HAS_ID, false)

        setContent {
            MaterialTheme {
                DialogUi(
                    origin = origin,
                    masked = masked,
                    hasPhone = hasPhone,
                    hasId = hasId,
                    onCopyOrigin = {
                        copyToClipboard(origin)
                        Toast.makeText(this, "已复制原文", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onCopyMasked = {
                        copyToClipboard(masked)
                        Toast.makeText(this, "已复制打码文本", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onClearNow = {
                        clearClipboard()
                        Toast.makeText(this, "已清空剪贴板（可在通知中再次设置定时）", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("", text))
        setResult(Activity.RESULT_OK)
    }

    private fun clearClipboard() {
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("", ""))
        setResult(Activity.RESULT_OK)
    }

    companion object {
        const val EXTRA_ORIGIN_TEXT = "origin"
        const val EXTRA_MASKED_TEXT = "masked"
        const val EXTRA_HAS_PHONE = "hasPhone"
        const val EXTRA_HAS_ID = "hasId"
    }
}

@Composable
private fun DialogUi(
    origin: String,
    masked: String,
    hasPhone: Boolean,
    hasId: Boolean,
    onCopyOrigin: () -> Unit,
    onCopyMasked: () -> Unit,
    onClearNow: () -> Unit
) {
    val flags = buildString {
        if (hasPhone) append("含手机号 ")
        if (hasId) append("含身份证")
    }.ifEmpty { "可能包含敏感信息" }

    val ctx = LocalContext.current

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = "检测到敏感复制", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(text = flags, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Text(text = "原文预览：${origin.take(150)}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Text(text = "打码预览：${masked.take(150)}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Row {
            Button(onClick = onCopyMasked) { Text("打码复制") }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onCopyOrigin) { Text("继续复制原文") }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onClearNow) { Text("立即清空") }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "提示：若应用不在前台，将通过通知提醒并提供操作按钮。",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
