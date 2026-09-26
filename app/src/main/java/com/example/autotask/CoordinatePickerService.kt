package com.example.autotask

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 悬浮窗取点（优化版）：
 * 1. 先显示一个悬浮按钮，用户点击按钮后才开始取点；
 * 2. 点击屏幕记录坐标，弹出确认面板。
 */
class CoordinatePickerService : Service() {

    private var wm: WindowManager? = null
    private var startButton: View? = null
    private var touchView: View? = null
    private var panel: View? = null

    companion object {
        var onPick: ((Int, Int) -> Unit)? = null

        fun start(c: Context, callback: (Int, Int) -> Unit) {
            onPick = callback
            c.startService(Intent(c, CoordinatePickerService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showStartButton()
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

    /** 悬浮按钮：点击后开始取点 */
    private fun showStartButton() {
        val btn = Button(this)
        btn.text = "🔘 点我开始取点"
        btn.setTextColor(Color.WHITE)
        btn.background = GradientDrawable().apply {
            setColor(Color.parseColor("#00897B"))
            cornerRadius = dp(24).toFloat()
        }
        btn.setOnClickListener {
            removeView(startButton)
            startButton = null
            showTouchCapture()
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.y = dp(100)
        try {
            wm?.addView(btn, lp)
            startButton = btn
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    /** 全屏透明触摸捕获层 */
    private fun showTouchCapture() {
        val v = object : View(this) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_UP) {
                    onTapped(event.rawX.toInt(), event.rawY.toInt())
                }
                return true
            }
        }
        v.setBackgroundColor(Color.TRANSPARENT)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        try {
            wm?.addView(v, lp)
            touchView = v
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun onTapped(x: Int, y: Int) {
        removeView(touchView)
        touchView = null
        showConfirmPanel(x, y)
    }

    private fun showConfirmPanel(x: Int, y: Int) {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(18), dp(14), dp(18), dp(14))
        root.background = GradientDrawable().apply {
            setColor(Color.rgb(32, 32, 36))
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), Color.parseColor("#4DB6AC"))
        }

        val title = TextView(this)
        title.text = "点击位置"
        title.setTextColor(Color.WHITE)
        title.textSize = 15f

        val coord = TextView(this)
        coord.text = "X: $x    Y: $y"
        coord.setTextColor(Color.parseColor("#4DB6AC"))
        coord.textSize = 20f
        coord.setPadding(0, dp(10), 0, dp(14))

        val btnRow = LinearLayout(this)
        btnRow.orientation = LinearLayout.HORIZONTAL

        val cancel = Button(this)
        cancel.text = "取消"
        cancel.setOnClickListener { cleanup() }

        val ok = Button(this)
        ok.text = "确定"
        ok.setOnClickListener {
            val cb = onPick
            cleanup()
            cb?.invoke(x, y)
        }

        btnRow.addView(cancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        btnRow.addView(ok, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        root.addView(title)
        root.addView(coord)
        root.addView(btnRow)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.CENTER
        try {
            wm?.addView(root, lp)
            panel = root
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun removeView(v: View?) {
        v?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
    }

    private fun cleanup() {
        removeView(panel); panel = null
        removeView(touchView); touchView = null
        removeView(startButton); startButton = null
        onPick = null
        stopSelf()
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
