package com.example.autotask

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/**
 * 任务执行器：按顺序执行一个任务内的多个动作，支持三种后端（Root / Shizuku / 无障碍）。
 */
object TaskExecutor {

    fun execute(context: Context, task: Task) {
        val backend = AppSettings.getBackend(context)
        val vars = HashMap<String, String>()
        var skipNext = false
        for (action in task.actions) {
            if (skipNext) {
                skipNext = false
                continue
            }
            if (action.type == ActionType.CONDITION) {
                if (!evalCondition(context, action, vars)) skipNext = true
            } else {
                executeAction(context, backend, action, vars)
            }
        }
    }

    private fun executeAction(context: Context, backend: Backend, a: Action, vars: HashMap<String, String>) {
        when (a.type) {
            ActionType.CLICK -> { wakeIfNeeded(backend, context); tap(backend, a.x, a.y) }
            ActionType.SWIPE -> { wakeIfNeeded(backend, context); swipe(backend, a.x, a.y, a.x2, a.y2, a.duration) }
            ActionType.LONG_PRESS -> { wakeIfNeeded(backend, context); longPress(backend, a.x, a.y, a.duration) }
            ActionType.KEY_EVENT -> keyEvent(backend, a.keyAction)
            ActionType.LOCK_SCREEN -> lock(backend, context)
            ActionType.OPEN_APP -> openApp(backend, context, a.packageName)
            ActionType.OPEN_URL -> openUrl(context, a.url)
            ActionType.NOTIFY -> notify(context, a.message)
            ActionType.SHELL -> shell(backend, substitute(a.shellCmd, vars))
            ActionType.VOLUME -> setVolume(backend, context, a.volumeStream, a.volume)
            ActionType.DELAY -> Thread.sleep(a.duration.coerceAtLeast(0).toLong())
            ActionType.RANDOM_DELAY -> randomDelay(a.minDelay, a.maxDelay)
            ActionType.SET_VAR -> vars[a.varName] = a.varValue
            ActionType.CONDITION -> {} // 已在 execute 中处理
            ActionType.HTTP_REQUEST -> httpRequest(a.url, a.httpMethod)
        }
    }

    // ==================== 通用 ====================

