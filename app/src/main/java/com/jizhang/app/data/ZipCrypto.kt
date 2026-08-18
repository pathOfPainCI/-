package com.jizhang.app.data

import java.util.zip.CRC32

/** 解压密码错误 */
class ZipPasswordException(message: String) : Exception(message)

/**
 * ZipCrypto（PKWARE 传统加密）解密器。
 * 支付宝账单 zip 使用此加密：解密 = 初始化密钥 → 解密 12 字节头 → 解密数据 → CRC 校验。
 */
class ZipCryptoDecryptor(password: ByteArray) {

    private var key0 = 0x12345678L
    private var key1 = 0x23456789L
    private var key2 = 0x34567890L

    init {
        for (b in password) updateKeys(b.toInt() and 0xFF)
    }

    /** 解密一段密文（连续调用会持续推进密钥状态），返回明文 */
    fun decrypt(data: ByteArray, offset: Int, length: Int): ByteArray {
        val out = ByteArray(length)
        for (i in 0 until length) {
            val c = data[offset + i].toInt() and 0xFF
            val k = key2 or 2L
            val dec = c xor (((k * (k xor 1L)) ushr 8) and 0xFF).toInt()
            out[i] = dec.toByte()
            updateKeys(dec)
        }
        return out
    }

    private fun updateKeys(c: Int) {
        key0 = crc32Update(key0, c)
        key1 = (key1 + (key0 and 0xFF)) and 0xFFFFFFFFL
        key1 = (key1 * 134775813L + 1L) and 0xFFFFFFFFL
        key2 = crc32Update(key2, (key1 ushr 24).toInt())
    }

    private fun crc32Update(crc: Long, b: Int): Long =
        ((crc ushr 8) xor (CRC_TABLE[((crc xor b.toLong()) and 0xFF).toInt()].toLong() and 0xFFFFFFFFL)) and 0xFFFFFFFFL

    companion object {
        // CRC32 表（多项式 0xEDB88320）
        private val CRC_TABLE = IntArray(256).also { table ->
            for (n in 0 until 256) {
                var c = n
                for (i in 0 until 8) {
                    c = if (c and 1 != 0) (0xEDB88320.toInt() xor (c ushr 1)) else c ushr 1
                }
                table[n] = c
            }
        }
    }
}

/** 解压数据的 CRC32（用于校验密码是否正确） */
fun crc32Of(data: ByteArray): Long = CRC32().apply { update(data) }.value
