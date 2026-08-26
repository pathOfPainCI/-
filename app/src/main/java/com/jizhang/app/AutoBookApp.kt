package com.jizhang.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

@HiltAndroidApp
class AutoBookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 崩溃日志捕获：记录到 filesDir/crash.log（设置页可查看/复制）
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val file = File(filesDir, "crash.log")
                val text = Date().toString() + " | " + thread.name + "\n" + sw.toString() + "\n====\n"
                file.appendText(text)
            } catch (e: Exception) {
                // 日志写入失败不影响崩溃处理
            }
            default?.uncaughtException(thread, throwable)
        }
    }
}
