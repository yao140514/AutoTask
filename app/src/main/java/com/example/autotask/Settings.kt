package com.example.autotask

import android.content.Context

/** 执行后端 */
enum class Backend {
    ROOT,          // 通过 Magisk Root（libsu）
    SHIZUKU,       // 通过 Shizuku（免 Root，ADB 权限）
    ACCESSIBILITY  // 通过无障碍服务（免 Root）
}

object AppSettings {
    private const val PREFS = "settings"
    private const val KEY_BACKEND = "backend"

    fun getBackend(context: Context): Backend {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BACKEND, Backend.ROOT.name) ?: Backend.ROOT.name
        return try {
            Backend.valueOf(name)
        } catch (e: Exception) {
            Backend.ROOT
        }
    }

    fun setBackend(context: Context, backend: Backend) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_BACKEND, backend.name).apply()
    }
}
