package com.example.autotask

import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku

/**
 * 设置页（模仿 LSPosed 的简洁设置风格）。
 */
class SettingsActivity : AppCompatActivity() {

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        toast(if (grantResult == PackageManager.PERMISSION_GRANTED) "Shizuku 授权成功 ✅" else "Shizuku 授权被拒绝")
    }

    private val backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { writeBackup(it) }
    }
    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readBackup(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = "设置"

        val backendGroup = findViewById<RadioGroup>(R.id.backendGroup)
        val recordSwitch = findViewById<Switch>(R.id.recordSwitch)
        val recordDirEdit = findViewById<EditText>(R.id.recordDirEdit)
        val screenshotDirEdit = findViewById<EditText>(R.id.screenshotDirEdit)
        val serviceSwitch = findViewById<Switch>(R.id.serviceSwitch)
        val btnPermissions = findViewById<Button>(R.id.btnPermissions)
        val btnCheckUpdate = findViewById<Button>(R.id.btnCheckUpdate)
        val btnBackup = findViewById<Button>(R.id.btnBackup)
        val btnRestore = findViewById<Button>(R.id.btnRestore)
        val btnModule = findViewById<Button>(R.id.btnModule)
        val btnTutorial = findViewById<Button>(R.id.btnTutorial)
        val btnLog = findViewById<Button>(R.id.btnLog)

        // 后端选择
        backendGroup.check(
            when (AppSettings.getBackend(this)) {
                Backend.ROOT -> R.id.radioRoot
                Backend.SHIZUKU -> R.id.radioShizuku
                Backend.ACCESSIBILITY -> R.id.radioAccessibility
            }
        )
        backendGroup.setOnCheckedChangeListener { _, id ->
            val b = when (id) {
                R.id.radioShizuku -> Backend.SHIZUKU
                R.id.radioAccessibility -> Backend.ACCESSIBILITY
                else -> Backend.ROOT
            }
            AppSettings.setBackend(this, b)
        }

        // 录制
        recordSwitch.isChecked = AppSettings.isRecordEnabled(this)
        recordSwitch.setOnCheckedChangeListener { _, checked ->
            AppSettings.setRecordEnabled(this, checked)
        }
        recordDirEdit.setText(AppSettings.getRecordDir(this))
        recordDirEdit.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) AppSettings.setRecordDir(this, (v as EditText).text.toString().trim().ifBlank { "/sdcard/Movies" })
        }

        // 截图目录
        screenshotDirEdit.setText(AppSettings.getScreenshotDir(this))
        screenshotDirEdit.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) AppSettings.setScreenshotDir(this, (v as EditText).text.toString().trim().ifBlank { "/sdcard/Pictures" })
        }

        // 服务
        serviceSwitch.isChecked = true
        serviceSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) TaskService.start(this) else TaskService.stop(this)
        }

        btnPermissions.setOnClickListener { checkPermissions() }
        btnCheckUpdate.setOnClickListener { checkForUpdate(manual = true) }
        btnBackup.setOnClickListener { backupLauncher.launch("autotask_backup.json") }
        btnRestore.setOnClickListener { restoreLauncher.launch(arrayOf("application/json")) }
        btnModule.setOnClickListener { promptModule() }
        btnTutorial.setOnClickListener { startActivity(Intent(this, TutorialActivity::class.java)) }
        btnLog.setOnClickListener { showLog() }

        Shizuku.addRequestPermissionResultListener(shizukuListener)
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
        super.onDestroy()
    }

    // ==================== 权限 ====================

    private fun checkPermissions() {
        val backend = AppSettings.getBackend(this)
        when (backend) {
            Backend.ROOT -> if (!Shell.getShell().isRoot) {
                toast("请在 Magisk 中为本应用授予 Root 权限")
                return
            }
            Backend.SHIZUKU -> {
                if (!ShizukuUtil.isRunning()) {
                    toast("Shizuku 未运行，请先启动 Shizuku App")
                    return
                }
                if (!ShizukuUtil.isPermissionGranted()) {
                    toast("请求 Shizuku 授权中…")
                    ShizukuUtil.requestPermission(1001)
                    return
                }
            }
            Backend.ACCESSIBILITY -> if (!isAccessibilityEnabled()) {
                toast("请在无障碍设置里开启「定时任务（无障碍模式）」")
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            toast("请授予悬浮窗权限（用于屏幕取点）")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            toast("请允许「忽略电池优化」")
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            return
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                toast("请允许「精确闹钟」")
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                return
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
            return
        }
        toast("权限全部就绪 ✅")
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("$packageName/${AutoAccessibilityService::class.java.name}")
    }

    // ==================== 更新检测 ====================

    private fun checkForUpdate(manual: Boolean) {
        UpdateChecker.check { release ->
            val moduleVersion = readModuleVersion()
            runOnUiThread {
                if (release == null) {
                    if (manual) toast("检查更新失败，请稍后重试")
                    return@runOnUiThread
                }
                val appVersion = BuildConfig.VERSION_NAME
                val appUpdate = UpdateChecker.isNewer(release.version, appVersion)
                val moduleUpdate = moduleVersion != null && UpdateChecker.isNewer(release.version, moduleVersion)
                if (!appUpdate && !moduleUpdate) {
                    if (manual) toast("已是最新版本 v$appVersion")
                    return@runOnUiThread
                }
                val prefs = getSharedPreferences("update", MODE_PRIVATE)
                if (prefs.getString("skip_version", "") == release.version) return@runOnUiThread
                showUpdateDialog(release, appVersion, appUpdate, moduleVersion, moduleUpdate)
            }
        }
    }

    private fun showUpdateDialog(
        release: UpdateChecker.Release,
        appVersion: String,
        appUpdate: Boolean,
        moduleVersion: String?,
        moduleUpdate: Boolean
    ) {
        val sb = StringBuilder()
        sb.append("类型：${release.typeLabel}\n")
        if (release.name.isNotBlank()) sb.append(release.name).append("\n")
        sb.append("\n")
        if (appUpdate) sb.append("• 应用：v$appVersion → v${release.version}\n")
        if (moduleUpdate) sb.append("• 增强模块：v$moduleVersion → v${release.version}\n")
        sb.append("\n是否前往下载？")
        AlertDialog.Builder(this)
            .setTitle("发现新版本 v${release.version}")
            .setMessage(sb.toString())
            .setPositiveButton("下载更新") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.releaseTagUrl(release.version))))
                } catch (_: Exception) {
                }
            }
            .setNeutralButton("跳过该版本") { _, _ ->
                getSharedPreferences("update", MODE_PRIVATE).edit().putString("skip_version", release.version).apply()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 备份 / 恢复 ====================

    private fun writeBackup(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { os ->
                os.write(TaskStore.exportJson(this).toByteArray(Charsets.UTF_8))
            }
            toast("备份成功")
        } catch (e: Exception) {
            toast("备份失败：${e.message}")
        }
    }

    private fun readBackup(uri: Uri) {
        try {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
            val count = TaskStore.importJson(this, json)
            toast("已导入 $count 个任务")
            TaskScheduler.rescheduleAll(this)
        } catch (e: Exception) {
            toast("恢复失败：${e.message}")
        }
    }

    // ==================== 增强模块 ====================

    private fun promptModule() {
        if (!Shell.getShell().isRoot) {
            toast("需要 Root 环境才能安装增强模块")
            return
        }
        val version = readModuleVersion()
        if (version != null) {
            AlertDialog.Builder(this)
                .setTitle("增强模块")
                .setMessage("已安装增强模块 v$version")
                .setPositiveButton("配置模块") { _, _ -> startActivity(Intent(this, ModuleConfigActivity::class.java)) }
                .setNegativeButton("关闭", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("增强模块（未安装）")
                .setMessage("增强模块（Magisk 模块）可提供：\n• 自动解锁锁屏（输入 PIN）\n• 更稳定的后台定时执行\n\n是否前往下载？")
                .setPositiveButton("去下载") { _, _ ->
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.REPO_RELEASES_URL)))
                    } catch (_: Exception) {
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun readModuleVersion(): String? {
        return try {
            val r = Shell.cmd("cat /data/adb/modules/autotask/module.prop").exec()
            r.out.firstOrNull { it.startsWith("version=") }
                ?.removePrefix("version=")?.trim()?.removePrefix("v")?.ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    // ==================== 日志 ====================

    private fun showLog() {
        val log = ExecutionLog.get(this).ifBlank { "暂无执行记录" }
        val scroll = android.widget.ScrollView(this)
        scroll.addView(TextView(this).apply {
            text = log
            textSize = 13f
            setPadding(24, 24, 24, 24)
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_primary))
        })
        AlertDialog.Builder(this)
            .setTitle("执行日志")
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .setNeutralButton("清空") { _, _ ->
                ExecutionLog.clear(this)
                toast("日志已清空")
            }
            .setNegativeButton("导出") { _, _ ->
                logLauncher.launch("autotask_log.txt")
            }
            .show()
    }

    private val logLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { exportLog(it) }
    }

    private fun exportLog(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { os ->
                os.write(ExecutionLog.get(this).toByteArray(Charsets.UTF_8))
            }
            toast("日志已导出")
        } catch (e: Exception) {
            toast("导出失败：${e.message}")
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
