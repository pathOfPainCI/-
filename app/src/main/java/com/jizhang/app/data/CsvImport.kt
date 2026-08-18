package com.jizhang.app.data

import com.jizhang.app.domain.parser.AlipayCsvParser
import com.jizhang.app.domain.parser.CsvParseResult
import com.jizhang.app.domain.parser.WechatCsvParser
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * CSV 账单文件读取（设计文档 §6.2）：
 * - 微信：UTF-8 带 BOM / xlsx / zip 压缩包（新版微信直接给 xlsx）
 * - 支付宝：GBK
 */
object CsvFileReader {

    fun readAndDetect(bytes: ByteArray): String {
        // 1) zip 容器（微信原始附件：含 csv/xlsx/pdf；或 xlsx 本身）
        if (XlsxParser.isZip(bytes)) {
            val entries = XlsxParser.readZipEntries(bytes)
            // 优先 csv 条目
            entries.firstOrNull { it.name.endsWith(".csv", ignoreCase = true) }?.let {
                return decodeText(it.data)
            }
            // 其次 xlsx 条目
            entries.firstOrNull { it.name.endsWith(".xlsx", ignoreCase = true) }?.let {
                return XlsxParser.xlsxToCsv(it.data) ?: "无法解析 xlsx 账单"
            }
            // xlsx 本身就是 zip（没有子条目时）
            return XlsxParser.xlsxToCsv(bytes) ?: "压缩包里没有找到 CSV 或 xlsx 账单文件"
        }
        // 2) 纯文本 CSV
        return decodeText(bytes)
    }

    private fun decodeText(bytes: ByteArray): String {
        // UTF-8 BOM
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        // 严格 UTF-8 尝试
        val utf8 = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            null
        }
        if (utf8 != null) return utf8
        // GBK 兜底
        return String(bytes, Charset.forName("GBK"))
    }
}

/** 自动识别微信/支付宝账单格式 */
object CsvFormatDetector {

    fun parseAuto(csv: String): CsvParseResult {
        return when {
            csv.contains("微信支付账单明细") -> WechatCsvParser.parse(csv)
            csv.contains("支付宝交易记录明细查询") -> AlipayCsvParser.parse(csv)
            csv.contains("金额(元)") -> WechatCsvParser.parse(csv)
            csv.contains("商品说明") || csv.contains("交易订单号") -> AlipayCsvParser.parse(csv)
            else -> CsvParseResult(emptyList(), 0, "无法识别账单格式（请使用微信/支付宝导出的账单文件）")
        }
    }
}
