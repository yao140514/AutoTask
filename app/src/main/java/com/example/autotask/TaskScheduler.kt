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
        if (trigger <= 0) return
        val pi = pendingIntent(c, task.id)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } catch (e: SecurityException) {
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

    /** 计算下一次触发时间戳（毫秒）；返回 <=0 表示无法安排 */
    fun nextTrigger(task: Task, from: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = from

        if (task.repeat == RepeatMode.WEEKLY) {
            for (i in 0..7) {
                val dow = cal.get(Calendar.DAY_OF_WEEK)
                if (task.isWeekdaySelected(dow)) {
                    val candidate = Calendar.getInstance().apply {
                        timeInMillis = cal.timeInMillis
                        set(Calendar.HOUR_OF_DAY, task.hour)
                        set(Calendar.MINUTE, task.minute)
                        set(Calendar.SECOND, task.second)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    if (candidate > from) return candidate
                }
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return 0
        }

        // DAILY / ONCE
        cal.set(Calendar.HOUR_OF_DAY, task.hour)
        cal.set(Calendar.MINUTE, task.minute)
        cal.set(Calendar.SECOND, task.second)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= from) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun nextTriggerText(task: Task): String {
        val t = nextTrigger(task)
        if (t <= 0) return "无"
        val cal = Calendar.getInstance().apply { timeInMillis = t }
        val now = Calendar.getInstance()
        val sameDay = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        val tomorrow = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) + 1

        val day = when {
            sameDay -> "今天"
            tomorrow -> "明天"
            else -> {
                val week = arrayOf("日", "一", "二", "三", "四", "五", "六")
                "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日(周${week[cal.get(Calendar.DAY_OF_WEEK) - 1]})"
            }
        }
        return "$day ${cal.get(Calendar.HOUR_OF_DAY)}:" +
                String.format("%02d", cal.get(Calendar.MINUTE)) + ":" +
                String.format("%02d", cal.get(Calendar.SECOND))
    }

    private fun pendingIntent(c: Context, id: Int): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java)
        i.putExtra("taskId", id)
        i.data = Uri.parse("task://$id")
        return PendingIntent.getBroadcast(
            c, id, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
