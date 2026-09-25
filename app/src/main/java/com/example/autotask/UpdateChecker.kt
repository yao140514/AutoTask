package com.example.autotask

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * 通过 GitHub API 检测最新版本。
 * 区分正式版 / 测试版（prerelease）。
 */
object UpdateChecker {

    const val REPO_RELEASES_URL = "https://github.com/yao140514/AutoTask/releases/"
    private const val API_URL = "https://api.github.com/repos/yao140514/AutoTask/releases"

    data class Release(
        val version: String,     // 去掉前缀 v 的版本号，如 "0.0.4"
        val name: String,
        val prerelease: Boolean
    ) {
        val typeLabel: String get() = if (prerelease) "测试版" else "正式版"
    }

    /** 异步检测最新版本（回调在子线程，调用方需切回主线程） */
    fun check(callback: (Release?) -> Unit) {
        Thread {
            callback(fetchLatest())
        }.start()
    }

    private fun fetchLatest(): Release? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(API_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "AutoTask")
            if (conn.responseCode != 200) return null
            val text = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optBoolean("draft", false)) continue
                return Release(
                    version = obj.optString("tag_name", "").trim().removePrefix("v"),
                    name = obj.optString("name", "").ifBlank { obj.optString("tag_name", "") },
                    prerelease = obj.optBoolean("prerelease", false)
                )
            }
            null
        } catch (e: Exception) {
            null
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /** latest 是否比 current 新 */
    fun isNewer(latest: String, current: String): Boolean = compare(latest, current) > 0

    fun compare(a: String, b: String): Int {
        val pa = a.trim().removePrefix("v").split(".")
        val pb = b.trim().removePrefix("v").split(".")
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { "0" }.toIntOrNull() ?: 0
            val y = pb.getOrElse(i) { "0" }.toIntOrNull() ?: 0
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}
