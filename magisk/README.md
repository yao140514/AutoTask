# AutoTask 增强模块（Magisk）

定时执行任务的 Root 守护模块，支持**自动解锁锁屏**及多种动作。

## 相比 App 多出的能力

- 🔓 **自动解锁锁屏**：配置 `unlock_pin` 后，执行任务前自动唤醒并输入 PIN 解锁
- 🚀 **更稳定后台执行**：以系统进程运行，不依赖 App 存活

## 支持的任务类型（18 种）

| 类型 | 格式 | 说明 |
|------|------|------|
| 点击 | `click X Y` | 点击坐标 |
| 滑动 | `swipe X1 Y1 X2 Y2 时长` | 时长毫秒 |
| 长按 | `longpress X Y 时长` | 长按 |
| 按键 | `key 键码` | 3=主页 4=返回 26=电源 24=音量+ |
| 开应用 | `app 包名` | 启动应用 |
| 链接 | `url 链接` | 打开链接 |
| 锁屏 | `lock` | 熄灭屏幕 |
| 通知 | `notify 标题\|内容` | 发送通知 |
| 命令 | `shell 命令` | 执行 shell 命令 |
| 音量 | `volume 通道 0-100` | media/ring/notification/alarm |
| 亮度 | `brightness 0-255` | 调整屏幕亮度 |
| 延时 | `delay 秒` | 可小数 |
| 随机延时 | `randdelay 最小 最大` | 秒 |
| HTTP | `http URL` | 发送 GET（仅 http://，走 nc） |
| 截图 | `screenshot` | 保存到 /sdcard/Pictures |
| 变量 | `setvar 名 值` | shell 中可用 `$名` 引用 |
| 条件 | `if 条件` | screen_on/screen_off/battery>N，不满足跳过下一行 |

## 配置

路径：`/data/adb/modules/autotask/tasks.conf`

```
# 自动解锁（可选，增强功能）
unlock_pin 123456

08:30:00 click 540 1200
09:00:00 notify 打卡提醒|该打卡了
12:00:00 app com.android.settings
22:30:00 lock
```

修改后无需重启，守护进程每秒读取配置自动生效。

## 变量与条件示例

```
# 设置变量，shell 里用 $myvar 引用
08:00:00 setvar myvar hello

# 条件判断：屏幕黑着则跳过下一行（同一时间的任务）
08:05:00 if screen_off
08:05:00 click 540 1200

# 电量大于 50 才执行下一行
08:10:00 if battery>50
08:10:00 app com.android.settings
```

## 查看日志

```sh
cat /data/adb/modules/autotask/autotask.log
```

## 已知限制

- 自动解锁是尽力而为：不同机型锁屏界面不同，`input keyevent 82` + `input text` 可能需按机型调整
- 通知功能需 Android 8+
- HTTP 请求仅支持 http://（通过 nc），https 不支持（本机无 curl/wget）
- 深睡时 shell `sleep` 循环可能延迟（需秒级精确请用 App 的 AlarmManager）
