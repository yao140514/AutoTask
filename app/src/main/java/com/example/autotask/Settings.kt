package com.example.autotask

import android.content.Context
import android.content.SharedPreferences

/** 执行后端 */
enum class Backend {
    ROOT,          // 通过 Magisk Root（libsu）
    SHIZUKU,       // 通过 Shizuku（免 Root，ADB 权限）
    ACCESSIBILITY  // 通过无障碍服务（免 Root）
}

object AppSettings {
    private const val PREFS = "settings"
    private const val KEY_BACKEND = "backend"
    private const val KEY_RECORD_ENABLED = "record_enabled"
    private const val KEY_RECORD_DIR = "record_dir"
    private const val KEY_SCREENSHOT_DIR = "screenshot_dir"

    private fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ===== 执行后端 =====

    fun getBackend(context: Context): Backend {
        val name = prefs(context).getString(KEY_BACKEND, Backend.ROOT.name) ?: Backend.ROOT.name
        return try {
            Backend.valueOf(name)
        } catch (e: Exception) {
            Backend.ROOT
        }
    }

    fun setBackend(context: Context, backend: Backend) {
        prefs(context).edit().putString(KEY_BACKEND, backend.name).apply()
    }

    // ===== 屏幕录制 =====

    fun isRecordEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RECORD_ENABLED, false)

    fun setRecordEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_RECORD_ENABLED, enabled).apply()
    }

    fun getRecordDir(context: Context): String =
        prefs(context).getString(KEY_RECORD_DIR, "/sdcard/Movies") ?: "/sdcard/Movies"

    fun setRecordDir(context: Context, dir: String) {
        prefs(context).edit().putString(KEY_RECORD_DIR, dir).apply()
    }

    // ===== 截图存放位置 =====

    fun getScreenshotDir(context: Context): String =
        prefs(context).getString(KEY_SCREENSHOT_DIR, "/sdcard/Pictures") ?: "/sdcard/Pictures"

    fun setScreenshotDir(context: Context, dir: String) {
        prefs(context).edit().putString(KEY_SCREENSHOT_DIR, dir).apply()
    }
}
