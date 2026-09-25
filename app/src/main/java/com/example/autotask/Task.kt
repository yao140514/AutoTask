package com.example.autotask

enum class TaskType { CLICK, OPEN_APP, LOCK_SCREEN }

enum class RepeatMode { DAILY, ONCE }

data class Task(
    val id: Int,
    var name: String,
    var hour: Int,
    var minute: Int,
    var second: Int,
    var type: TaskType,
    var repeat: RepeatMode,
    var enabled: Boolean,
    var x: Int = 0,
    var y: Int = 0,
    var packageName: String = "",
    var wakeScreen: Boolean = true
) {
    val timeText: String
        get() = String.format("%02d:%02d:%02d", hour, minute, second)

    val typeText: String
        get() = when (type) {
            TaskType.CLICK -> "点击 ($x,$y)"
            TaskType.OPEN_APP -> "打开 $packageName"
            TaskType.LOCK_SCREEN -> "锁屏"
        }
}
