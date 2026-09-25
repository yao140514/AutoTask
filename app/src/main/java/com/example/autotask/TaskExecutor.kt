package com.example.autotask

import com.topjohnwu.superuser.Shell

/**
 * 通过 Root 真正执行任务。
 * 所有命令都用 root 权限的 shell 执行。
 */
object TaskExecutor {

    fun execute(task: Task) {
        when (task.type) {
            TaskType.LOCK_SCREEN -> lockScreen()
            TaskType.CLICK -> {
                if (task.wakeScreen) wakeAndUnlock()
                click(task.x, task.y)
            }
            TaskType.OPEN_APP -> {
                if (task.wakeScreen) wakeAndUnlock()
                openApp(task.packageName)
            }
        }
    }

    private fun isScreenOn(): Boolean {
        val r = Shell.cmd("dumpsys power").exec()
        val line = r.out.firstOrNull { it.contains("mWakefulness=") }
        return line?.contains("Awake") == true
    }

    /** 唤醒屏幕并尝试解除 keyguard（仅对滑动解锁/无锁有效） */
    private fun wakeAndUnlock() {
        if (isScreenOn()) return
        Shell.cmd(
            "input keyevent 224",   // KEYCODE_WAKEUP 唤醒
            "input keyevent 82",    // KEYCODE_MENU 部分机型可解除滑动锁
            "wm dismiss-keyguard"   // 解除 keyguard
        ).exec()
        Thread.sleep(400)
    }

    private fun click(x: Int, y: Int) {
        Shell.cmd("input tap $x $y").exec()
    }

    private fun lockScreen() {
        // 仅当屏幕亮着时才按电源键（否则会变成亮屏）
        if (isScreenOn()) {
            Shell.cmd("input keyevent 26").exec() // KEYCODE_POWER
        }
    }

    private fun openApp(pkg: String) {
        // 先解析启动 Activity，再 am start；失败则用 monkey 兜底
        val r = Shell.cmd("cmd package resolve-activity --brief -c android.intent.category.LAUNCHER $pkg").exec()
        val activity = r.out.joinToString("").trim()
        val ok = if (activity.isNotBlank() && !activity.lowercase().startsWith("error")) {
            Shell.cmd("am start -n $activity").exec().isSuccess
        } else {
            false
        }
        if (!ok) {
            Shell.cmd("monkey -p $pkg -c android.intent.category.LAUNCHER 1").exec()
        }
    }
}
