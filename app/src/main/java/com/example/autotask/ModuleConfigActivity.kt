package com.example.autotask

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.topjohnwu.superuser.Shell

/** 增强模块配置：在 App 里直接编辑模块的 tasks.conf（需 Root） */
class ModuleConfigActivity : AppCompatActivity() {

    private lateinit var configEdit: EditText
    private lateinit var infoText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_module_config)
        title = "模块配置"

        configEdit = findViewById(R.id.configEdit)
        infoText = findViewById(R.id.tvModuleInfo)
        val btnSave = findViewById<Button>(R.id.btnSaveConfig)
        val btnReset = findViewById<Button>(R.id.btnResetConfig)

        btnSave.setOnClickListener { saveConfig() }
        btnReset.setOnClickListener { resetConfig() }

        loadConfig()
    }

    private fun loadConfig() {
        if (!Shell.getShell().isRoot || !ModuleManager.isInstalled()) {
            infoText.text = "未检测到 Root 或增强模块未安装"
            configEdit.isEnabled = false
            return
        }
        infoText.text = "配置文件：${ModuleManager.CONFIG_PATH}"
        Thread {
            val content = ModuleManager.readConfig()
            runOnUiThread {
                configEdit.setText(content ?: ModuleManager.DEFAULT_CONFIG)
            }
        }.start()
    }

    private fun saveConfig() {
        if (!Shell.getShell().isRoot) {
            toast("需要 Root 权限")
            return
        }
        val content = configEdit.text.toString()
        Thread {
            val ok = ModuleManager.writeConfig(content)
            runOnUiThread { toast(if (ok) "已保存，模块将自动生效" else "保存失败") }
        }.start()
    }

    private fun resetConfig() {
        configEdit.setText(ModuleManager.DEFAULT_CONFIG)
        toast("已填入默认模板，请点「保存」生效")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
