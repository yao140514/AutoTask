package com.example.autotask

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 闹钟触发后进入这里。
 * 委托给前台服务 TaskService 执行，避免 goAsync() 约 10 秒的生命周期限制。
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("taskId", -1)
        TaskService.execute(context, id)
    }
}
