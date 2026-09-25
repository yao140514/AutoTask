package com.example.autotask

enum class TaskType {
    CLICK,       // 点击坐标
    SWIPE,       // 滑动
    LONG_PRESS,  // 长按
    KEY_EVENT,   // 模拟按键
    OPEN_APP,    // 打开应用
    OPEN_URL,    // 打开链接
    LOCK_SCREEN, // 锁屏
    NOTIFY       // 通知提醒
}

enum class RepeatMode { DAILY, ONCE, WEEKLY }

/** 可模拟的按键（keyCode 用于 input keyevent） */
enum class KeyAction(val label: String, val keyCode: Int) {
    HOME("主页键", 3),
    BACK("返回键", 4),
    RECENT("最近任务", 187),
    POWER("电源/锁屏", 26),
    VOLUME_UP("音量 +", 24),
    VOLUME_DOWN("音量 -", 25),
    PLAY_PAUSE("播放/暂停", 85);
}

data class Task(
    val id: Int,
    var name: String,
    var hour: Int,
    var minute: Int,
    var second: Int,
    var type: TaskType,
    var repeat: RepeatMode,
    var enabled: Boolean,
    // 点击 / 长按
    var x: Int = 0,
    var y: Int = 0,
    // 滑动终点 & 时长
    var x2: Int = 0,
    var y2: Int = 0,
    var duration: Int = 500,   // 毫秒
    // 按键
    var keyAction: KeyAction = KeyAction.HOME,
    // 打开应用
    var packageName: String = "",
    // 打开链接
    var url: String = "",
    // 通知
    var message: String = "",
    // 每周重复：位掩码，bit0=周日 bit1=周一 ... bit6=周六（对应 Calendar.DAY_OF_WEEK-1）
    var weekdays: Int = 0,
    var wakeScreen: Boolean = true
) {
    val timeText: String
        get() = String.format("%02d:%02d:%02d", hour, minute, second)

    val typeText: String
        get() = when (type) {
            TaskType.CLICK -> "点击 ($x,$y)"
            TaskType.SWIPE -> "滑动 ($x,$y)→($x2,$y2)"
            TaskType.LONG_PRESS -> "长按 ($x,$y)"
            TaskType.KEY_EVENT -> "按键 ${keyAction.label}"
            TaskType.OPEN_APP -> "打开 $packageName"
            TaskType.OPEN_URL -> "打开链接 $url"
            TaskType.LOCK_SCREEN -> "锁屏"
            TaskType.NOTIFY -> "通知: $message"
        }

    val repeatText: String
        get() = when (repeat) {
            RepeatMode.DAILY -> "每天"
            RepeatMode.ONCE -> "仅一次"
            RepeatMode.WEEKLY -> "每周 " + weekdayText()
        }

    fun weekdayText(): String {
        if (repeat != RepeatMode.WEEKLY) return ""
        val names = arrayOf("日", "一", "二", "三", "四", "五", "六")
        val sb = StringBuilder()
        for (i in 0..6) {
            if (weekdays and (1 shl i) != 0) {
                if (sb.isNotEmpty()) sb.append('、')
                sb.append(names[i])
            }
        }
        return if (sb.isEmpty()) "未选" else sb.toString()
    }

    fun isWeekdaySelected(calendarDay: Int): Boolean {
        // calendarDay: Calendar.SUNDAY(1)..SATURDAY(7)
        return weekdays and (1 shl (calendarDay - 1)) != 0
    }
}
