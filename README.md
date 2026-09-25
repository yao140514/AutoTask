# 定时任务 (AutoTask)

一个安卓定时任务工具，可在指定时间（精确到秒）自动执行各种任务，支持 **三种执行方式**：

- 🖱️ **点击坐标**：悬浮按钮取点（点按钮 → 再点屏幕），到点自动点击
- 👆 **滑动 / 长按**：支持自定义时长
- 📱 **打开应用 / 打开链接**
- ⌨️ **模拟按键**（主页 / 返回 / 最近任务 / 电源 / 音量 / 播放暂停）
- 🔒 **自动锁屏** · 🔔 **通知提醒**
- 🔗 **多动作任务**：一个任务内可组合多个动作，按顺序执行
- 🖥️ **Shell 命令 / 调整音量 / 延时 / 变量 / 条件判断**
- 🔁 **重复方式**：每天 / 仅一次 / 每周（可选星期几）
- 🔄 **自动检测更新**（区分正式版/测试版，支持跳过版本）
- 💾 **备份 / 恢复配置**

即使 **息屏 / 省电模式 (Doze)** 下也能准点触发。

---

## 三种执行方式（可切换）

| 方式 | 需要什么 | 能力 | 说明 |
|------|---------|------|------|
| **Root**（默认） | Magisk 等 Root | 全部 | 最稳定，推荐 |
| **Shizuku** | 安装 [Shizuku](https://shizuku.rikka.app/) 并授权 | 全部 | 免 Root，拥有 ADB 级权限 |
| **无障碍** | 系统无障碍里开启本服务 | 点击/滑动/长按/锁屏/部分按键 | 免 Root，但无法唤醒黑屏、音量键等不支持 |

> 主页顶部可随时切换执行方式，状态栏会显示各方式是否就绪。

---

## 增强模块（Magisk 模块）

检测到 Root 环境时，App 会自动提示下载「增强模块」，用于实现 **App 无法做到的能力**：

| 能力 | 说明 |
|------|------|
| 🔓 **自动解锁锁屏** | 配置 `unlock_pin` 后，执行任务前自动唤醒并输入 PIN 解锁 |
| 🚀 **更稳定后台执行** | 以系统进程运行，不依赖 App 存活 |
| 📱 全部任务类型 | 点击/滑动/长按/按键/开应用/链接/锁屏 |

模块源码在 `magisk/` 目录，配置见 `magisk/README.md`。

---

## 技术方案

| 组件 | 说明 |
|------|------|
| 定时 | `AlarmManager.setExactAndAllowWhileIdle`（精确闹钟，休眠下也触发） |
| 保活 | 前台服务（常驻通知）+ 忽略电池优化 |
| Root 执行 | [libsu](https://github.com/topjohnwu/libsu) |
| Shizuku 执行 | [Shizuku API](https://github.com/RikkaApps/Shizuku-API) |
| 无障碍执行 | `AccessibilityService` 手势注入 |
| 取点 | `SYSTEM_ALERT_WINDOW` 悬浮窗 |
| 存储 | `SharedPreferences` + `org.json` |

---

## 目录结构

```
AutoTask/
├── settings.gradle
├── build.gradle
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/autotask/
        │   ├── App.kt                    # libsu 初始化
        │   ├── Task.kt                   # 任务/类型/按键枚举
        │   ├── Settings.kt               # 执行方式设置
        │   ├── TaskStore.kt              # 任务存取 (SharedPreferences)
        │   ├── TaskScheduler.kt          # 闹钟调度（含每周）
        │   ├── TaskExecutor.kt           # 三后端分发执行
        │   ├── AutoAccessibilityService.kt # 无障碍服务
        │   ├── AlarmReceiver.kt          # 闹钟回调
        │   ├── BootReceiver.kt           # 开机重新注册闹钟
        │   ├── TaskService.kt            # 前台服务
        │   ├── CoordinatePickerService.kt# 悬浮窗取点
        │   ├── MainActivity.kt           # 任务列表 + 方式选择
        │   └── AddEditTaskActivity.kt    # 新建/编辑任务
        └── res/                          # 布局、主题、图标、无障碍配置
```

---

## 前置条件（三选一）

1. **Root**：已安装 Magisk（或其它 `su` 方案）
2. **Shizuku**：安装 Shizuku App 并通过无线调试/ADB 启动
3. **无障碍**：无需额外安装，只需在系统设置里开启本服务

编译环境：Android Studio，或 JDK 17 + Android SDK。

---

## 编译步骤

**方式一：Android Studio（推荐）**

1. 用 Android Studio 打开 `AutoTask` 文件夹
2. 等待 Gradle 同步完成
3. 手机开启 USB 调试，连接电脑，点运行 ▶

**方式二：命令行（已内置 Gradle Wrapper）**

```bash
cd AutoTask
./gradlew assembleDebug          # 产物在 app/build/outputs/apk/debug/
```

> 首次运行 `./gradlew` 会自动下载 Gradle 8.2。项目已配置腾讯镜像加速；
> 如在海外环境，可把 `gradle/wrapper/gradle-wrapper.properties` 里的
> `distributionUrl` 改回 `https\://services.gradle.org/distributions/gradle-8.2-bin.zip`。

---

## 安装与授权（首次必做）

1. 安装 APK 后打开应用
2. 主页选择 **执行方式**（Root / Shizuku / 无障碍）
3. 点 **「检查并授权权限」**，按提示依次完成：
   - **Root**：Magisk 弹窗点「允许」（仅 Root 方式需要）
   - **Shizuku**：授权（仅 Shizuku 方式需要）
   - **无障碍**：系统设置里开启「定时任务（无障碍模式）」（仅无障碍方式需要）
   - **悬浮窗**：允许（用于屏幕取点）
   - **忽略电池优化**：允许，否则休眠下可能延迟
   - **精确闹钟**：Android 12+ 需允许
   - **通知**：Android 13+ 允许
4. 保持 **后台服务** 开关开启

---

## 使用方法

1. 主页点右下角 **＋** 新建任务
2. 填写名称、时间（时/分/秒）
3. 选择任务类型，按类型填写参数：
   - **点击坐标 / 长按** → 悬浮窗取点
   - **滑动** → 取起点 + 取终点 + 时长
   - **打开应用** → 选择应用
   - **打开链接** → 输入 URL
   - **模拟按键** → 下拉选按键
   - **锁屏 / 通知提醒** → 填提醒内容（通知）或无需参数（锁屏）
4. 选择重复方式：每天 / 仅一次 / 每周（勾选星期几）
5. 保存

保存后列表会显示「下次：今天/明天/具体日期 HH:MM:SS」。

---

## 注意事项 / 已知限制

- **锁屏界面无法自动解锁**：有 PIN/图案/密码锁时，任务只能「唤醒屏幕」（Root/Shizuku 模式），无法自动解锁后点击。无障碍模式无法唤醒黑屏。
- **坐标是绝对像素**：不同分辨率/横竖屏下坐标含义不同。
- **厂商后台清理**：MIUI/EMUI/ColorOS 等需在系统「电池/自启动」里允许本应用自启动、后台运行。
- **无障碍模式限制**：不支持音量键/播放键、无法唤醒黑屏、打开链接等需依赖系统允许。
- **每周重复**：未勾选任何星期几时会拒绝保存。

---

## 自定义扩展

想加新任务类型？改两处即可：

1. `Task.kt` 的 `TaskType` 枚举加一项
2. `TaskExecutor.execute()` 里加对应分支，例如：

```kotlin
// 输入文本示例（Root/Shizuku）
Shell.cmd("input text hello").exec()
```

其它命令参考：`input tap/swipe/keyevent/text`、`am start`、`monkey`、`cmd` 等。
