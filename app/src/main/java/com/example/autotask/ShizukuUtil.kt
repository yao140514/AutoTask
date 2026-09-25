package com.example.autotask

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * 安全的 Shizuku 调用封装。
 * Shizuku 的 checkSelfPermission() 等方法在 Shizuku 未安装/未运行时
 * 会抛出 IllegalStateException（"binder haven't been received"），
 * 这里统一先判断 pingBinder() 并 try/catch 兜底，避免闪退。
 */
object ShizukuUtil {

    /** Shizuku 是否在运行（不会抛异常） */
    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    /** 是否已获得 Shizuku 授权（不会抛异常） */
    fun isPermissionGranted(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    /** 请求 Shizuku 授权（不会抛异常） */
    fun requestPermission(code: Int): Boolean = try {
        Shizuku.requestPermission(code)
        true
    } catch (t: Throwable) {
        false
    }
}
