package com.jizhang.app

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

@HiltAndroidApp
class AutoBookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 崩溃日志捕获：同时写入应用目录和公共「下载」目录（方便查看）
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val text = Date().toString() + " | " + thread.name + "\n" + sw.toString() + "\n====\n"

                // 1) 应用私有目录（设置页可查看）
                try {
                    File(filesDir, "crash.log").appendText(text)
                } catch (e: Exception) {
                }

                // 2) 公共「下载」目录（Android 10+ 无需权限）
                if (Build.VERSION.SDK_INT >= 29) {
                    try {
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, "自动记账崩溃日志.txt")
                            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                        }
                        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        uri?.let {
                            contentResolver.openOutputStream(it)?.use { os ->
                                os.write(text.toByteArray())
                            }
                        }
                    } catch (e: Exception) {
                    }
                }
            } catch (e: Exception) {
            }
            default?.uncaughtException(thread, throwable)
        }
    }
}
