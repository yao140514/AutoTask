package com.example.autotask

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍服务：提供免 Root 的点击/滑动/长按/锁屏能力。
 * 使用前需在系统「无障碍」设置里手动开启本服务。
 */
class AutoAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AutoAccessibilityService? = null
            private set

        fun isConnected(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 本服务只做手势注入，不读取窗口内容
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    // ---- 手势 ----

    fun tap(x: Int, y: Int) {
        gesture(Path().apply { moveTo(x.toFloat(), y.toFloat()) }, 60)
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int) {
        gesture(Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }, duration.coerceAtLeast(100))
    }

    fun longPress(x: Int, y: Int, duration: Int) {
        gesture(Path().apply { moveTo(x.toFloat(), y.toFloat()) }, duration.coerceAtLeast(400))
    }

    private fun gesture(path: Path, duration: Int) {
        val stroke = GestureDescription.StrokeDescription(path, 0, duration.toLong())
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    // ---- 全局动作 ----

    fun lock() {
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }
}
