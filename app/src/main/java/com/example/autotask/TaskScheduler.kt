package com.example.autotask

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Calendar

object TaskScheduler {

    fun schedule(c: Context, task: Task) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancel(c, task.id)
        if (!task.enabled) return
        val trigger = nextTrigger(task)
        val pi = pendingIntent(c, task.id)
        try {
            // 即使休眠/省电模式也精确触发
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } catch (e: SecurityException) {
            // 没精确闹钟权限时降级
            am.set(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    fun cancel(c: Context, id: Int) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(c, id))
    }

    fun rescheduleAll(c: Context) {
        TaskStore.getAll(c).forEach { schedule(c, it) }
    }

    /** 计算下一次触发时间戳（毫秒） */
    fun nextTrigger(task: Task, from: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = from
        cal.set(Calendar.HOUR_OF_DAY, task.hour)
        cal.set(Calendar.MINUTE, task.minute)
        cal.set(Calendar.SECOND, task.second)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= from) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /** 下一次触发的友好描述，例如「今天 08:30:00」 */
    fun nextTriggerText(task: Task): String {
        val t = nextTrigger(task)
        val cal = Calendar.getInstance().apply { timeInMillis = t }
        val now = Calendar.getInstance()
        val sameDay = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        val day = if (sameDay) "今天" else "明天"
        return "$day ${cal.get(Calendar.HOUR_OF_DAY)}:" +
                String.format("%02d", cal.get(Calendar.MINUTE)) + ":" +
                String.format("%02d", cal.get(Calendar.SECOND))
    }

    private fun pendingIntent(c: Context, id: Int): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java)
        i.putExtra("taskId", id)
        // 用 data 保证不同 id 的 Intent 互不相同
        i.data = Uri.parse("task://$id")
        return PendingIntent.getBroadcast(
            c, id, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
