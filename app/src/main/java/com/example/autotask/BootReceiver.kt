package com.example.autotask

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机后重新注册所有闹钟（闹钟在重启后会丢失），并拉起前台服务 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON") {
            TaskScheduler.rescheduleAll(context)
            TaskService.start(context)
        }
    }
}
