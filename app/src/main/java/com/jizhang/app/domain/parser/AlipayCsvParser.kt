package com.jizhang.app.domain.parser

import com.jizhang.app.domain.model.TransactionSource
import com.jizhang.app.domain.model.TransactionType

/**
 * 支付宝账单 CSV 解析器。
 * - 前 ~24 行为元数据，'#' 开头为注释行
 * - 表头定位：首列「交易时间」
 * - 金额为纯数字（如 50.00）
 * - 收/支 列：支出 / 收入 / 不计收支
 * - 交易分类含「退款」的行记为 REFUND（冲抵语义）
 */
object AlipayCsvParser {

    private const val HEADER_MARK = "交易时间"

    private val COLUMN_ALIASES = mapOf(
        "交易时间" to "time",
        "交易分类" to "kind",
        "交易对方" to "merchant",
        "商品说明" to "goods",
        "收/支" to "direction",
        "金额" to "amount",
        "交易状态" to "status",
        "交易订单号" to "orderId",
        "备注" to "note",
    )

    fun parse(csv: String): CsvParseResult {
        val lines = csv.replace("\r\n", "\n").replace('\r', '\n')
            .split("\n")
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }

        val headerIdx = lines.indexOfFirst { line ->
            CsvLine.parseLine(line).firstOrNull()?.trim() == HEADER_MARK
        }
        if (headerIdx < 0) {
            return CsvParseResult(emptyList(), 0, "未找到表头（首列应为「交易时间」）")
        }

        val header = CsvLine.parseLine(lines[headerIdx]).map { it.trim() }
        val col = header.mapIndexedNotNull { idx, name -> COLUMN_ALIASES[name]?.let { it to idx } }.toMap()

        val iTime = col["time"] ?: return CsvParseResult(emptyList(), 0, "缺少「交易时间」列")
        val iAmount = col["amount"] ?: return CsvParseResult(emptyList(), 0, "缺少「金额」列")
        val iDir = col["direction"] ?: return CsvParseResult(emptyList(), 0, "缺少「收/支」列")
        val iStatus = col["status"]
        val iKind = col["kind"]
        val iMerchant = col["merchant"]
        val iOrderId = col["orderId"]
        val iNote = col["note"]
        val iGoods = col["goods"]

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

            val status = iStatus?.let { f.getOrNull(it)?.trim() }
            if (status != null && (status.contains("失败") || status.contains("关闭"))) {
                skipped++
                continue
            }

            val timeMs = CsvUtil.parseDateTime(timeStr) ?: run { skipped++; continue }
            val amountCents = CsvUtil.parseAmountCents(f.getOrNull(iAmount).orEmpty()) ?: run { skipped++; continue }

            val kind = iKind?.let { f.getOrNull(it)?.trim() }.orEmpty()
            val type = when {
                kind.contains("退款") -> TransactionType.REFUND
                f.getOrNull(iDir)?.trim() == "支出" -> TransactionType.EXPENSE
                f.getOrNull(iDir)?.trim() == "收入" -> TransactionType.INCOME
                else -> TransactionType.NEUTRAL // "不计收支" 及未知
            }

            transactions.add(
                CsvTransaction(
                    transactionTimeMs = timeMs,
                    type = type,
                    amountCents = amountCents,
                    merchant = iMerchant?.let { f.getOrNull(it)?.trim() }?.takeIf { it.isNotEmpty() },
                    note = iNote?.let { f.getOrNull(it)?.trim() }?.takeIf { it.isNotEmpty() }
                        ?: iGoods?.let { f.getOrNull(it)?.trim() }?.takeIf { it.isNotEmpty() }
                        ?: kind.takeIf { it.isNotEmpty() },
                    orderId = iOrderId?.let { f.getOrNull(it)?.trim() }?.takeIf { it.isNotEmpty() },
                    source = TransactionSource.ALIPAY_CSV,
                )
            )
        }

        return CsvParseResult(transactions, skipped)
    }
}
