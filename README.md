# 定时任务 (AutoTask)

一个基于 **Root** 的安卓定时任务工具，可在指定时间（精确到秒）自动执行：

- 🖱️ **点击坐标**：通过悬浮窗在屏幕上取点，到点自动 `input tap`
- 📱 **打开应用**：到点自动启动指定 App
- 🔒 **自动锁屏**：到点自动熄灭屏幕

即使 **息屏 / 省电模式 (Doze)** 下也能准点触发。

---

## 技术方案

| 组件 | 说明 |
|------|------|
| 定时 | `AlarmManager.setExactAndAllowWhileIdle`（精确闹钟，休眠下也触发） |
| 保活 | 前台服务（常驻通知）+ 忽略电池优化 |
| Root 执行 | [libsu](https://github.com/topjohnwu/libsu)（Magisk 作者出品） |
| 取点 | `SYSTEM_ALERT_WINDOW` 悬浮窗 |
| 存储 | `SharedPreferences` + `org.json`（零额外依赖） |

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
        │   ├── Task.kt                   # 任务数据模型
        │   ├── TaskStore.kt              # 任务存取 (SharedPreferences)
        │   ├── TaskScheduler.kt          # 闹钟调度
        │   ├── TaskExecutor.kt           # Root 执行点击/开应用/锁屏
        │   ├── AlarmReceiver.kt          # 闹钟回调
        │   ├── BootReceiver.kt           # 开机重新注册闹钟
        │   ├── TaskService.kt            # 前台服务
        │   ├── CoordinatePickerService.kt# 悬浮窗取点
        │   ├── MainActivity.kt           # 任务列表
        │   └── AddEditTaskActivity.kt    # 新建/编辑任务
        └── res/                          # 布局、主题、图标
```

---

## 前置条件

1. 已安装 **Magisk**（或其它提供 `su` 的 Root 方案）
2. 电脑装有 **Android Studio**（或仅需 JDK + Android SDK 命令行编译）

---

## 编译步骤

**方式一：Android Studio（推荐）**

1. 用 Android Studio 打开 `AutoTask` 文件夹
2. 等待 Gradle 同步完成（首次会自动下载依赖）
3. 手机开启 USB 调试，连接电脑
4. 点运行 ▶，或 `Build → Build APK(s)` 生成 APK

**方式二：命令行（已内置 Gradle Wrapper）**

```bash
cd AutoTask
./gradlew assembleDebug          # 产物在 app/build/outputs/apk/debug/
```

> 首次运行 `./gradlew` 会自动下载 Gradle 8.2。项目已配置腾讯镜像加速；
> 如在海外环境，可把 `gradle/wrapper/gradle-wrapper.properties` 里的
> `distributionUrl` 改回 `https\://services.gradle.org/distributions/gradle-8.2-bin.zip`。

> 生成的 APK 可直接安装，或复制到手机安装。

---

## 安装与授权（首次必做）

1. 安装 APK 后打开应用
2. 点击 **「检查并授权权限」**，按提示依次完成：
   - **Root**：Magisk 弹窗点「允许」
   - **悬浮窗**：系统设置里允许本应用显示在其他应用上层
   - **忽略电池优化**：允许，否则休眠下可能延迟
   - **精确闹钟**：Android 12+ 需允许
   - **通知**：Android 13+ 允许
3. 保持 **后台服务** 开关为开启状态

---

## 使用方法

1. 主页点右下角 **＋** 新建任务
2. 填写名称、时间（时/分/秒）
3. 选择类型：
   - **点击坐标** → 点「屏幕上取点」，屏幕会变透明覆盖层，在目标位置点一下，确认即可记录坐标
   - **打开应用** → 点「选择应用」挑一个
   - **锁屏** → 无需参数
4. 选择「每天」或「仅一次」，保存

保存后列表会显示「下次：今天/明天 HH:MM:SS」。

---

## 注意事项 / 已知限制

- **锁屏界面无法自动解锁**：若手机有 **PIN/图案/密码** 锁，任务只能「唤醒屏幕」，无法自动解锁后点击。请关闭锁屏、或使用「滑动解锁」，或把本应用设为「无锁屏」环境使用。`wm dismiss-keyguard` 对安全锁无效。
- **坐标是绝对像素**：不同分辨率/横竖屏下坐标含义不同；若目标位置变动需重新取点。
- **厂商后台清理**：MIUI/EMUI/ColorOS 等需在系统「电池/自启动」设置里允许本应用自启动、允许后台运行，否则闹钟可能被杀。
- **每天重复**：任务按「下一次 HH:MM:SS」触发；若当天时间已过则顺延到明天。
- **仅一次**：执行一次后自动停用。

---

## 自定义扩展

想加新任务类型？改两处即可：

1. `Task.kt` 的 `TaskType` 枚举加一项
2. `TaskExecutor.execute()` 里加对应分支，例如：

```kotlin
// 长按示例
Shell.cmd("input swipe $x $y $x $y 800").exec()
// 输入文本示例
Shell.cmd("input text hello").exec()
```

其它 root 命令参考：`input tap/swipe/keyevent/text`、`am start`、`monkey`、`cmd` 等。
