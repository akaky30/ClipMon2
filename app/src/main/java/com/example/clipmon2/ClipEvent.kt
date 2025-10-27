package com.example.clipmon2

data class ClipEvent(
    val ts: Long,
    val type: String,              // "WRITE" 或 "READ"
    val preview: String,
    val rawLen: Int,
    val containsPhone: Boolean,
    val containsId: Boolean,
    val topApp: String?
)
