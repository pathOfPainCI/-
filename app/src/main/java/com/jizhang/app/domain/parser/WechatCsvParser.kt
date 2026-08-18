package com.jizhang.app.domain.parser

import com.jizhang.app.domain.model.TransactionSource
import com.jizhang.app.domain.model.TransactionType

/**
 * 微信支付账单 CSV 解析器。
 * - 编码 UTF-8 带 BOM（首字段 BOM 残留需剥离；此处以 String 输入，由上层探测编码时处理）
 * - 前若干行为元数据，扫描首列为「交易时间」的行定位表头（不硬编码行号）
 * - 金额形如 "¥1,200.50"
 * - 收/支 列：收入 / 支出 / 中性交易
 */
object WechatCsvParser {

    private const val HEADER_MARK = "交易时间"

    private val COLUMN_ALIASES = mapOf(
        "交易时间" to "time",
        "交易类型" to "kind",
        "交易对方" to "merchant",
        "商品" to "goods",
        "收/支" to "direction",
        "金额(元)" to "amount",
        "当前状态" to "status",
        "交易单号" to "orderId",
        "备注" to "note",
    )

    fun parse(csv: String): CsvParseResult {
        val lines = csv.replace("\r\n", "\n").replace('\r', '\n')
            .split("\n")
            .filter { it.isNotBlank() }

        val headerIdx = lines.indexOfFirst { line ->
            CsvLine.parseLine(line).firstOrNull()?.trim() == HEADER_MARK
        }
        if (headerIdx < 0) {
            return CsvParseResult(emptyList(), 0, "未找到表头（首列应为「交易时间」）")
        }

        val header = CsvLine.parseLine(lines[headerIdx]).map { it.trim() }
        val col = header.mapIndexedNotNull { idx, name -> COLUMN_ALIASES[name]?.let { it to idx } }.toMap()

        val iTime = col["time"] ?: return CsvParseResult(emptyList(), 0, "缺少「交易时间」列")
        val iAmount = col["amount"] ?: return CsvParseResult(emptyList(), 0, "缺少「金额(元)」列")
        val iDir = col["direction"] ?: return CsvParseResult(emptyList(), 0, "缺少「收/支」列")
        val iStatus = col["status"]
        val iKind = col["kind"]
        val iMerchant = col["merchant"]
        val iOrderId = col["orderId"]
        val iNote = col["note"]

        val transactions = mutableListOf<CsvTransaction>()
        var skipped = 0

        for (line in lines.drop(headerIdx + 1)) {
            val f = CsvLine.parseLine(line)
            if (f.size <= iTime) {
                skipped++
                continue
            }
            val timeStr = f.getOrNull(iTime)?.trim().orEmpty()
            if (timeStr.isEmpty()) {
                skipped++
                continue
            }

            // 状态过滤：仅失败交易跳过（退款行要保留并标记 REFUND）
            val status = iStatus?.let { f.getOrNull(it)?.trim() }
            if (status != null && status.contains("失败")) {
                skipped++
                continue
            }

            val timeMs = CsvUtil.parseDateTime(timeStr)
            if (timeMs == null) {
                skipped++
                continue
            }
            val amountCents = CsvUtil.parseAmountCents(f.getOrNull(iAmount).orEmpty())
            if (amountCents == null) {
                skipped++
                continue
            }

            val kind = iKind?.let { f.getOrNull(it)?.trim() }.orEmpty()
            val type = when {
                kind.contains("退款") || (status != null && (status.contains("已退款") || status.contains("退款成功"))) ->
                    TransactionType.REFUND
                f.getOrNull(iDir)?.trim() == "收入" -> TransactionType.INCOME
                f.getOrNull(iDir)?.trim() == "支出" -> TransactionType.EXPENSE
                else -> TransactionType.NEUTRAL // "中性交易" 及未知
            }

            transactions.add(
                CsvTransaction(
                    transactionTimeMs = timeMs,
                    type = type,
                    amountCents = amountCents,
                    merchant = iMerchant?.let { f.getOrNull(it)?.trim() }?.takeIf { it.isNotEmpty() && it != "/" },
                    note = iNote?.let { f.getOrNull(it)?.trim() }?.takeIf { it.isNotEmpty() && it != "/" },
                    orderId = iOrderId?.let { f.getOrNull(it)?.trim() }?.takeIf { it.isNotEmpty() },
                    source = TransactionSource.WECHAT_CSV,
                )
            )
        }

        return CsvParseResult(transactions, skipped)
    }
}
