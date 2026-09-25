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
 * 前台服务：常驻通知提升进程优先级，降低被系统/厂商后台清理的概率，
 * 从而保证闹钟可靠触发。
 */
class TaskService : Service() {

    companion object {
        const val CHANNEL = "autotask"
        const val NOTIFICATION_ID = 1

        fun start(c: Context) {
            c.startForegroundService(Intent(c, TaskService::class.java))
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, TaskService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
            .setContentText("将在设定时间自动执行点击 / 打开应用 / 锁屏")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }
}
