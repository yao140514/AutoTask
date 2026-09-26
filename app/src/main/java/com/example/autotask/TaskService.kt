package com.example.autotask

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * 前台服务：常驻通知提升进程优先级，保证闹钟可靠触发；
 * 同时负责在闹钟触发后执行任务（后台线程，不受 goAsync 生命周期限制）。
 */
class TaskService : Service() {

    companion object {
        const val CHANNEL = "autotask"
        const val NOTIFICATION_ID = 1
        private const val ACTION_EXECUTE = "com.example.autotask.EXECUTE"

        fun start(c: Context) {
            c.startForegroundService(Intent(c, TaskService::class.java))
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, TaskService::class.java))
        }

        /** 触发某个任务的执行 */
        fun execute(c: Context, taskId: Int) {
            val i = Intent(c, TaskService::class.java).setAction(ACTION_EXECUTE)
            i.putExtra("taskId", taskId)
            try {
                c.startForegroundService(i)
            } catch (e: Exception) {
                // 极端情况下后台启动受限时降级为普通启动
                try {
                    c.startService(i)
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_EXECUTE) {
            val taskId = intent.getIntExtra("taskId", -1)
            Thread { runTask(taskId) }.start()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private var recordProcess: Process? = null

    private fun runTask(taskId: Int) {
        try {
            val task = TaskStore.get(this, taskId) ?: return
            if (!task.enabled) return
            TaskExecutor.execute(this, task)
            when (task.repeat) {
                RepeatMode.DAILY, RepeatMode.WEEKLY -> TaskScheduler.schedule(this, task)
                RepeatMode.ONCE -> {
                    task.enabled = false
                    TaskStore.update(this, task)
                    TaskScheduler.cancel(this, task.id)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "定时任务", NotificationManager.IMPORTANCE_LOW)
        )
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("定时任务运行中")
            .setContentText("将在设定时间自动执行任务")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }
}
