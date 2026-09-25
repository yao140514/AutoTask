package com.example.autotask

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AddEditTaskActivity : AppCompatActivity() {

    private var editingId = -1
    private var x = 0
    private var y = 0
    private var x2 = 0
    private var y2 = 0
    private var packageName = ""

    // 类型顺序与 strings.xml 的 task_types 一致
    private val typeList = arrayOf(
        TaskType.CLICK, TaskType.SWIPE, TaskType.LONG_PRESS,
        TaskType.OPEN_APP, TaskType.OPEN_URL, TaskType.KEY_EVENT,
        TaskType.LOCK_SCREEN, TaskType.NOTIFY
    )

    private lateinit var nameInput: EditText
    private lateinit var timePicker: TimePicker
    private lateinit var secondPicker: NumberPicker
    private lateinit var typeSpinner: Spinner
    private lateinit var repeatSpinner: Spinner
    private lateinit var wakeSwitch: Switch

    private lateinit var clickSection: LinearLayout
    private lateinit var tvCoord: TextView
    private lateinit var btnPick: Button

    private lateinit var swipeSection: LinearLayout
    private lateinit var tvSwipeStart: TextView
    private lateinit var tvSwipeEnd: TextView
    private lateinit var btnPickStart: Button
    private lateinit var btnPickEnd: Button
    private lateinit var etSwipeDuration: EditText

    private lateinit var longPressSection: LinearLayout
    private lateinit var tvLongCoord: TextView
    private lateinit var btnPickLong: Button
    private lateinit var etLongDuration: EditText

    private lateinit var keyEventSection: LinearLayout
    private lateinit var keyActionSpinner: Spinner

    private lateinit var appSection: LinearLayout
    private lateinit var tvApp: TextView
    private lateinit var btnPickApp: Button

    private lateinit var urlSection: LinearLayout
    private lateinit var etUrl: EditText

    private lateinit var notifySection: LinearLayout
    private lateinit var etMessage: EditText

    private lateinit var lockSection: TextView

    private lateinit var weeklySection: LinearLayout
    private lateinit var cbMon: CheckBox
    private lateinit var cbTue: CheckBox
    private lateinit var cbWed: CheckBox
    private lateinit var cbThu: CheckBox
    private lateinit var cbFri: CheckBox
    private lateinit var cbSat: CheckBox
    private lateinit var cbSun: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit)
        title = if (intent.getIntExtra("id", -1) > 0) "编辑任务" else "新建任务"

        nameInput = findViewById(R.id.etName)
        timePicker = findViewById(R.id.timePicker)
        secondPicker = findViewById(R.id.secondPicker)
        typeSpinner = findViewById(R.id.typeSpinner)
        repeatSpinner = findViewById(R.id.repeatSpinner)
        wakeSwitch = findViewById(R.id.switchWake)

        clickSection = findViewById(R.id.clickSection)
        tvCoord = findViewById(R.id.tvCoord)
        btnPick = findViewById(R.id.btnPick)

        swipeSection = findViewById(R.id.swipeSection)
        tvSwipeStart = findViewById(R.id.tvSwipeStart)
        tvSwipeEnd = findViewById(R.id.tvSwipeEnd)
        btnPickStart = findViewById(R.id.btnPickStart)
        btnPickEnd = findViewById(R.id.btnPickEnd)
        etSwipeDuration = findViewById(R.id.etSwipeDuration)

        longPressSection = findViewById(R.id.longPressSection)
        tvLongCoord = findViewById(R.id.tvLongCoord)
        btnPickLong = findViewById(R.id.btnPickLong)
        etLongDuration = findViewById(R.id.etLongDuration)

        keyEventSection = findViewById(R.id.keyEventSection)
        keyActionSpinner = findViewById(R.id.keyActionSpinner)

        appSection = findViewById(R.id.appSection)
        tvApp = findViewById(R.id.tvApp)
        btnPickApp = findViewById(R.id.btnPickApp)

        urlSection = findViewById(R.id.urlSection)
        etUrl = findViewById(R.id.etUrl)

        notifySection = findViewById(R.id.notifySection)
        etMessage = findViewById(R.id.etMessage)

        lockSection = findViewById(R.id.lockSection)

        weeklySection = findViewById(R.id.weeklySection)
        cbMon = findViewById(R.id.cbMon)
        cbTue = findViewById(R.id.cbTue)
        cbWed = findViewById(R.id.cbWed)
        cbThu = findViewById(R.id.cbThu)
        cbFri = findViewById(R.id.cbFri)
        cbSat = findViewById(R.id.cbSat)
        cbSun = findViewById(R.id.cbSun)

        val btnSave = findViewById<Button>(R.id.btnSave)

        timePicker.setIs24HourView(true)
        secondPicker.minValue = 0
        secondPicker.maxValue = 59

        setupSpinners()

        btnPick.setOnClickListener { pickPoint { px, py -> x = px; y = py; updateCoordTexts() } }
        btnPickStart.setOnClickListener { pickPoint { px, py -> x = px; y = py; updateCoordTexts() } }
        btnPickEnd.setOnClickListener { pickPoint { px, py -> x2 = px; y2 = py; updateCoordTexts() } }
        btnPickLong.setOnClickListener { pickPoint { px, py -> x = px; y = py; updateCoordTexts() } }
        btnPickApp.setOnClickListener { showAppPicker() }
        btnSave.setOnClickListener { save() }

        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateSections()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        repeatSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                weeklySection.visibility =
                    if (currentRepeat() == RepeatMode.WEEKLY) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        editingId = intent.getIntExtra("id", -1)
        if (editingId > 0) {
            TaskStore.get(this, editingId)?.let { loadTask(it) }
        } else {
            updateSections()
            weeklySection.visibility = View.GONE
        }
    }

    private fun setupSpinners() {
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, resources.getStringArray(R.array.task_types))
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = typeAdapter

        val keyAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, resources.getStringArray(R.array.key_actions))
        keyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        keyActionSpinner.adapter = keyAdapter

        val repeatAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, resources.getStringArray(R.array.repeat_modes))
        repeatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        repeatSpinner.adapter = repeatAdapter
    }

    private fun currentType(): TaskType = typeList[typeSpinner.selectedItemPosition]
    private fun currentRepeat(): RepeatMode = RepeatMode.values()[repeatSpinner.selectedItemPosition]

    private fun loadTask(t: Task) {
        nameInput.setText(t.name)
        timePicker.hour = t.hour
        timePicker.minute = t.minute
        secondPicker.value = t.second
        x = t.x; y = t.y; x2 = t.x2; y2 = t.y2
        packageName = t.packageName
        etUrl.setText(t.url)
        etMessage.setText(t.message)
        etSwipeDuration.setText(t.duration.toString())
        etLongDuration.setText(t.duration.toString())
        typeSpinner.setSelection(typeList.indexOf(t.type))
        keyActionSpinner.setSelection(t.keyAction.ordinal)
        repeatSpinner.setSelection(t.repeat.ordinal)
        setWeekdays(t.weekdays)
        wakeSwitch.isChecked = t.wakeScreen
        appTextRefresh()
        updateCoordTexts()
        updateSections()
    }

    private fun updateSections() {
        val type = currentType()
        clickSection.visibility = if (type == TaskType.CLICK) View.VISIBLE else View.GONE
        swipeSection.visibility = if (type == TaskType.SWIPE) View.VISIBLE else View.GONE
        longPressSection.visibility = if (type == TaskType.LONG_PRESS) View.VISIBLE else View.GONE
        keyEventSection.visibility = if (type == TaskType.KEY_EVENT) View.VISIBLE else View.GONE
        appSection.visibility = if (type == TaskType.OPEN_APP) View.VISIBLE else View.GONE
        urlSection.visibility = if (type == TaskType.OPEN_URL) View.VISIBLE else View.GONE
        notifySection.visibility = if (type == TaskType.NOTIFY) View.VISIBLE else View.GONE
        lockSection.visibility = if (type == TaskType.LOCK_SCREEN) View.VISIBLE else View.GONE
    }

    private fun updateCoordTexts() {
        tvCoord.text = "X: $x　Y: $y"
        tvLongCoord.text = "X: $x　Y: $y"
        tvSwipeStart.text = "起点: $x, $y"
        tvSwipeEnd.text = "终点: $x2, $y2"
    }

    private fun appTextRefresh() {
        tvApp.text = appLabel(packageName)
    }

    private fun pickPoint(callback: (Int, Int) -> Unit) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        CoordinatePickerService.start(this) { px, py ->
            runOnUiThread { callback(px, py) }
        }
        Toast.makeText(this, "请在屏幕上点击目标位置", Toast.LENGTH_LONG).show()
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
                appTextRefresh()
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

    private fun readWeekdays(): Int {
        var mask = 0
        if (cbMon.isChecked) mask = mask or (1 shl 1)
        if (cbTue.isChecked) mask = mask or (1 shl 2)
        if (cbWed.isChecked) mask = mask or (1 shl 3)
        if (cbThu.isChecked) mask = mask or (1 shl 4)
        if (cbFri.isChecked) mask = mask or (1 shl 5)
        if (cbSat.isChecked) mask = mask or (1 shl 6)
        if (cbSun.isChecked) mask = mask or (1 shl 0)
        return mask
    }

    private fun setWeekdays(mask: Int) {
        cbMon.isChecked = mask and (1 shl 1) != 0
        cbTue.isChecked = mask and (1 shl 2) != 0
        cbWed.isChecked = mask and (1 shl 3) != 0
        cbThu.isChecked = mask and (1 shl 4) != 0
        cbFri.isChecked = mask and (1 shl 5) != 0
        cbSat.isChecked = mask and (1 shl 6) != 0
        cbSun.isChecked = mask and (1 shl 0) != 0
    }

    private fun save() {
        val name = nameInput.text.toString().trim().ifBlank { "任务" }
        val type = currentType()

        when (type) {
            TaskType.CLICK, TaskType.LONG_PRESS ->
                if (x == 0 && y == 0) {
                    Toast.makeText(this, "请先「取点」设置坐标", Toast.LENGTH_SHORT).show(); return
                }
            TaskType.SWIPE ->
                if ((x == 0 && y == 0) || (x2 == 0 && y2 == 0)) {
                    Toast.makeText(this, "请设置滑动起点和终点", Toast.LENGTH_SHORT).show(); return
                }
            TaskType.OPEN_APP ->
                if (packageName.isBlank()) {
                    Toast.makeText(this, "请选择要打开的应用", Toast.LENGTH_SHORT).show(); return
                }
            TaskType.OPEN_URL ->
                if (etUrl.text.toString().trim().isBlank()) {
                    Toast.makeText(this, "请输入链接", Toast.LENGTH_SHORT).show(); return
                }
            else -> {}
        }

        val repeat = currentRepeat()
        if (repeat == RepeatMode.WEEKLY && readWeekdays() == 0) {
            Toast.makeText(this, "请至少选择一个星期几", Toast.LENGTH_SHORT).show(); return
        }

        val swipeDuration = etSwipeDuration.text.toString().trim().toIntOrNull() ?: 500
        val longDuration = etLongDuration.text.toString().trim().toIntOrNull() ?: 800
        val duration = if (type == TaskType.SWIPE) swipeDuration else longDuration

        val task = Task(
            id = if (editingId > 0) editingId else TaskStore.nextId(this),
            name = name,
            hour = timePicker.hour,
            minute = timePicker.minute,
            second = secondPicker.value,
            type = type,
            repeat = repeat,
            enabled = true,
            x = x, y = y,
            x2 = x2, y2 = y2,
            duration = duration,
            keyAction = KeyAction.values()[keyActionSpinner.selectedItemPosition],
            packageName = packageName,
            url = etUrl.text.toString().trim(),
            message = etMessage.text.toString().trim(),
            weekdays = if (repeat == RepeatMode.WEEKLY) readWeekdays() else 0,
            wakeScreen = wakeSwitch.isChecked
        )
        TaskStore.update(this, task)
        TaskScheduler.schedule(this, task)
        Toast.makeText(this, "已保存，将在 ${TaskScheduler.nextTriggerText(task)} 执行", Toast.LENGTH_SHORT).show()
        finish()
    }

    data class AppInfo(val label: String, val pkg: String)
}
