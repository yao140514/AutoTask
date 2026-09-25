# AutoTask 增强模块（Magisk）

定时执行任务的 Root 守护模块，**支持自动解锁锁屏**等 App 无法实现的能力。

## 相比 App 多出的能力

- 🔓 **自动解锁锁屏**：配置 `unlock_pin` 后，执行任务前自动唤醒并输入 PIN 解锁
- 🚀 **更稳定后台执行**：以系统进程运行，不依赖 App 存活

## 任务类型

| 类型 | 格式 | 说明 |
|------|------|------|
| 点击 | `click X Y` | 点击坐标 |
| 滑动 | `swipe X1 Y1 X2 Y2 时长` | 时长毫秒 |
| 长按 | `longpress X Y 时长` | 长按 |
| 按键 | `key 键码` | 3=主页 4=返回 26=电源 24=音量+ |
| 开应用 | `app 包名` | 启动应用 |
| 链接 | `url 链接` | 打开链接 |
| 锁屏 | `lock` | 熄灭屏幕 |

## 配置

路径：`/data/adb/modules/autotask/tasks.conf`

```
# 自动解锁（可选，增强功能）
unlock_pin 123456

08:30:00 click 540 1200
12:00:00 app com.android.settings
22:30:00 lock
```

修改后无需重启，守护进程每秒读取配置自动生效。

## 已知限制

- 自动解锁是尽力而为：不同机型的锁屏界面不同，`input keyevent 82` + `input text` 可能需按机型调整
- 深睡时 shell `sleep` 循环可能延迟（需秒级精确请用 App 的 AlarmManager）
