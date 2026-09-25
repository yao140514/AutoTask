package com.example.autotask

import android.app.AlarmManager
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
import android.widget.Button
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.topjohnwu.superuser.Shell

class MainActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var statusText: TextView
    private lateinit var serviceSwitch: Switch
    private lateinit var adapter: TaskAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recycler = findViewById(R.id.recycler)
        statusText = findViewById(R.id.statusText)
        serviceSwitch = findViewById(R.id.serviceSwitch)
        val fab = findViewById<FloatingActionButton>(R.id.fab)
        val btnPermissions = findViewById<Button>(R.id.btnPermissions)

        adapter = TaskAdapter()
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        fab.setOnClickListener {
            startActivity(Intent(this, AddEditTaskActivity::class.java))
        }
        btnPermissions.setOnClickListener { checkPermissions() }

        // 前台服务开关
        serviceSwitch.isChecked = true
        serviceSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) TaskService.start(this) else TaskService.stop(this)
        }

        // 启动前台服务
        TaskService.start(this)
        // 首次启动自动检查一次权限（非强制）
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        updateStatus()
    }

    private fun refresh() {
        adapter.submit(TaskStore.getAll(this))
    }

    private fun updateStatus() {
        val root = Shell.getShell().isRoot
        val overlay = Settings.canDrawOverlays(this)
        val battery = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(packageName)
        val exact = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        } else true
        statusText.text = buildString {
            append("Root：").append(if (root) "✅ 已授权" else "❌ 未授权").append('\n')
            append("悬浮窗：").append(if (overlay) "✅" else "❌").append('\n')
            append("忽略省电：").append(if (battery) "✅" else "❌").append('\n')
            append("精确闹钟：").append(if (exact) "✅" else "❌")
        }
    }

    /** 逐个检查并跳转到缺失权限的设置页 */
    private fun checkPermissions() {
        if (!Shell.getShell().isRoot) {
            toast("请在 Magisk 中为本应用授予 Root 权限")
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("请授予悬浮窗权限（用于屏幕取点）")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            toast("请允许「忽略电池优化」，保证休眠下准点执行")
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
            private val delete: ImageButton = v.findViewById(R.id.btnDelete)

            fun bind(task: Task) {
                title.text = task.name
                subtitle.text = "${task.timeText}　${task.typeText}\n下次：${TaskScheduler.nextTriggerText(task)}"

                switch.setOnCheckedChangeListener(null)
                switch.isChecked = task.enabled
                switch.setOnCheckedChangeListener { _, checked ->
                    task.enabled = checked
                    TaskStore.update(this@MainActivity, task)
                    TaskScheduler.schedule(this@MainActivity, task)
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
