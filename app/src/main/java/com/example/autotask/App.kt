package com.example.autotask

import android.app.Application
import com.topjohnwu.superuser.Shell

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 调试时打印 libsu 日志
        Shell.enableVerboseLogging = BuildConfig.DEBUG
    }
}
