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

    /** 加密压缩包标记（支付宝账单 zip 有密码，需用户先解压） */
    const val ENCRYPTED_ZIP_MARKER = "\u0000ENCRYPTED_ZIP"
    const val WRONG_PASSWORD_MARKER = "\u0000WRONG_PASSWORD"

    /** 检测 zip 传统加密（本地文件头 flags bit 0） */
    fun isEncryptedZip(bytes: ByteArray): Boolean {
        if (bytes.size < 30) return false
        if (bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
        ) {
            val flags = ((bytes[7].toInt() and 0xFF) shl 8) or (bytes[6].toInt() and 0xFF)
            return flags and 0x01 != 0
        }
        return false
    }

    fun readAndDetect(bytes: ByteArray, password: String? = null): String {
        // 0) 加密压缩包（支付宝账单）→ 无密码提示输入，有密码直接解密
        if (XlsxParser.isZip(bytes) && isEncryptedZip(bytes)) {
            if (password.isNullOrEmpty()) return ENCRYPTED_ZIP_MARKER
            return try {
                val entries = XlsxParser.readZipEntries(bytes, password)
                entries.firstOrNull { it.name.endsWith(".csv", ignoreCase = true) }?.let {
                    return decodeText(it.data)
                }
                entries.firstOrNull { it.name.endsWith(".xlsx", ignoreCase = true) }?.let {
                    return XlsxParser.xlsxToCsv(it.data) ?: "无法解析 xlsx 账单"
                }
                "压缩包里没有找到 CSV 或 xlsx 账单文件"
            } catch (e: ZipPasswordException) {
                WRONG_PASSWORD_MARKER
            }
        }
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
        if (csv == CsvFileReader.ENCRYPTED_ZIP_MARKER) {
            return CsvParseResult(
                emptyList(), 0,
                "账单压缩包已加密：请在手机文件管理里先解压（输入下载账单时设置的那个密码），得到 CSV 文件后再导入",
            )
        }
        if (csv == CsvFileReader.WRONG_PASSWORD_MARKER) {
            return CsvParseResult(emptyList(), 0, "解压密码不正确，请重新输入")
        }
        // 支付宝「记账本」导出（非交易账单）
        if (csv.contains("记录时间") && csv.contains("收支类型")) {
            return CsvParseResult(
                emptyList(), 0,
                "这是支付宝「记账本」的导出文件，不是交易账单。\n请用支付宝这样导出：我的 → 账单 → 右上角「...」→ 开具交易流水证明 → 选「用于个人对账」→ 发送到邮箱 → 解压得到 CSV 后再导入",
            )
        }
        return when {
            csv.contains("微信支付账单明细") -> WechatCsvParser.parse(csv)
            csv.contains("支付宝交易记录明细查询") -> AlipayCsvParser.parse(csv)
            csv.contains("金额(元)") -> WechatCsvParser.parse(csv)
            csv.contains("商品说明") || csv.contains("交易订单号") -> AlipayCsvParser.parse(csv)
            else -> CsvParseResult(emptyList(), 0, "无法识别账单格式（请使用微信/支付宝导出的账单文件）")
        }
    }
}
