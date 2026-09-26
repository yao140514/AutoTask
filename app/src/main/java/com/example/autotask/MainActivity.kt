package com.example.autotask

import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.topjohnwu.superuser.Shell

/**
 * 主页（模仿 LSPosed）：只保留任务列表 + 新建按钮 + 设置入口。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var statusText: TextView
    private lateinit var adapter: TaskAdapter

    private var updateChecked = false

    @Volatile
    private var cachedModuleVersion: String? = null
    @Volatile
    private var moduleVersionLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recycler = findViewById(R.id.recycler)
        statusText = findViewById(R.id.statusText)
        val fab = findViewById<FloatingActionButton>(R.id.fab)

        adapter = TaskAdapter()
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        fab.setOnClickListener { startActivity(Intent(this, AddEditTaskActivity::class.java)) }

        TaskService.start(this)
        refreshModuleVersionAsync()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.action_pause_all -> togglePauseAll()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun togglePauseAll() {
        val tasks = TaskStore.getAll(this)
        val anyEnabled = tasks.any { it.enabled }
        val newState = !anyEnabled
        tasks.forEach {
            it.enabled = newState
            TaskStore.update(this, it)
            if (newState) TaskScheduler.schedule(this, it) else TaskScheduler.cancel(this, it.id)
        }
        refresh()
        toast(if (newState) "已恢复全部任务" else "已暂停全部任务")
    }

    override fun onResume() {
        super.onResume()
        refresh()
        updateStatus()
        checkAndPromptEnhancedModule()
        if (!updateChecked) {
            updateChecked = true
            checkForUpdate()
        }
    }

    private fun refresh() {
        adapter.submit(TaskStore.getAll(this))
    }

    // ==================== 状态面板 ====================

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
        val backend = AppSettings.getBackend(this)
        val module = readModuleVersion()

        statusText.text = buildString {
            append("v").append(BuildConfig.VERSION_NAME).append("  ·  ").append(
                when (backend) {
                    Backend.ROOT -> "Root"
                    Backend.SHIZUKU -> "Shizuku"
                    Backend.ACCESSIBILITY -> "无障碍"
                }
            ).append('\n')
            append("Root ").append(if (root) "✅" else "❌").append("   ")
            append("Shizuku ").append(if (shizukuRunning && shizukuGranted) "✅" else "❌").append("   ")
            append("无障碍 ").append(if (accessibility) "✅" else "❌").append('\n')
            append("模块 ").append(module?.let { "v$it" } ?: "未装").append("   ")
            append("悬浮窗 ").append(if (overlay) "✅" else "❌").append("   ")
            append("省电 ").append(if (battery) "✅" else "❌").append("   ")
            append("闹钟 ").append(if (exact) "✅" else "❌")
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("$packageName/${AutoAccessibilityService::class.java.name}")
    }

    // ==================== 更新检测 ====================

    private fun checkForUpdate() {
        UpdateChecker.check { release ->
            val moduleVersion = readModuleVersionBlocking()
            runOnUiThread {
                if (release == null) return@runOnUiThread
                val appVersion = BuildConfig.VERSION_NAME
                val appUpdate = UpdateChecker.isNewer(release.version, appVersion)
                val moduleUpdate = moduleVersion != null && UpdateChecker.isNewer(release.version, moduleVersion)
                if (!appUpdate && !moduleUpdate) return@runOnUiThread
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

    // ==================== 增强模块提示 ====================

    private fun checkAndPromptEnhancedModule() {
        if (!Shell.getShell().isRoot) return
        if (!moduleVersionLoaded) return
        if (readModuleVersion() != null) return
        val prefs = getSharedPreferences("module_prompt", MODE_PRIVATE)
        if (prefs.getBoolean("prompted", false)) return
        prefs.edit().putBoolean("prompted", true).apply()
        AlertDialog.Builder(this)
            .setTitle("检测到 Root 环境")
            .setMessage("建议安装「增强模块」获得自动解锁锁屏等能力，是否前往下载？")
            .setPositiveButton("去下载") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.REPO_RELEASES_URL)))
                } catch (_: Exception) {
                }
            }
            .setNegativeButton("暂不", null)
            .show()
    }

    private fun readModuleVersion(): String? {
        if (!moduleVersionLoaded) {
            refreshModuleVersionAsync()
            return null
        }
        return cachedModuleVersion
    }

    private fun readModuleVersionBlocking(): String? {
        return try {
            val r = Shell.cmd("cat /data/adb/modules/autotask/module.prop").exec()
            val v = r.out.firstOrNull { it.startsWith("version=") }
                ?.removePrefix("version=")?.trim()?.removePrefix("v")?.ifBlank { null }
            cachedModuleVersion = v
            moduleVersionLoaded = true
            v
        } catch (e: Exception) {
            moduleVersionLoaded = true
            null
        }
    }

    private fun refreshModuleVersionAsync() {
        Thread {
            readModuleVersionBlocking()
            runOnUiThread {
                updateStatus()
                checkAndPromptEnhancedModule()
            }
        }.start()
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
                itemView.setOnLongClickListener {
                    val copy = task.copy(id = TaskStore.nextId(this@MainActivity), name = task.name + "（副本）", enabled = true)
                    TaskStore.add(this@MainActivity, copy)
                    TaskScheduler.schedule(this@MainActivity, copy)
                    refresh()
                    toast("已复制任务「${task.name}」")
                    true
                }
            }
        }
    }
}
