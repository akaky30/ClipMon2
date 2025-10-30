package com.example.clipmon2

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.PixelFormat
import android.os.Bundle
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

    // 去抖缓存：同窗口/同文本在短时间内不重复提醒
    private var lastWindowId: Int? = null
    private var lastText: String? = null
    private var lastTs = 0L

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        val info = AccessibilityServiceInfo().apply {
            eventTypes =
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_FOCUSED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
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
        // 1) 从事件拿文本
        val fromEvent = event.text?.joinToString("")?.trim().orEmpty()
        // 2) source 节点文本
        val node = event.source
        val fromNode = node?.text?.toString()?.trim().orEmpty()
        // 3) 焦点节点兜底
        val focusedText = try {
            val root = rootInActiveWindow
            val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            focused?.text?.toString()?.trim().orEmpty()
        } catch (_: Throwable) { "" }

        val textNow = sequenceOf(fromNode, fromEvent, focusedText)
            .firstOrNull { it.isNotBlank() } ?: return

        // 跳过密码/安全输入框
        if (node?.isPassword == true) return

        // 去抖：同窗口/同文本 1.2s 内不重复
        val now = System.currentTimeMillis()
        val winId = event.windowId
        if (lastWindowId == winId && lastText == textNow && now - lastTs < 1200) return

        if (SensitiveDetector.containsSensitive(textNow)) {
            lastWindowId = winId
            lastText = textNow
            lastTs = now

            if (node != null) showWarningOverlay(node, textNow) else showWarningOverlayFallback(textNow)
            Log.d("ClipMon-Acc", "hit sensitive: ${textNow.take(30)}")
        }
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
            targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val selArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, (targetNode.text?.length ?: 0))
            }
            targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
            val setArgs = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, masked)
            }
            val ok = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)
            Log.d("ClipMon-Acc", "mask setText=$ok")
            hideOverlay()
        }
        overlayView?.findViewById<Button>(R.id.btn_clear)?.setOnClickListener {
            val setArgs = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            }
            val ok = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)
            Log.d("ClipMon-Acc", "clear setText=$ok")
            hideOverlay()
        }
        overlayView?.findViewById<Button>(R.id.btn_close)?.setOnClickListener { hideOverlay() }
        overlayView?.isVisible = true
    }

    private fun showWarningOverlayFallback(textNow: String) {
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
}
