package com.jizhang.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jizhang.app.data.repo.TransactionRepository
import com.jizhang.app.domain.parser.NotificationParser
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 通知监听（自动采集主通道）：过滤微信/支付宝包名 → 合并全部文本字段 →
 * 归一化 + 正则解析 → 去重 → 分类 → 入库。
 * 授权方式：系统设置 → 通知使用权（只能用户手动开启，见引导页/设置页）。
 */
@AndroidEntryPoint
class NotificationMonitorService : NotificationListenerService() {

    @Inject lateinit var repository: TransactionRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        val pkg = sbn.packageName
        if (pkg != TransactionRepository.WECHAT_PACKAGE && pkg != TransactionRepository.ALIPAY_PACKAGE) {
            return
        }

        val text = extractText(sbn.notification)
        if (text.isNullOrBlank()) return

        val parseResult = NotificationParser.parse(text)
        // 噪声通知（积分/还款/验证码等）直接丢弃
        if (parseResult.amountCents == null && parseResult.reason == "疑似非支付类通知") return

        scope.launch {
            repository.handleNotification(pkg, sbn.postTime, sbn.key, text, parseResult)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // 国产 ROM 杀后台导致系统解绑：申请重绑（文档 §8/§10 第二层应对）
        requestRebind()
    }

    /** 合并通知全部文本字段（金额/商户常散落在不同字段） */
    private fun extractText(notification: Notification): String? {
        val extras = notification.extras ?: return null
        val parts = mutableListOf<String>()

        fun add(key: String) {
            extras.getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        }
        add(Notification.EXTRA_TITLE)
        add(Notification.EXTRA_TEXT)
        add(Notification.EXTRA_BIG_TEXT)
        add(Notification.EXTRA_SUB_TEXT)
        add(Notification.EXTRA_SUMMARY_TEXT)

        // EXTRA_TEXT_LINES 为数组，部分 ROM 类型不同，容错处理
        try {
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if (lines != null) {
                for (line in lines) {
                    line?.toString()?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
                }
            }
        } catch (e: Exception) {
            // 忽略：个别 ROM 该字段类型异常
        }

        return parts.joinToString("\n").takeIf { it.isNotBlank() }
    }
}
