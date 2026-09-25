package com.example.autotask

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 任务执行日志（存 SharedPreferences，最多保留 200 条） */
object ExecutionLog {
    private const val PREFS = "exec_log"
    private const val KEY = "log"
    private const val MAX = 200

    fun add(context: Context, msg: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val line = "[$now] $msg"
            val old = prefs.getString(KEY, "") ?: ""
            val merged = (line + "\n" + old).lineSequence().take(MAX).joinToString("\n")
            prefs.edit().putString(KEY, merged).apply()
        } catch (_: Exception) {
        }
    }

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
