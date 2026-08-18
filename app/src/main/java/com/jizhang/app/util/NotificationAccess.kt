package com.jizhang.app.util

import android.content.Context
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * 通知监听授权检测。
 * 标准 API getEnabledListenerPackages 在部分国产 ROM（如 MIUI）上
 * 因 Settings.Secure 中组件名格式差异可能解析不出包名，
 * 因此兜底直接读取原始字符串做子串匹配。
 */
object NotificationAccess {

    private const val KEY = "enabled_notification_listeners"

    fun isEnabled(context: Context): Boolean {
        // 标准 API
        if (NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) {
            return true
        }
        // 兜底：原始字符串子串匹配（兼容 ROM 格式差异）
        val flat = rawValue(context)
        return !flat.isNullOrBlank() && flat.contains(context.packageName)
    }

    /** 系统原始记录（诊断用）：形如 "com.jizhang.app/com.jizhang.app.service.NotificationMonitorService:..." */
    fun rawValue(context: Context): String? =
        Settings.Secure.getString(context.contentResolver, KEY)
}
