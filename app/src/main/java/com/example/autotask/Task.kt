package com.example.autotask

enum class RepeatMode { DAILY, ONCE, WEEKLY }

/** 可模拟的按键 */
enum class KeyAction(val label: String, val keyCode: Int) {
    HOME("主页键", 3),
    BACK("返回键", 4),
    RECENT("最近任务", 187),
    POWER("电源/锁屏", 26),
    VOLUME_UP("音量 +", 24),
    VOLUME_DOWN("音量 -", 25),
    PLAY_PAUSE("播放/暂停", 85);
}

/** 动作类型 */
enum class ActionType(val label: String) {
    CLICK("点击坐标"),
    SWIPE("滑动"),
    LONG_PRESS("长按"),
    KEY_EVENT("模拟按键"),
    OPEN_APP("打开应用"),
    OPEN_URL("打开链接"),
    LOCK_SCREEN("锁屏"),
    NOTIFY("通知提醒"),
    SHELL("执行命令"),
    VOLUME("调整音量"),
    DELAY("延时等待"),
    RANDOM_DELAY("随机延时"),
    SET_VAR("设置变量"),
    CONDITION("条件判断"),
    HTTP_REQUEST("HTTP 请求"),
    SCREENSHOT("截图");
}

/** 音量通道（streamCode 同时是 AudioManager 的流类型常量） */
enum class VolumeStream(val label: String, val streamCode: Int) {
    MEDIA("媒体", 3),
    RING("铃声", 2),
    NOTIFICATION("通知", 5),
    ALARM("闹钟", 4);
}

/** 单个动作 */
data class Action(
    var type: ActionType = ActionType.CLICK,
    var x: Int = 0,
    var y: Int = 0,
    var x2: Int = 0,
    var y2: Int = 0,
    var duration: Int = 500,
    var keyAction: KeyAction = KeyAction.HOME,
    var packageName: String = "",
    var url: String = "",
    var message: String = "",
    var shellCmd: String = "",
    var volumeStream: VolumeStream = VolumeStream.MEDIA,
    var volume: Int = 50,
    var varName: String = "",
    var varValue: String = "",
    var condition: String = "screen_on",
    // 随机延时
    var minDelay: Int = 1000,
    var maxDelay: Int = 3000,
    // HTTP 请求（复用 url 字段作为地址）
    var httpMethod: String = "GET"
) {
    val summary: String
        get() = when (type) {
            ActionType.CLICK -> "点击 ($x,$y)"
            ActionType.SWIPE -> "滑动 ($x,$y)→($x2,$y2) ${duration}ms"
            ActionType.LONG_PRESS -> "长按 ($x,$y) ${duration}ms"
            ActionType.KEY_EVENT -> "按键 ${keyAction.label}"
            ActionType.OPEN_APP -> "打开 $packageName"
            ActionType.OPEN_URL -> "打开 $url"
            ActionType.LOCK_SCREEN -> "锁屏"
            ActionType.NOTIFY -> "通知: $message"
            ActionType.SHELL -> "命令: $shellCmd"
            ActionType.VOLUME -> "音量 ${volumeStream.label}→$volume"
            ActionType.DELAY -> "延时 ${duration}ms"
            ActionType.RANDOM_DELAY -> "随机延时 ${minDelay}~${maxDelay}ms"
            ActionType.SET_VAR -> "变量 $varName=$varValue"
            ActionType.CONDITION -> "条件: ${conditionText()}"
            ActionType.HTTP_REQUEST -> "HTTP $httpMethod $url"
            ActionType.SCREENSHOT -> "截图"
        }

    fun conditionText(): String = when {
        condition == "screen_on" -> "屏幕亮着"
        condition == "screen_off" -> "屏幕黑着"
        condition.startsWith("battery>") -> "电量>" + condition.removePrefix("battery>") + "%"
        else -> condition
    }
}

data class Task(
    val id: Int,
    var name: String,
    var hour: Int,
    var minute: Int,
    var second: Int,
    var repeat: RepeatMode,
    var enabled: Boolean,
    var weekdays: Int = 0,
    var actions: List<Action> = emptyList()
) {
    val timeText: String
        get() = String.format("%02d:%02d:%02d", hour, minute, second)

    val actionsSummary: String
        get() = actions.joinToString(" → ") { it.summary }.ifBlank { "（无动作）" }

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

    fun isWeekdaySelected(calendarDay: Int): Boolean =
        weekdays and (1 shl (calendarDay - 1)) != 0
}
