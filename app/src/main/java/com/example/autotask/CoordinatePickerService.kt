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
 * 悬浮窗取点：先在屏幕上点一下记录坐标，再弹出确认面板。
 */
class CoordinatePickerService : Service() {

    private var wm: WindowManager? = null
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
        showTouchCapture()
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

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
        touchView?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        touchView = null
        showConfirmPanel(x, y)
    }

    /** 确认面板（可聚焦，按钮可点击） */
    private fun showConfirmPanel(x: Int, y: Int) {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(18), dp(14), dp(18), dp(14))
        root.background = GradientDrawable().apply {
            setColor(Color.rgb(32, 32, 36))
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), Color.parseColor("#4FC3F7"))
        }

        val title = TextView(this)
        title.text = "点击位置"
        title.setTextColor(Color.WHITE)
        title.textSize = 15f

        val coord = TextView(this)
        coord.text = "X: $x    Y: $y"
        coord.setTextColor(Color.parseColor("#4FC3F7"))
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

    private fun cleanup() {
        panel?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        panel = null
        touchView?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        touchView = null
        onPick = null
        stopSelf()
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
