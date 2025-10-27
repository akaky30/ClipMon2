package com.example.clipmon2

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EventBus {
    private val _events = MutableStateFlow<List<ClipEvent>>(emptyList())
    val events: StateFlow<List<ClipEvent>> = _events

    fun push(ev: ClipEvent) {
        _events.value = listOf(ev) + _events.value.take(999)
    }

    fun exportCsv(ctx: Context): File {
        val f = File(ctx.getExternalFilesDir(null), "clip_events_${System.currentTimeMillis()}.csv")
        f.bufferedWriter().use { w ->
            w.appendLine("time,type,rawLen,phone,id,topApp,preview")
            for (e in _events.value.reversed()) {
                val t = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(e.ts))
                w.appendLine("$t,${e.type},${e.rawLen},${e.containsPhone},${e.containsId},${e.topApp ?: ""},\"${e.preview.replace("\"","\"\"")}\"")
            }
        }
        return f
    }
}
