package com.example.autotask

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AddEditTaskActivity : AppCompatActivity() {

    private var editingId = -1
    private var x = 0
    private var y = 0
    private var packageName = ""

    private lateinit var nameInput: EditText
    private lateinit var timePicker: TimePicker
    private lateinit var secondPicker: NumberPicker
    private lateinit var typeGroup: RadioGroup
    private lateinit var repeatGroup: RadioGroup
    private lateinit var wakeSwitch: Switch
    private lateinit var clickSection: LinearLayout
    private lateinit var coordText: TextView
    private lateinit var appSection: LinearLayout
    private lateinit var appText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit)
        title = if (intent.getIntExtra("id", -1) > 0) "编辑任务" else "新建任务"

        nameInput = findViewById(R.id.etName)
        timePicker = findViewById(R.id.timePicker)
        secondPicker = findViewById(R.id.secondPicker)
        typeGroup = findViewById(R.id.typeGroup)
        repeatGroup = findViewById(R.id.repeatGroup)
        wakeSwitch = findViewById(R.id.switchWake)
        clickSection = findViewById(R.id.clickSection)
        coordText = findViewById(R.id.tvCoord)
        appSection = findViewById(R.id.appSection)
        appText = findViewById(R.id.tvApp)
        val btnPick = findViewById<Button>(R.id.btnPick)
        val btnPickApp = findViewById<Button>(R.id.btnPickApp)
        val btnSave = findViewById<Button>(R.id.btnSave)

        timePicker.setIs24HourView(true)
        secondPicker.minValue = 0
        secondPicker.maxValue = 59

        btnPick.setOnClickListener { startPicker() }
        btnPickApp.setOnClickListener { showAppPicker() }
        btnSave.setOnClickListener { save() }
        typeGroup.setOnCheckedChangeListener { _, _ -> updateSections() }

        editingId = intent.getIntExtra("id", -1)
        if (editingId > 0) {
            TaskStore.get(this, editingId)?.let { loadTask(it) }
        } else {
            typeGroup.check(R.id.radioClick)
            repeatGroup.check(R.id.radioDaily)
            updateSections()
        }
    }

    private fun loadTask(t: Task) {
        nameInput.setText(t.name)
        timePicker.hour = t.hour
        timePicker.minute = t.minute
        secondPicker.value = t.second
        x = t.x
        y = t.y
        packageName = t.packageName
        coordText.text = "X: $x　Y: $y"
        appText.text = appLabel(packageName)
        when (t.type) {
            TaskType.CLICK -> typeGroup.check(R.id.radioClick)
            TaskType.OPEN_APP -> typeGroup.check(R.id.radioApp)
            TaskType.LOCK_SCREEN -> typeGroup.check(R.id.radioLock)
        }
        repeatGroup.check(if (t.repeat == RepeatMode.DAILY) R.id.radioDaily else R.id.radioOnce)
        wakeSwitch.isChecked = t.wakeScreen
        updateSections()
    }

    private fun updateSections() {
        val type = when (typeGroup.checkedRadioButtonId) {
            R.id.radioApp -> TaskType.OPEN_APP
            R.id.radioLock -> TaskType.LOCK_SCREEN
            else -> TaskType.CLICK
        }
        clickSection.visibility = if (type == TaskType.CLICK) View.VISIBLE else View.GONE
        appSection.visibility = if (type == TaskType.OPEN_APP) View.VISIBLE else View.GONE
    }

    private fun startPicker() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        CoordinatePickerService.start(this) { px, py ->
            x = px
            y = py
            runOnUiThread { coordText.text = "X: $x　Y: $y" }
        }
        Toast.makeText(this, "请在屏幕上点击要设置的位置", Toast.LENGTH_LONG).show()
    }

    private fun showAppPicker() {
        val apps = loadApps()
        if (apps.isEmpty()) {
            Toast.makeText(this, "未找到可启动应用", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = apps.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择应用")
            .setItems(labels) { _, which ->
                packageName = apps[which].pkg
                appText.text = apps[which].label
            }
            .show()
    }

    private fun loadApps(): List<AppInfo> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull {
                val pkg = it.activityInfo.packageName
                if (pkg == this.packageName) null else AppInfo(it.loadLabel(pm).toString(), pkg)
            }
            .sortedBy { it.label }
    }

    private fun appLabel(pkg: String): String {
        if (pkg.isBlank()) return "未选择"
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    private fun save() {
        val name = nameInput.text.toString().trim().ifBlank { "任务" }
        val type = when (typeGroup.checkedRadioButtonId) {
            R.id.radioApp -> TaskType.OPEN_APP
            R.id.radioLock -> TaskType.LOCK_SCREEN
            else -> TaskType.CLICK
        }
        if (type == TaskType.CLICK && x == 0 && y == 0) {
            Toast.makeText(this, "请先「取点」设置点击坐标", Toast.LENGTH_SHORT).show()
            return
        }
        if (type == TaskType.OPEN_APP && packageName.isBlank()) {
            Toast.makeText(this, "请选择要打开的应用", Toast.LENGTH_SHORT).show()
            return
        }
        val repeat = if (repeatGroup.checkedRadioButtonId == R.id.radioDaily) RepeatMode.DAILY else RepeatMode.ONCE
        val task = Task(
            id = if (editingId > 0) editingId else TaskStore.nextId(this),
            name = name,
            hour = timePicker.hour,
            minute = timePicker.minute,
            second = secondPicker.value,
            type = type,
            repeat = repeat,
            enabled = true,
            x = x,
            y = y,
            packageName = packageName,
            wakeScreen = wakeSwitch.isChecked
        )
        TaskStore.update(this, task)
        TaskScheduler.schedule(this, task)
        Toast.makeText(this, "已保存，将在 ${TaskScheduler.nextTriggerText(task)} 执行", Toast.LENGTH_SHORT).show()
        finish()
    }

    data class AppInfo(val label: String, val pkg: String)
}
