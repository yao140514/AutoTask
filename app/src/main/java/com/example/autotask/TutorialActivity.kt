package com.example.autotask

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TutorialActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial)
        title = "使用教程"
        findViewById<TextView>(R.id.tvTutorial).text = TUTORIAL
    }

    companion object {
        val TUTORIAL = """
            ▍执行方式（三选一）

            ① Root 模式（推荐）
               需要手机已 Root（Magisk）。首次打开本应用时 Magisk 会弹窗，
               点「允许」即可。

            ② Shizuku 模式（免 Root）
               1. 安装 Shizuku 应用（GitHub: RikkaApps/Shizuku）
               2. 启动 Shizuku（二选一）：
                  · 无线调试（Android 11+）：开发者选项 → 无线调试，
                    然后在 Shizuku 里点「通过无线调试启动」
                  · ADB：电脑连手机执行
                    adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
               3. 回到本应用 → 选「Shizuku」→ 点「检查并授权权限」→ 允许

            ③ 无障碍模式（免 Root）
               1. 系统设置 → 无障碍 → 已下载的应用
               2. 找到「定时任务（无障碍模式）」→ 开启
               3. 回到本应用选「无障碍」执行方式

            ▍创建任务
               1. 点右下角 ＋
               2. 填写名称、时间、重复方式（每天/仅一次/每周）
               3. 点「添加动作」逐个添加动作（可添加多个，按顺序执行）
               4. 点「测试执行」验证效果，再点「保存」

            ▍动作类型（共 15 种）
               点击 / 滑动 / 长按 / 模拟按键 / 打开应用 / 打开链接 /
               锁屏 / 通知 / Shell命令 / 调整音量 / 延时 / 随机延时 /
               设置变量 / 条件判断 / HTTP请求

            ▍增强模块（Magisk 模块）
               · 作用：自动解锁锁屏、更稳定的后台执行
               · 检测到 Root 时会提示下载，也可点主页「增强模块」按钮
               · 配置：/data/adb/modules/autotask/tasks.conf
               · 自动解锁：配置里加一行 unlock_pin 你的锁屏密码

            ▍注意事项
               · 有 PIN/图案锁时无法自动解锁后点击（可用模块 unlock_pin）
               · 坐标是绝对像素，换分辨率/横竖屏需重新取点
               · 国产 ROM（MIUI/EMUI 等）需允许自启动 + 后台运行
        """.trimIndent()
    }
}
