package com.jizhang.app.domain.parser

/** 全角 → 半角归一化（金额、商户名常混入全角字符） */
object Normalizer {
    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(
                when {
                    ch == '￥' -> '¥'
                    ch == '，' -> ','
                    ch == '．' -> '.'
                    ch == '：' -> ':'
                    ch == '；' -> ';'
                    ch == '　' -> ' '
                    ch == '－' -> '-'
                    ch.code in 0xFF01..0xFF5E -> (ch.code - 0xFEE0).toChar()
                    else -> ch
                }
            )
        }
        return sb.toString()
    }
}
