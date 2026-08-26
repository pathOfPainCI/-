package com.jizhang.app.domain.parser

import com.jizhang.app.domain.model.TransactionSource
import com.jizhang.app.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
    val categoryName: String? = null, // 备份恢复时保留原分类
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

    /**
     * "2024-01-15 12:30:45" → epoch ms（本地时区）。
     * 微信 xlsx 的时间列可能是 Excel 日期序列号（1899-12-30 起的天数，如 46215.5），
     * 纯数字且在合理年份范围（20000~80000 ≈ 1954~2119 年）时按序列号转换。
     */
    fun parseDateTime(timeStr: String): Long? {
        val trimmed = timeStr.trim()
        val serial = trimmed.toDoubleOrNull()
        if (serial != null && serial in 20000.0..80000.0) {
            return try {
                val days = serial.toLong()
                val frac = serial - days
                val date = LocalDate.of(1899, 12, 30).plusDays(days)
                val seconds = (frac * 86400.0 + 0.5).toLong().coerceIn(0, 86399)
                val time = LocalTime.ofSecondOfDay(seconds)
                date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (e: Exception) {
                null
            }
        }
        return try {
            LocalDateTime.parse(trimmed, TIME_FORMAT)
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
