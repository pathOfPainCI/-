package com.jizhang.app.domain.parser

import com.jizhang.app.domain.model.TransactionSource
import com.jizhang.app.domain.model.TransactionType

/**
 * 备份 CSV 解析器（App「备份导出」生成的格式）：
 * 表头：时间,类型,金额(元),商户,分类,备注,来源
 * 恢复时保留原分类（categoryName）。
 */
object BackupCsvParser {

    private val HEADER_MARK = "时间"

    fun parse(csv: String): CsvParseResult {
        val lines = csv.replace("\r\n", "\n").replace('\r', '\n')
            .split("\n")
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return CsvParseResult(emptyList(), 0, "备份文件为空")
        }
        val header = CsvLine.parseLine(lines[0]).map { it.trim() }
        if (header.firstOrNull() != HEADER_MARK || header.getOrNull(1) != "类型") {
            return CsvParseResult(emptyList(), 0, "不是本 App 导出的备份文件（表头应为：时间,类型,金额(元),商户,分类,备注,来源）")
        }

        val transactions = mutableListOf<CsvTransaction>()
        var skipped = 0

        for (line in lines.drop(1)) {
            val f = CsvLine.parseLine(line)
            val timeMs = CsvUtil.parseDateTime(f.getOrNull(0)?.trim().orEmpty())
            if (timeMs == null) {
                skipped++
                continue
            }
            val amountCents = CsvUtil.parseAmountCents(f.getOrNull(2)?.trim().orEmpty())
            if (amountCents == null) {
                skipped++
                continue
            }
            val type = when (f.getOrNull(1)?.trim()) {
                "支出" -> TransactionType.EXPENSE
                "收入" -> TransactionType.INCOME
                "退款" -> TransactionType.REFUND
                else -> TransactionType.NEUTRAL
            }
            transactions.add(
                CsvTransaction(
                    transactionTimeMs = timeMs,
                    type = type,
                    amountCents = amountCents,
                    merchant = f.getOrNull(3)?.trim()?.takeIf { it.isNotEmpty() && it != "/" },
                    note = f.getOrNull(5)?.trim()?.takeIf { it.isNotEmpty() && it != "/" },
                    orderId = null,
                    source = TransactionSource.MANUAL,
                    categoryName = f.getOrNull(4)?.trim()?.takeIf { it.isNotEmpty() && it != "/" },
                )
            )
        }
        return CsvParseResult(transactions, skipped)
    }
}
