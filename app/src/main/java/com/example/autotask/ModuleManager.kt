package com.example.autotask

import android.util.Base64
import com.topjohnwu.superuser.Shell

/** 增强模块管理：读取/写入模块配置文件（需 Root） */
object ModuleManager {
    const val MODULE_DIR = "/data/adb/modules/autotask"
    const val CONFIG_PATH = "$MODULE_DIR/tasks.conf"
    const val PROP_PATH = "$MODULE_DIR/module.prop"

    fun isInstalled(): Boolean = try {
        Shell.cmd("test -f $PROP_PATH && echo yes").exec().out.any { it.contains("yes") }
    } catch (e: Exception) {
        false
    }

    fun readConfig(): String? = try {
        val r = Shell.cmd("cat $CONFIG_PATH").exec()
        if (r.isSuccess) r.out.joinToString("\n") else null
    } catch (e: Exception) {
        null
    }

    fun writeConfig(content: String): Boolean = try {
        val b64 = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        Shell.cmd("echo $b64 | base64 -d > $CONFIG_PATH").exec().isSuccess
    } catch (e: Exception) {
        false
    }

    val DEFAULT_CONFIG: String = """
        # ============================================================
        #  AutoTask 增强模块配置（修改后自动生效，无需重启）
        #  每行一个任务：时间 类型 参数
        #  时间格式：HH:MM:SS（24 小时制）
        #
        #  任务类型：
        #    click X Y                点击坐标
        #    swipe X1 Y1 X2 Y2 时长    滑动（毫秒）
        #    longpress X Y 时长        长按（毫秒）
        #    key 键码                 模拟按键（3=主页 4=返回 26=电源 24=音量+）
        #    app 包名                 打开应用
        #    url 链接                 打开链接
        #    lock                     锁屏
        #    notify 标题|内容          发送通知（| 分隔标题和内容）
        #    shell 命令               执行 shell 命令
        #    volume 通道 0-100        调整音量（通道: media/ring/notification/alarm）
        #    delay 秒                 延时（可小数，如 0.5）
        #    randdelay 最小 最大       随机延时（秒）
        #    http URL                 发送 HTTP GET 请求（仅 http://，https 不支持）
        #
        #  【增强功能】自动解锁：设置下面这行，执行任务前自动输入 PIN 解锁
        #    unlock_pin 你的锁屏密码
        #
        #  示例（去掉行首 # 启用）：
        # 08:30:00 click 540 1200
        # 09:00:00 notify 打卡提醒|该打卡了
        # 12:00:00 app com.android.settings
        # 22:30:00 lock
        # ============================================================
    """.trimIndent()
}
