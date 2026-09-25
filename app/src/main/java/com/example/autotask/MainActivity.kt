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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var statusText: TextView
    private lateinit var serviceSwitch: Switch
    private lateinit var backendSpinner: Spinner
    private lateinit var adapter: TaskAdapter

    private var updateChecked = false

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        toast(if (grantResult == PackageManager.PERMISSION_GRANTED) "Shizuku 授权成功 ✅" else "Shizuku 授权被拒绝")
        updateStatus()
    }

    private val backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { writeBackup(it) }
    }
    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readBackup(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recycler = findViewById(R.id.recycler)
        statusText = findViewById(R.id.statusText)
        serviceSwitch = findViewById(R.id.serviceSwitch)
        backendSpinner = findViewById(R.id.backendSpinner)
        val fab = findViewById<FloatingActionButton>(R.id.fab)
        val btnPermissions = findViewById<Button>(R.id.btnPermissions)
        val btnCheckUpdate = findViewById<Button>(R.id.btnCheckUpdate)
        val btnBackup = findViewById<Button>(R.id.btnBackup)
        val btnRestore = findViewById<Button>(R.id.btnRestore)
        val btnModule = findViewById<Button>(R.id.btnModule)
        val btnTutorial = findViewById<Button>(R.id.btnTutorial)
        val btnLog = findViewById<Button>(R.id.btnLog)

        adapter = TaskAdapter()
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        val backendAdapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.backends)
        )
        backendAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        backendSpinner.adapter = backendAdapter
        backendSpinner.setSelection(AppSettings.getBackend(this).ordinal)
        backendSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                AppSettings.setBackend(this@MainActivity, Backend.values()[position])
                updateStatus()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        fab.setOnClickListener { startActivity(Intent(this, AddEditTaskActivity::class.java)) }
        btnPermissions.setOnClickListener { checkPermissions() }
        btnCheckUpdate.setOnClickListener { checkForUpdate(manual = true) }
        btnBackup.setOnClickListener { backupLauncher.launch("autotask_backup.json") }
        btnRestore.setOnClickListener { restoreLauncher.launch(arrayOf("application/json")) }
        btnModule.setOnClickListener { promptModule() }
        btnTutorial.setOnClickListener { startActivity(Intent(this, TutorialActivity::class.java)) }
        btnLog.setOnClickListener { showLog() }

        serviceSwitch.isChecked = true
        serviceSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) TaskService.start(this) else TaskService.stop(this)
        }

        TaskService.start(this)
        Shizuku.addRequestPermissionResultListener(shizukuListener)
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        updateStatus()
        checkAndPromptEnhancedModule()
        if (!updateChecked) {
            updateChecked = true
            checkForUpdate(manual = false)
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
        super.onDestroy()
    }

    private fun refresh() {
        adapter.submit(TaskStore.getAll(this))
    }

    private fun updateStatus() {
        val root = Shell.getShell().isRoot
        val shizukuRunning = ShizukuUtil.isRunning()
        val shizukuGranted = ShizukuUtil.isPermissionGranted()
        val accessibility = isAccessibilityEnabled()
        val overlay = Settings.canDrawOverlays(this)
        val battery = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(packageName)
        val exact = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        } else true

        statusText.text = buildString {
            append("版本：v").append(BuildConfig.VERSION_NAME).append('\n')
            append("执行方式：").append(
                when (AppSettings.getBackend(this@MainActivity)) {
                    Backend.ROOT -> "Root"
                    Backend.SHIZUKU -> "Shizuku"
                    Backend.ACCESSIBILITY -> "无障碍"
                }
            ).append('\n')
            append("Root：").append(if (root) "✅" else "❌").append('\n')
            append("Shizuku：").append(
                if (shizukuRunning && shizukuGranted) "✅" else if (shizukuRunning) "⚠️ 未授权" else "❌ 未运行"
            ).append('\n')
            append("无障碍：").append(if (accessibility) "✅" else "❌").append('\n')
            append("增强模块：").append(readModuleVersion()?.let { "已安装 v$it" } ?: "未安装").append('\n')
            append("悬浮窗：").append(if (overlay) "✅" else "❌").append('\n')
            append("忽略省电：").append(if (battery) "✅" else "❌").append('\n')
            append("精确闹钟：").append(if (exact) "✅" else "❌")
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("$packageName/${AutoAccessibilityService::class.java.name}")
    }

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

    // ==================== 更新检测 ====================

    private fun checkForUpdate(manual: Boolean) {
        UpdateChecker.check { release ->
            runOnUiThread {
                if (release == null) {
                    if (manual) toast("检查更新失败，请稍后重试")
                    return@runOnUiThread
                }
                val appVersion = BuildConfig.VERSION_NAME
                val appUpdate = UpdateChecker.isNewer(release.version, appVersion)
                val moduleVersion = readModuleVersion()
                val moduleUpdate = moduleVersion != null && UpdateChecker.isNewer(release.version, moduleVersion)

                if (!appUpdate && !moduleUpdate) {
                    if (manual) toast("已是最新版本 v$appVersion")
                    return@runOnUiThread
                }
                val prefs = getSharedPreferences("update", MODE_PRIVATE)
                if (prefs.getString("skip_version", "") == release.version) {
                    return@runOnUiThread
                }
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
                getSharedPreferences("update", MODE_PRIVATE)
                    .edit().putString("skip_version", release.version).apply()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 读取已安装增强模块的版本号（未安装返回 null） */
    private fun readModuleVersion(): String? {
        return try {
            val r = Shell.cmd("cat /data/adb/modules/autotask/module.prop").exec()
            r.out.firstOrNull { it.startsWith("version=") }
                ?.removePrefix("version=")?.trim()?.removePrefix("v")?.ifBlank { null }
        } catch (e: Exception) {
            null
        }
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
            refresh()
            TaskScheduler.rescheduleAll(this)
        } catch (e: Exception) {
            toast("恢复失败：${e.message}")
        }
    }

    // ==================== 增强模块提示 ====================

    private fun checkAndPromptEnhancedModule() {
        if (!Shell.getShell().isRoot) return
        if (readModuleVersion() != null) return
        val prefs = getSharedPreferences("module_prompt", MODE_PRIVATE)
        if (prefs.getBoolean("prompted", false)) return
        prefs.edit().putBoolean("prompted", true).apply()
        promptModule()
    }

    /** 打开增强模块下载 / 状态对话框（主页「增强模块」按钮） */
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
                .setPositiveButton("知道了", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("增强模块（未安装）")
                .setMessage("增强模块（Magisk 模块）可提供：\n" +
                        "• 自动解锁锁屏（输入 PIN）\n" +
                        "• 更稳定的后台定时执行\n\n" +
                        "是否前往下载？")
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

    /** 显示执行日志 */
    private fun showLog() {
        val log = ExecutionLog.get(this).ifBlank { "暂无执行记录" }
        val scroll = android.widget.ScrollView(this)
        scroll.addView(TextView(this).apply {
            text = log
            textSize = 13f
            setPadding(24, 24, 24, 24)
            setTextColor(0xFFFFFFFF.toInt())
        })
        AlertDialog.Builder(this)
            .setTitle("执行日志")
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .setNeutralButton("清空") { _, _ ->
                ExecutionLog.clear(this)
                toast("日志已清空")
            }
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private inner class TaskAdapter : RecyclerView.Adapter<TaskAdapter.VH>() {
        private var items: List<Task> = emptyList()

        fun submit(list: List<Task>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        override fun getItemCount(): Int = items.size

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            private val title: TextView = v.findViewById(R.id.tvTitle)
            private val subtitle: TextView = v.findViewById(R.id.tvSubtitle)
            private val switch: Switch = v.findViewById(R.id.switchEnabled)
            private val run: Button = v.findViewById(R.id.btnRun)
            private val delete: ImageButton = v.findViewById(R.id.btnDelete)

            fun bind(task: Task) {
                title.text = "${task.name}（${task.repeatText}）"
                subtitle.text = "${task.timeText}　${task.actionsSummary}\n下次：${TaskScheduler.nextTriggerText(task)}"

                switch.setOnCheckedChangeListener(null)
                switch.isChecked = task.enabled
                switch.setOnCheckedChangeListener { _, checked ->
                    task.enabled = checked
                    TaskStore.update(this@MainActivity, task)
                    TaskScheduler.schedule(this@MainActivity, task)
                }

                run.setOnClickListener {
                    toast("正在运行「${task.name}」…")
                    Thread {
                        TaskExecutor.execute(this@MainActivity, task)
                        ExecutionLog.add(this@MainActivity, "手动运行「${task.name}」")
                        runOnUiThread { toast("运行完成") }
                    }.start()
                }

                delete.setOnClickListener {
                    TaskScheduler.cancel(this@MainActivity, task.id)
                    TaskStore.delete(this@MainActivity, task.id)
                    refresh()
                }

                itemView.setOnClickListener {
                    val i = Intent(this@MainActivity, AddEditTaskActivity::class.java)
                    i.putExtra("id", task.id)
                    startActivity(i)
                }
            }
        }
    }
}
