package com.example.autotask

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object TaskStore {
    private const val PREFS = "tasks"
    private const val KEY = "task_list"
    private const val KEY_NEXT_ID = "next_id"

    private fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun nextId(c: Context): Int {
        val p = prefs(c)
        val id = p.getInt(KEY_NEXT_ID, 1)
        p.edit().putInt(KEY_NEXT_ID, id + 1).apply()
        return id
    }

    fun getAll(c: Context): List<Task> {
        val s = prefs(c).getString(KEY, null) ?: return emptyList()
        val arr = try {
            JSONArray(s)
        } catch (e: Exception) {
            return emptyList()
        }
        val list = mutableListOf<Task>()
        for (i in 0 until arr.length()) {
            try {
                list.add(fromJson(arr.getJSONObject(i)))
            } catch (_: Exception) {
            }
        }
        return list.sortedBy { it.hour * 3600 + it.minute * 60 + it.second }
    }

    fun get(c: Context, id: Int): Task? = getAll(c).find { it.id == id }

    fun add(c: Context, task: Task) {
        val list = getAll(c).toMutableList()
        list.add(task)
        save(c, list)
    }

    fun update(c: Context, task: Task) {
        val list = getAll(c).toMutableList()
        val idx = list.indexOfFirst { it.id == task.id }
        if (idx >= 0) list[idx] = task else list.add(task)
        save(c, list)
    }

    fun delete(c: Context, id: Int) {
        save(c, getAll(c).filter { it.id != id })
    }

    private fun save(c: Context, list: List<Task>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        prefs(c).edit().putString(KEY, arr.toString()).apply()
    }

    private fun toJson(t: Task): JSONObject = JSONObject().apply {
        put("id", t.id)
        put("name", t.name)
        put("hour", t.hour)
        put("minute", t.minute)
        put("second", t.second)
        put("type", t.type.name)
        put("repeat", t.repeat.name)
        put("enabled", t.enabled)
        put("x", t.x)
        put("y", t.y)
        put("x2", t.x2)
        put("y2", t.y2)
        put("duration", t.duration)
        put("keyAction", t.keyAction.name)
        put("packageName", t.packageName)
        put("url", t.url)
        put("message", t.message)
        put("weekdays", t.weekdays)
        put("wakeScreen", t.wakeScreen)
    }

    private fun fromJson(o: JSONObject): Task = Task(
        id = o.getInt("id"),
        name = o.optString("name", "任务"),
        hour = o.getInt("hour"),
        minute = o.getInt("minute"),
        second = o.optInt("second", 0),
        type = TaskType.valueOf(o.getString("type")),
        repeat = RepeatMode.valueOf(o.optString("repeat", RepeatMode.DAILY.name)),
        enabled = o.optBoolean("enabled", true),
        x = o.optInt("x", 0),
        y = o.optInt("y", 0),
        x2 = o.optInt("x2", 0),
        y2 = o.optInt("y2", 0),
        duration = o.optInt("duration", 500),
        keyAction = runCatching { KeyAction.valueOf(o.optString("keyAction", KeyAction.HOME.name)) }.getOrDefault(KeyAction.HOME),
        packageName = o.optString("packageName", ""),
        url = o.optString("url", ""),
        message = o.optString("message", ""),
        weekdays = o.optInt("weekdays", 0),
        wakeScreen = o.optBoolean("wakeScreen", true)
    )
}
