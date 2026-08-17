package com.jizhang.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService

/** 开机 / 覆盖安装后申请重绑通知监听（系统重启后监听不会自动恢复） */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> NotificationListenerService.requestRebind(context)
        }
    }
}
