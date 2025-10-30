package com.example.clipmon2

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible

class ClipMonAccessibilityService : AccessibilityService() {

    private var overlayView: android.view.View? = null
    private var wm: WindowManager? = null

    // —— 新增：主线程 Handler & 防抖 —— //
    private val handler = Handler(Looper.getMainLooper())
    private var pendingRunnable: Runnable? = null
    private var pendingText: String? = null
    private var pendingNode: AccessibilityNodeInfo? = null

    // —— 去抖：同窗口同文本不要连弹 —— //
    private val lastShownForWindow = mutableMapOf<Int, String>()

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        val info = AccessibilityServiceInfo().apply {
            // 只监听“文本变化”，避免聚焦/窗口变化就弹
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags =
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                        AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                        AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
            notificationTimeout = 50
        }
        serviceInfo = info
        Log.d("ClipMon-Acc", "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return

        val node = event.source
        val fromNode = node?.text?.toString()?.trim().orEmpty()
        val fromEvent = event.text?.joinToString("")?.trim().orEmpty()
        val textNow = sequenceOf(fromNode, fromEvent).firstOrNull { it.isNotBlank() } ?: return

        if (node?.isPassword == true) return

        // —— 防抖：用户停止输入 600ms 才检查一次 —— //
        pendingRunnable?.let { handler.removeCallbacks(it) }
        pendingText = textNow
        pendingNode = node

        val winId = event.windowId
        pendingRunnable = Runnable {
            val t = pendingText ?: return@Runnable
            val srcNode = pendingNode

            // 同窗口同文本已提醒过就不再弹
            if (lastShownForWindow[winId] == t) return@Runnable

            if (SensitiveDetector.containsSensitive(t)) {
                lastShownForWindow[winId] = t
                if (srcNode != null) showWarningOverlay(srcNode, t) else showWarningOverlayFallback(t)
                Log.d("ClipMon-Acc", "hit sensitive: ${t.take(30)}")
            }
        }
        handler.postDelayed(pendingRunnable!!, 600) // 600ms 停止输入后再判定
    }

    override fun onInterrupt() {}

    private fun ensureOverlay() {
        if (overlayView != null) return
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_sensitive_hint, null)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP
        lp.y = 0
        wm?.addView(overlayView, lp)
    }

    private fun showWarningOverlay(targetNode: AccessibilityNodeInfo, textNow: String) {
        ensureOverlay()
        overlayView?.findViewById<TextView>(R.id.tv_title)?.text = "检测到可能的敏感输入"
        overlayView?.findViewById<TextView>(R.id.tv_msg)?.text = "是否打码或清空当前输入？"

        overlayView?.findViewById<Button>(R.id.btn_mask)?.setOnClickListener {
            val masked = SensitiveDetector.maskWithin(textNow)
            tryFocus(targetNode)
            // 直接 setText 覆盖
            val okSet = setTextCompat(targetNode, masked)
            Log.d("ClipMon-Acc", "mask setText=$okSet")
            hideOverlay()
        }

        overlayView?.findViewById<Button>(R.id.btn_clear)?.setOnClickListener {
            // 一次点击就清空，内部已做多策略兜底
            clearTextCompat(targetNode)
            hideOverlay()
        }

        overlayView?.findViewById<Button>(R.id.btn_close)?.setOnClickListener { hideOverlay() }
        overlayView?.isVisible = true
    }

    private fun showWarningOverlayFallback(@Suppress("UNUSED_PARAMETER") textNow: String) {
        ensureOverlay()
        overlayView?.findViewById<TextView>(R.id.tv_title)?.text = "检测到可能的敏感输入"
        overlayView?.findViewById<TextView>(R.id.tv_msg)?.text = "请检查并手动调整（当前应用限制）"
        overlayView?.findViewById<Button>(R.id.btn_mask)?.setOnClickListener { hideOverlay() }
        overlayView?.findViewById<Button>(R.id.btn_clear)?.setOnClickListener { hideOverlay() }
        overlayView?.findViewById<Button>(R.id.btn_close)?.setOnClickListener { hideOverlay() }
        overlayView?.isVisible = true
    }

    private fun hideOverlay() {
        overlayView?.isVisible = false
    }

    // —— 工具函数：更稳的一次性清空 —— //
    private fun tryFocus(node: AccessibilityNodeInfo) {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }

    private fun setTextCompat(node: AccessibilityNodeInfo, value: CharSequence): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!ok) node.refresh()
        return ok
    }

    private fun selectAll(node: AccessibilityNodeInfo) {
        val len = node.text?.length ?: 0
        val selArgs = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, len)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
    }

    private fun clearTextCompat(node: AccessibilityNodeInfo) {
        tryFocus(node)
        // 方案A：直接置空
        var ok = setTextCompat(node, "")
        if (ok) return

        // 方案B：全选+剪切（很多输入框支持）
        selectAll(node)
        ok = node.performAction(AccessibilityNodeInfo.ACTION_CUT)
        if (ok) return

        // 方案C：全选后再置空兜底
        selectAll(node)
        setTextCompat(node, "")
    }
}
