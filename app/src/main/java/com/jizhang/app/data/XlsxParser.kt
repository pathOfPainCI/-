package com.jizhang.app.data

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * 微信导出的 xlsx 账单解析：xlsx → CSV 文本（行列转 CSV，复用现有 CSV 解析逻辑）。
 * xlsx 本质是 zip 包：sharedStrings.xml（共享字符串）+ worksheets/sheetN.xml（表格）。
 */
object XlsxParser {

    data class ZipEntry(val name: String, val data: ByteArray)

    /** 读取 zip 全部条目（微信压缩包 / xlsx / 支付宝加密 zip 通用） */
    fun readZipEntries(bytes: ByteArray, password: String? = null): List<ZipEntry> {
        // 用 ZipInputStream 快速失败：无加密条目时走常规路径
        val plain = mutableListOf<ZipEntry>()
        var encrypted = false
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        plain.add(ZipEntry(entry.name, zis.readBytes()))
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            return plain
        } catch (e: Exception) {
            encrypted = true
        }
        // 有加密条目：手动解析 central directory + ZipCrypto 解密
        val view = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        var eocd = -1
        for (i in bytes.size - 22 downTo 0) {
            if (view.getInt(i) == 0x06054b50) {
                eocd = i
                break
            }
        }
        if (eocd < 0) return plain
        val cdCount = view.getShort(eocd + 10).toInt() and 0xFFFF
        val cdOffset = view.getInt(eocd + 16)
        val result = mutableListOf<ZipEntry>()
        var p = cdOffset
        for (n in 0 until cdCount) {
            if (view.getInt(p) != 0x02014b50) break
            val method = view.getShort(p + 10).toInt() and 0xFFFF
            val flags = view.getShort(p + 8).toInt() and 0xFFFF
            val crc = view.getInt(p + 16).toLong() and 0xFFFFFFFFL
            val compSize = view.getInt(p + 20).toLong().toInt()
            val nameLen = view.getShort(p + 28).toInt() and 0xFFFF
            val extraLen = view.getShort(p + 30).toInt() and 0xFFFF
            val commentLen = view.getShort(p + 32).toInt() and 0xFFFF
            val localOffset = view.getInt(p + 42)
            val name = String(bytes, p + 46, nameLen, Charsets.UTF_8)

            if (!name.endsWith("/")) {
                val lNameLen = view.getShort(localOffset + 26).toInt() and 0xFFFF
                val lExtraLen = view.getShort(localOffset + 28).toInt() and 0xFFFF
                val dataStart = localOffset + 30 + lNameLen + lExtraLen
                if (dataStart + compSize <= bytes.size) {
                    val raw = bytes.copyOfRange(dataStart, dataStart + compSize)
                    if (flags and 0x01 != 0) {
                        // 加密条目：需要密码
                        if (password.isNullOrEmpty()) continue
                        val dec = ZipCryptoDecryptor(password.toByteArray(Charsets.UTF_8))
                        if (raw.size < 12) continue
                        dec.decrypt(raw, 0, 12) // 12 字节加密头
                        val plainData = dec.decrypt(raw, 12, raw.size - 12)
                        // CRC 校验：密码错误时解出的数据 CRC 不匹配
                        if (crc32Of(plainData) != crc) {
                            throw ZipPasswordException("解压密码不正确")
                        }
                        val content = if (method == 8) inflateRaw(plainData) else plainData
                        result.add(ZipEntry(name, content))
                    } else {
                        val content = if (method == 8) inflateRaw(raw) else raw
                        result.add(ZipEntry(name, content))
                    }
                }
            }
            p += 46 + nameLen + extraLen + commentLen
        }
        return result
    }

    private fun inflateRaw(data: ByteArray): ByteArray =
        java.util.zip.InflaterInputStream(java.io.ByteArrayInputStream(data)).use { it.readBytes() }

    /** 是否为 zip 容器（含 xlsx） */
    fun isZip(bytes: ByteArray): Boolean =
        bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    /** xlsx → CSV 文本；失败返回 null */
    fun xlsxToCsv(bytes: ByteArray): String? {
        val entries = readZipEntries(bytes)
        val shared = entries.firstOrNull { it.name.endsWith("sharedStrings.xml") }
            ?.let { parseSharedStrings(it.data) } ?: emptyList()
        val sheet = entries.firstOrNull { it.name.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
            ?: entries.firstOrNull { it.name.contains("worksheets") && it.name.endsWith(".xml") }
            ?: return null
        val rows = parseSheet(sheet.data, shared)
        return rowsToCsv(rows)
    }

    private fun parseSharedStrings(data: ByteArray): List<String> {
        val result = mutableListOf<String>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(data), "UTF-8")
        val sb = StringBuilder()
        var inSi = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> {
                        inSi = true
                        sb.setLength(0)
                    }
                    "t" -> if (inSi) sb.append(parser.nextText())
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "si" -> {
                        if (inSi) result.add(sb.toString())
                        inSi = false
                    }
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun parseSheet(data: ByteArray, shared: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(data), "UTF-8")

        var currentCells: MutableList<Pair<Int, String>>? = null
        var cellRef = ""
        var cellType = ""
        var cellValue: StringBuilder? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> currentCells = mutableListOf()
                    "c" -> {
                        cellRef = parser.getAttributeValue(null, "r") ?: ""
                        cellType = parser.getAttributeValue(null, "t") ?: ""
                        cellValue = StringBuilder()
                    }
                    "v" -> {
                        cellValue?.setLength(0)
                        cellValue?.append(parser.nextText())
                    }
                    "t" -> {
                        // inlineStr 的内联文本
                        cellValue?.setLength(0)
                        cellValue?.append(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "c" -> {
                        val raw = cellValue?.toString() ?: ""
                        val value = if (cellType == "s") {
                            raw.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                        } else {
                            raw
                        }
                        val col = if (cellRef.isBlank()) {
                            currentCells?.size ?: 0
                        } else {
                            colIndex(cellRef)
                        }
                        currentCells?.add(col to value)
                        cellValue = null
                    }
                    "row" -> {
                        val cells = currentCells ?: emptyList()
                        val maxCol = cells.maxOfOrNull { it.first } ?: -1
                        val row = MutableList(maxCol + 1) { "" }
                        for ((idx, v) in cells) {
                            if (idx in row.indices) row[idx] = v
                        }
                        rows.add(row)
                        currentCells = null
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    /** 列坐标 "A"→0 "B"→1 ... "AA"→26 */
    private fun colIndex(ref: String): Int {
        var idx = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') {
                idx = idx * 26 + (ch - 'A' + 1)
            } else {
                break
            }
        }
        return idx - 1
    }

    private fun rowsToCsv(rows: List<List<String>>): String {
        val sb = StringBuilder()
        for (row in rows) {
            sb.append(row.joinToString(",") { escapeCsv(it) }).append("\n")
        }
        return sb.toString()
    }

    private fun escapeCsv(v: String): String {
        return if (v.contains(',') || v.contains('"') || v.contains('\n')) {
            "\"" + v.replace("\"", "\"\"") + "\""
        } else {
            v
        }
    }
}
