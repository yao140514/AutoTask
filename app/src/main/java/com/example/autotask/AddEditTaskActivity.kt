package com.example.autotask

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Spinner
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AddEditTaskActivity : AppCompatActivity() {

    private var editingId = -1
    private val actions = mutableListOf<Action>()

    private lateinit var nameInput: EditText
    private lateinit var timePicker: TimePicker
    private lateinit var secondPicker: NumberPicker
    private lateinit var repeatSpinner: Spinner
    private lateinit var weeklySection: LinearLayout
    private lateinit var actionListContainer: LinearLayout

    private lateinit var cbMon: CheckBox
    private lateinit var cbTue: CheckBox
    private lateinit var cbWed: CheckBox
    private lateinit var cbThu: CheckBox
    private lateinit var cbFri: CheckBox
    private lateinit var cbSat: CheckBox
    private lateinit var cbSun: CheckBox

    // 动作编辑对话框状态
    private var currentDialog: AlertDialog? = null
    private var draft: Action? = null
    private var draftIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit)
        title = if (intent.getIntExtra("id", -1) > 0) "编辑任务" else "新建任务"

        nameInput = findViewById(R.id.etName)
        timePicker = findViewById(R.id.timePicker)
        secondPicker = findViewById(R.id.secondPicker)
        repeatSpinner = findViewById(R.id.repeatSpinner)
        weeklySection = findViewById(R.id.weeklySection)
        actionListContainer = findViewById(R.id.actionListContainer)
        val btnAddAction = findViewById<Button>(R.id.btnAddAction)
        val btnSave = findViewById<Button>(R.id.btnSave)

        cbMon = findViewById(R.id.cbMon); cbTue = findViewById(R.id.cbTue)
        cbWed = findViewById(R.id.cbWed); cbThu = findViewById(R.id.cbThu)
        cbFri = findViewById(R.id.cbFri); cbSat = findViewById(R.id.cbSat)
        cbSun = findViewById(R.id.cbSun)

        timePicker.setIs24HourView(true)
        secondPicker.minValue = 0
        secondPicker.maxValue = 59

        val repeatAdapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.repeat_modes)
        )
        repeatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        repeatSpinner.adapter = repeatAdapter
        repeatSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                weeklySection.visibility = if (position == 2) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnAddAction.setOnClickListener { showActionDialog(-1) }
        btnSave.setOnClickListener { save() }

        editingId = intent.getIntExtra("id", -1)
        if (editingId > 0) {
            TaskStore.get(this, editingId)?.let { loadTask(it) }
        } else {
            weeklySection.visibility = View.GONE
        }
        refreshActionList()
    }

    private fun loadTask(t: Task) {
        nameInput.setText(t.name)
        timePicker.hour = t.hour
        timePicker.minute = t.minute
        secondPicker.value = t.second
        repeatSpinner.setSelection(t.repeat.ordinal)
        setWeekdays(t.weekdays)
        actions.clear()
        actions.addAll(t.actions)
        weeklySection.visibility = if (t.repeat == RepeatMode.WEEKLY) View.VISIBLE else View.GONE
    }

    // ==================== 动作列表 ====================

    private fun refreshActionList() {
        actionListContainer.removeAllViews()
        actions.forEachIndexed { i, action ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val tv = TextView(this).apply {
                text = "${i + 1}. ${action.summary}"
                textSize = 13f
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = getDrawable(R.drawable.bg_status)
            }
            row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val editBtn = Button(this).apply { text = "编辑" }
            editBtn.setOnClickListener { showActionDialog(i) }
            row.addView(editBtn)

            val delBtn = Button(this).apply { text = "删" }
            delBtn.setOnClickListener {
                actions.removeAt(i)
                refreshActionList()
            }
            row.addView(delBtn)

            actionListContainer.addView(row)
        }
    }

    // ==================== 动作编辑对话框 ====================

    private fun showActionDialog(index: Int) {
        draftIndex = index
        draft = if (index >= 0) actions[index].copy() else Action()
        buildActionDialog()
    }

    private fun buildActionDialog() {
        val d = draft ?: return
        val index = draftIndex

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }

        val typeSpinner = Spinner(this)
        val typeAdapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            ActionType.values().map { it.label }
        )
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = typeAdapter
        typeSpinner.setSelection(d.type.ordinal)
        container.addView(typeSpinner)

        val paramsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(paramsBox)

        fun rebuildParams() {
            paramsBox.removeAllViews()
            val t = ActionType.values()[typeSpinner.selectedItemPosition]
            when (t) {
                ActionType.CLICK -> {
                    paramsBox.addView(coordText("坐标: ${d.x}, ${d.y}", "coord"))
                    paramsBox.addView(actionButton("屏幕上取点") {
                        syncDraft(d, typeSpinner, paramsBox)
                        currentDialog?.dismiss()
                        pickPoint { px, py -> d.x = px; d.y = py; buildActionDialog() }
                    })
                }
                ActionType.SWIPE -> {
                    paramsBox.addView(coordText("起点: ${d.x}, ${d.y}", "start"))
                    paramsBox.addView(actionButton("取起点") {
                        syncDraft(d, typeSpinner, paramsBox)
                        currentDialog?.dismiss()
                        pickPoint { px, py -> d.x = px; d.y = py; buildActionDialog() }
                    })
                    paramsBox.addView(coordText("终点: ${d.x2}, ${d.y2}", "end"))
                    paramsBox.addView(actionButton("取终点") {
                        syncDraft(d, typeSpinner, paramsBox)
                        currentDialog?.dismiss()
                        pickPoint { px, py -> d.x2 = px; d.y2 = py; buildActionDialog() }
                    })
                    paramsBox.addView(input("duration", "滑动时长(ms)", d.duration.toString()))
                }
                ActionType.LONG_PRESS -> {
                    paramsBox.addView(coordText("坐标: ${d.x}, ${d.y}", "coord"))
                    paramsBox.addView(actionButton("屏幕上取点") {
                        syncDraft(d, typeSpinner, paramsBox)
                        currentDialog?.dismiss()
                        pickPoint { px, py -> d.x = px; d.y = py; buildActionDialog() }
                    })
                    paramsBox.addView(input("duration", "长按时长(ms)", d.duration.toString()))
                }
                ActionType.KEY_EVENT -> {
                    paramsBox.addView(spinner("key", KeyAction.values().map { it.label }, d.keyAction.ordinal))
                }
                ActionType.OPEN_APP -> {
                    paramsBox.addView(coordText("应用: ${appLabel(d.packageName)}", "app"))
                    paramsBox.addView(actionButton("选择应用") {
                        syncDraft(d, typeSpinner, paramsBox)
                        currentDialog?.dismiss()
                        showAppPicker { pkg -> d.packageName = pkg; buildActionDialog() }
                    })
                }
                ActionType.OPEN_URL -> paramsBox.addView(input("url", "链接地址", d.url))
                ActionType.LOCK_SCREEN -> paramsBox.addView(coordText("锁屏：到点熄灭屏幕（无需参数）", "hint"))
                ActionType.NOTIFY -> paramsBox.addView(input("message", "提醒内容", d.message))
                ActionType.SHELL -> paramsBox.addView(input("shell", "Shell 命令", d.shellCmd))
                ActionType.VOLUME -> {
                    paramsBox.addView(spinner("stream", VolumeStream.values().map { it.label }, d.volumeStream.ordinal))
                    paramsBox.addView(input("volume", "音量(0-100)", d.volume.toString()))
                }
                ActionType.DELAY -> paramsBox.addView(input("duration", "延时(ms)", d.duration.toString()))
                ActionType.SET_VAR -> {
                    paramsBox.addView(input("varname", "变量名", d.varName))
                    paramsBox.addView(input("varvalue", "变量值", d.varValue))
                }
                ActionType.CONDITION -> {
                    val condPos = when {
                        d.condition == "screen_on" -> 0
                        d.condition == "screen_off" -> 1
                        else -> 2
                    }
                    paramsBox.addView(spinner("condition", resources.getStringArray(R.array.condition_options).toList(), condPos))
                    paramsBox.addView(input("batt", "电量阈值(%)", d.condition.removePrefix("battery>").ifBlank { "50" }))
                }
            }
        }

        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = rebuildParams()
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        rebuildParams()

        currentDialog = AlertDialog.Builder(this)
            .setTitle(if (index >= 0) "编辑动作 ${index + 1}" else "添加动作")
            .setView(container)
            .setPositiveButton("确定") { _, _ ->
                syncDraft(d, typeSpinner, paramsBox)
                if (index >= 0) actions[index] = d else actions.add(d)
                refreshActionList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 从对话框控件读取值写入 draft */
    private fun syncDraft(d: Action, typeSpinner: Spinner, paramsBox: LinearLayout) {
        d.type = ActionType.values()[typeSpinner.selectedItemPosition]
        fun text(tag: String): String =
            (paramsBox.findViewWithTag(tag) as? EditText)?.text?.toString()?.trim() ?: ""

        when (d.type) {
            ActionType.SWIPE, ActionType.LONG_PRESS, ActionType.DELAY ->
                text("duration").toIntOrNull()?.let { d.duration = it.coerceAtLeast(0) }
            ActionType.KEY_EVENT ->
                (paramsBox.findViewWithTag("key") as? Spinner)?.let { d.keyAction = KeyAction.values()[it.selectedItemPosition] }
            ActionType.OPEN_URL -> d.url = text("url")
            ActionType.NOTIFY -> d.message = text("message")
            ActionType.SHELL -> d.shellCmd = text("shell")
            ActionType.VOLUME -> {
                (paramsBox.findViewWithTag("stream") as? Spinner)?.let { d.volumeStream = VolumeStream.values()[it.selectedItemPosition] }
                text("volume").toIntOrNull()?.let { d.volume = it.coerceIn(0, 100) }
            }
            ActionType.SET_VAR -> {
                d.varName = text("varname")
                d.varValue = text("varvalue")
            }
            ActionType.CONDITION -> {
                val sp = paramsBox.findViewWithTag("condition") as? Spinner
                if (sp != null) {
                    d.condition = when (sp.selectedItemPosition) {
                        0 -> "screen_on"
                        1 -> "screen_off"
                        else -> "battery>" + (text("batt").toIntOrNull() ?: 50)
                    }
                }
            }
            else -> {}
        }
    }

    // ---- 对话框控件构建辅助 ----

    private fun input(tag: String, hint: String, value: String): EditText =
        EditText(this).apply {
            this.tag = tag
            this.hint = hint
            setText(value)
        }

    private fun coordText(text: String, tag: String): TextView =
        TextView(this).apply {
            this.tag = tag
            this.text = text
            textSize = 15f
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = getDrawable(R.drawable.bg_status)
        }

    private fun actionButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }

    private fun spinner(tag: String, labels: List<String>, position: Int): Spinner =
        Spinner(this).apply {
            this.tag = tag
            val a = ArrayAdapter(this@AddEditTaskActivity, android.R.layout.simple_spinner_item, labels)
            a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            adapter = a
            setSelection(position.coerceAtLeast(0))
        }

    // ==================== 取点 / 选应用 ====================

    private fun pickPoint(callback: (Int, Int) -> Unit) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        CoordinatePickerService.start(this) { px, py ->
            runOnUiThread { callback(px, py) }
        }
        Toast.makeText(this, "先点击悬浮按钮，再点屏幕目标位置", Toast.LENGTH_LONG).show()
    }

    private fun showAppPicker(callback: (String) -> Unit) {
        val apps = loadApps()
        if (apps.isEmpty()) {
            Toast.makeText(this, "未找到可启动应用", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = apps.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择应用")
            .setItems(labels) { _, which -> callback(apps[which].pkg) }
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

    // ==================== 星期几 ====================

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

    // ==================== 保存 ====================

    private fun save() {
        val name = nameInput.text.toString().trim().ifBlank { "任务" }
        if (actions.isEmpty()) {
            Toast.makeText(this, "请至少添加一个动作", Toast.LENGTH_SHORT).show()
            return
        }
        val repeat = RepeatMode.values()[repeatSpinner.selectedItemPosition]
        if (repeat == RepeatMode.WEEKLY && readWeekdays() == 0) {
            Toast.makeText(this, "请至少选择一个星期几", Toast.LENGTH_SHORT).show()
            return
        }
        val task = Task(
            id = if (editingId > 0) editingId else TaskStore.nextId(this),
            name = name,
            hour = timePicker.hour,
            minute = timePicker.minute,
            second = secondPicker.value,
            repeat = repeat,
            enabled = true,
            weekdays = if (repeat == RepeatMode.WEEKLY) readWeekdays() else 0,
            actions = actions.toList()
        )
        TaskStore.update(this, task)
        TaskScheduler.schedule(this, task)
        Toast.makeText(this, "已保存，将在 ${TaskScheduler.nextTriggerText(task)} 执行", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    data class AppInfo(val label: String, val pkg: String)
}
