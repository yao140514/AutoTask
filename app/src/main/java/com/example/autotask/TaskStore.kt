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
                list.add(taskFromJson(arr.getJSONObject(i)))
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

    // ===== 备份 / 恢复 =====

    fun exportJson(c: Context): String {
        val arr = JSONArray()
        getAll(c).forEach { arr.put(taskToJson(it)) }
        return arr.toString()
    }

    fun importJson(c: Context, json: String): Int {
        val arr = try {
            JSONArray(json)
        } catch (e: Exception) {
            return 0
        }
        val list = getAll(c).toMutableList()
        val existingIds = list.map { it.id }.toMutableSet()
        var count = 0
        for (i in 0 until arr.length()) {
            try {
                val t = taskFromJson(arr.getJSONObject(i))
                // 保证 id 唯一
                var id = t.id
                while (id in existingIds) id = nextId(c)
                existingIds.add(id)
                list.add(t.copy(id = id))
                count++
            } catch (_: Exception) {
            }
        }
        save(c, list)
        return count
    }

    private fun save(c: Context, list: List<Task>) {
        val arr = JSONArray()
        list.forEach { arr.put(taskToJson(it)) }
        prefs(c).edit().putString(KEY, arr.toString()).apply()
    }

    // ===== 序列化 =====

    private fun taskToJson(t: Task): JSONObject = JSONObject().apply {
        put("id", t.id)
        put("name", t.name)
        put("hour", t.hour)
        put("minute", t.minute)
        put("second", t.second)
        put("repeat", t.repeat.name)
        put("enabled", t.enabled)
        put("weekdays", t.weekdays)
        put("actions", JSONArray().apply {
            t.actions.forEach { put(actionToJson(it)) }
        })
    }

    private fun taskFromJson(o: JSONObject): Task = Task(
        id = o.getInt("id"),
        name = o.optString("name", "任务"),
        hour = o.getInt("hour"),
        minute = o.getInt("minute"),
        second = o.optInt("second", 0),
        repeat = RepeatMode.valueOf(o.optString("repeat", RepeatMode.DAILY.name)),
        enabled = o.optBoolean("enabled", true),
        weekdays = o.optInt("weekdays", 0),
        actions = runCatching {
            val arr = o.getJSONArray("actions")
            (0 until arr.length()).map { actionFromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    )

    private fun actionToJson(a: Action): JSONObject = JSONObject().apply {
        put("type", a.type.name)
        put("x", a.x); put("y", a.y)
        put("x2", a.x2); put("y2", a.y2)
        put("duration", a.duration)
        put("keyAction", a.keyAction.name)
        put("packageName", a.packageName)
        put("url", a.url); put("message", a.message)
        put("shellCmd", a.shellCmd)
        put("volumeStream", a.volumeStream.name)
        put("volume", a.volume)
        put("varName", a.varName); put("varValue", a.varValue)
        put("condition", a.condition)
        put("minDelay", a.minDelay); put("maxDelay", a.maxDelay)
        put("httpMethod", a.httpMethod)
    }

    private fun actionFromJson(o: JSONObject): Action = Action(
        type = runCatching { ActionType.valueOf(o.getString("type")) }.getOrDefault(ActionType.CLICK),
        x = o.optInt("x", 0), y = o.optInt("y", 0),
        x2 = o.optInt("x2", 0), y2 = o.optInt("y2", 0),
        duration = o.optInt("duration", 500),
        keyAction = runCatching { KeyAction.valueOf(o.optString("keyAction", KeyAction.HOME.name)) }.getOrDefault(KeyAction.HOME),
        packageName = o.optString("packageName", ""),
        url = o.optString("url", ""), message = o.optString("message", ""),
        shellCmd = o.optString("shellCmd", ""),
        volumeStream = runCatching { VolumeStream.valueOf(o.optString("volumeStream", VolumeStream.MEDIA.name)) }.getOrDefault(VolumeStream.MEDIA),
        volume = o.optInt("volume", 50),
        varName = o.optString("varName", ""), varValue = o.optString("varValue", ""),
        condition = o.optString("condition", "screen_on"),
        minDelay = o.optInt("minDelay", 1000), maxDelay = o.optInt("maxDelay", 3000),
        httpMethod = o.optString("httpMethod", "GET")
    )
}
