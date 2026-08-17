package com.jizhang.app.domain.dedup

import com.jizhang.app.domain.model.TransactionSource

/**
 * 去重键生成：
 * - 通知：包名 + 5 秒时间窗 + 金额 + 商户。
 *   同一笔支付推送多条通知（支付成功 + 支付凭证）发布时间在同一秒级窗口 → 去重；
 *   连续两笔同金额同商户（如两杯 15 元咖啡）发布时间相差 > 5s → 不误杀。
 * - CSV：交易单号优先（唯一锚点）；无单号回退 时间窗 + 金额 + 对方。
 * - 手动：时间窗 + 金额 + 备注。
 */
object DedupGuard {

    private const val NOTIFICATION_WINDOW_MS = 5_000L
    private const val CSV_WINDOW_MS = 60_000L

    fun notificationKey(
        packageName: String,
        postedTimeMs: Long,
        amountCents: Long,
        merchant: String?,
    ): String = "notif|$packageName|${postedTimeMs / NOTIFICATION_WINDOW_MS}|$amountCents|${merchant ?: ""}"

    fun csvKey(
        source: TransactionSource,
        orderId: String?,
        transactionTimeMs: Long,
        amountCents: Long,
        merchant: String?,
    ): String =
        if (!orderId.isNullOrBlank()) {
            "csv|$source|$orderId"
        } else {
            "csv|$source|${transactionTimeMs / CSV_WINDOW_MS}|$amountCents|${merchant ?: ""}"
        }

    fun manualKey(
        transactionTimeMs: Long,
        amountCents: Long,
        note: String?,
    ): String = "manual|${transactionTimeMs / CSV_WINDOW_MS}|$amountCents|${note ?: ""}"
}