    private fun isScreenOn(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive

    private fun batteryLevel(context: Context): Int =
        (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    private fun wakeIfNeeded(backend: Backend, context: Context) {
        if (backend == Backend.ACCESSIBILITY) return
        if (isScreenOn(context)) return
        when (backend) {
            Backend.ROOT -> Shell.cmd("input keyevent 224", "input keyevent 82", "wm dismiss-keyguard").exec()
            Backend.SHIZUKU -> shizuku("input keyevent 224; input keyevent 82; wm dismiss-keyguard")
            else -> {}
        }
        Thread.sleep(400)
    }

    private fun substitute(s: String, vars: Map<String, String>): String {
        var r = s
        for ((k, v) in vars) r = r.replace("\${$k}", v)
        return r
    }

    // ==================== 条件 ====================

    private fun evalCondition(context: Context, a: Action, vars: Map<String, String>): Boolean {
        val c = substitute(a.condition, vars)
        return when {
            c == "screen_on" -> isScreenOn(context)
            c == "screen_off" -> !isScreenOn(context)
            c.startsWith("battery>") -> {
                val threshold = c.removePrefix("battery>").toIntOrNull() ?: 0
                batteryLevel(context) > threshold
            }
            else -> false
        }
    }

    // ==================== 点击 / 滑动 / 长按 ====================

    private fun tap(backend: Backend, x: Int, y: Int) {
        when (backend) {
            Backend.ROOT -> Shell.cmd("input tap $x $y").exec()
            Backend.SHIZUKU -> shizuku("input tap $x $y")
            Backend.ACCESSIBILITY -> AutoAccessibilityService.instance?.tap(x, y)
        }
    }

    private fun swipe(backend: Backend, x1: Int, y1: Int, x2: Int, y2: Int, duration: Int) {
        when (backend) {
            Backend.ROOT -> Shell.cmd("input swipe $x1 $y1 $x2 $y2 $duration").exec()
            Backend.SHIZUKU -> shizuku("input swipe $x1 $y1 $x2 $y2 $duration")
            Backend.ACCESSIBILITY -> AutoAccessibilityService.instance?.swipe(x1, y1, x2, y2, duration)
        }
    }

    private fun longPress(backend: Backend, x: Int, y: Int, duration: Int) {
        when (backend) {
            Backend.ROOT -> Shell.cmd("input swipe $x $y $x $y $duration").exec()
            Backend.SHIZUKU -> shizuku("input swipe $x $y $x $y $duration")
            Backend.ACCESSIBILITY -> AutoAccessibilityService.instance?.longPress(x, y, duration)
        }
    }

    // ==================== 按键 ====================

    private fun keyEvent(backend: Backend, action: KeyAction) {
        when (backend) {
            Backend.ROOT -> Shell.cmd("input keyevent ${action.keyCode}").exec()
            Backend.SHIZUKU -> shizuku("input keyevent ${action.keyCode}")
            Backend.ACCESSIBILITY -> {
                val svc = AutoAccessibilityService.instance ?: return
                val g = when (action) {
                    KeyAction.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
                    KeyAction.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
                    KeyAction.RECENT -> AccessibilityService.GLOBAL_ACTION_RECENTS
                    KeyAction.POWER -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
                    else -> return
                }
                svc.performGlobalAction(g)
            }
        }
    }

    // ==================== 锁屏 ====================

    private fun lock(backend: Backend, context: Context) {
        when (backend) {
            Backend.ROOT -> if (isScreenOn(context)) Shell.cmd("input keyevent 26").exec()
            Backend.SHIZUKU -> if (isScreenOn(context)) shizuku("input keyevent 26")
            Backend.ACCESSIBILITY ->
                AutoAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        }
    }

    // ==================== 打开应用 / 链接 ====================

    private fun openApp(backend: Backend, context: Context, pkg: String) {
        if (pkg.isBlank()) return
        when (backend) {
            Backend.ROOT, Backend.SHIZUKU -> {
                val cmd = "cmd package resolve-activity --brief -c android.intent.category.LAUNCHER $pkg"
                val out = if (backend == Backend.ROOT) Shell.cmd(cmd).exec().out else shizukuOut(cmd)
                val activity = out.joinToString("").trim()
                val ok = if (activity.isNotBlank() && !activity.lowercase().startsWith("error")) {
                    val start = "am start -n $activity"
                    if (backend == Backend.ROOT) Shell.cmd(start).exec().isSuccess else shizuku(start)
                } else false
                if (!ok) {
                    val m = "monkey -p $pkg -c android.intent.category.LAUNCHER 1"
                    if (backend == Backend.ROOT) Shell.cmd(m).exec() else shizuku(m)
                }
            }
            Backend.ACCESSIBILITY -> {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun openUrl(context: Context, url: String) {
        if (url.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==================== 通知 ====================

    private fun notify(context: Context, message: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("autotask_notify", "任务提醒", NotificationManager.IMPORTANCE_HIGH)
        )
        val pi = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, "autotask_notify")
            .setContentTitle("定时任务提醒")
            .setContentText(message.ifBlank { "到点了" })
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        nm.notify((System.currentTimeMillis() % 100000).toInt(), n)
    }

    // ==================== Shell 命令 ====================

    private fun shell(backend: Backend, cmd: String) {
        if (cmd.isBlank()) return
        when (backend) {
            Backend.ROOT -> Shell.cmd(cmd).exec()
            Backend.SHIZUKU -> shizuku(cmd)
            Backend.ACCESSIBILITY -> {} // 无障碍无 shell 能力
        }
    }

    // ==================== 音量 ====================

    private fun setVolume(backend: Backend, context: Context, stream: VolumeStream, volume: Int) {
        when (backend) {
            Backend.ROOT -> Shell.cmd("media volume --stream ${stream.streamCode} --set ${(volume * 15) / 100}").exec()
            Backend.SHIZUKU -> shizuku("media volume --stream ${stream.streamCode} --set ${(volume * 15) / 100}")
            Backend.ACCESSIBILITY -> {
                try {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val max = am.getStreamMaxVolume(stream.streamCode)
                    val v = (volume * max) / 100
                    am.setStreamVolume(stream.streamCode, v, 0)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // ==================== 随机延时 / HTTP 请求 ====================

    private fun randomDelay(min: Int, max: Int) {
        val lo = min.coerceAtLeast(0)
        val hi = max.coerceAtLeast(lo)
        val delay = if (hi > lo) lo + Random.nextInt(hi - lo + 1) else lo
        Thread.sleep(delay.toLong())
    }

    private fun httpRequest(url: String, method: String) {
        if (url.isBlank()) return
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = method.uppercase().ifBlank { "GET" }
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.connect()
            conn.responseCode
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    // ==================== Shizuku 执行 ====================

    private fun shizuku(cmd: String): Boolean {
        if (!ShizukuUtil.isPermissionGranted()) return false
        return try {
            val p = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
            p.inputStream.bufferedReader().readText()
            p.waitFor()
            p.exitValue() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun shizukuOut(cmd: String): List<String> {
        if (!ShizukuUtil.isPermissionGranted()) return emptyList()
        val out = mutableListOf<String>()
        try {
            val p = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
            p.inputStream.bufferedReader().forEachLine { out.add(it) }
            p.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return out
    }
}
