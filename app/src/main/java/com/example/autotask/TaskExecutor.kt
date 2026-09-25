package com.example.autotask

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku

/**
 * 任务执行器：根据所选后端（Root / Shizuku / 无障碍）执行任务。
 */
object TaskExecutor {

    fun execute(context: Context, task: Task) {
        val backend = AppSettings.getBackend(context)
        when (task.type) {
            TaskType.CLICK -> {
                if (task.wakeScreen) wakeIfNeeded(backend)
                tap(backend, task.x, task.y)
            }
            TaskType.SWIPE -> {
                if (task.wakeScreen) wakeIfNeeded(backend)
                swipe(backend, task.x, task.y, task.x2, task.y2, task.duration)
            }
            TaskType.LONG_PRESS -> {
                if (task.wakeScreen) wakeIfNeeded(backend)
                longPress(backend, task.x, task.y, task.duration)
            }
            TaskType.KEY_EVENT -> keyEvent(backend, task.keyAction)
            TaskType.LOCK_SCREEN -> lock(backend)
            TaskType.OPEN_APP -> openApp(backend, context, task.packageName)
            TaskType.OPEN_URL -> openUrl(context, task.url)
            TaskType.NOTIFY -> notify(context, task.message)
        }
    }

    // ==================== 通用 ====================

    private fun isScreenOn(backend: Backend): Boolean {
        val out: List<String> = when (backend) {
            Backend.ROOT -> Shell.cmd("dumpsys power").exec().out
            Backend.SHIZUKU -> shizukuOut("dumpsys power")
            Backend.ACCESSIBILITY -> return true
        }
        val line = out.firstOrNull { it.contains("mWakefulness=") }
        return line?.contains("Awake") == true
    }

    private fun wakeIfNeeded(backend: Backend) {
        if (backend == Backend.ACCESSIBILITY) return // 无障碍模式无法直接唤醒
        if (isScreenOn(backend)) return
        when (backend) {
            Backend.ROOT -> Shell.cmd("input keyevent 224", "input keyevent 82", "wm dismiss-keyguard").exec()
            Backend.SHIZUKU -> shizuku("input keyevent 224; input keyevent 82; wm dismiss-keyguard")
            else -> {}
        }
        Thread.sleep(400)
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
                    else -> return // 无障碍不支持音量/播放键
                }
                svc.performGlobalAction(g)
            }
        }
    }

    // ==================== 锁屏 ====================

    private fun lock(backend: Backend) {
        when (backend) {
            Backend.ROOT -> if (isScreenOn(Backend.ROOT)) Shell.cmd("input keyevent 26").exec()
            Backend.SHIZUKU -> if (isScreenOn(Backend.SHIZUKU)) shizuku("input keyevent 26")
            Backend.ACCESSIBILITY ->
                AutoAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        }
    }

    // ==================== 打开应用 ====================

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
                } else {
                    false
                }
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

    // ==================== 打开链接 ====================

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
