package com.example.autotask

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 闹钟触发后进入这里。
 * 用 goAsync() 让系统在异步执行期间保持唤醒锁，避免休眠中断 root 命令。
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        Thread {
            try {
                val id = intent.getIntExtra("taskId", -1)
                val task = TaskStore.get(context, id)
                if (task != null && task.enabled) {
                    TaskExecutor.execute(task)
                    when (task.repeat) {
                        RepeatMode.DAILY -> TaskScheduler.schedule(context, task)
                        RepeatMode.ONCE -> {
                            task.enabled = false
                            TaskStore.update(context, task)
                            TaskScheduler.cancel(context, task.id)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                result.finish()
            }
        }.start()
    }
}
