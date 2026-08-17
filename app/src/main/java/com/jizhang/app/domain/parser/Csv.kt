package com.jizhang.app.domain.parser

import com.jizhang.app.domain.model.TransactionSource
import com.jizhang.app.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CsvTransaction(
    val transactionTimeMs: Long,
    val type: TransactionType,
    val amountCents: Long,
    val merchant: String?,
    val note: String?,
    val orderId: String?,       // 交易单号（去重锚点，按字符串处理）
    val source: TransactionSource,
)

data class CsvParseResult(
    val transactions: List<CsvTransaction>,
    val skippedLines: Int,
    val error: String? = null,
)

/** RFC4180 风格 CSV 行解析（支持引号包裹、"" 转义、字段内逗号） */
object CsvLine {
    fun parseLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < line.length && line[i + 1] == '"') {
                            sb.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        sb.append(c)
                    }
                }
                c == '"' -> inQuotes = true
                c == ',' -> {
                    fields.add(sb.toString())
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        fields.add(sb.toString())
        return fields
    }
}

object CsvUtil {
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** "2024-01-15 12:30:45" → epoch ms（本地时区）；解析失败返回 null */
    fun parseDateTime(timeStr: String): Long? {
        return try {
            LocalDateTime.parse(timeStr.trim(), TIME_FORMAT)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    /** 金额字符串 → 分；去 ¥/￥ 前缀和千分位逗号 */
    fun parseAmountCents(raw: String): Long? {
        return try {
            BigDecimal(raw.trim().replace("¥", "").replace("￥", "").replace(",", ""))
                .movePointRight(2).longValueExact()
        } catch (e: Exception) {
            null
        }
    }
}
