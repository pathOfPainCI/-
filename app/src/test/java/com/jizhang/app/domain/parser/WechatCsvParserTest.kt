package com.jizhang.app.domain.parser

import com.jizhang.app.domain.model.TransactionSource
import com.jizhang.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WechatCsvParserTest {

    private val csv = """
        微信支付账单明细,,,,,,,,
        ,,,,,,,,
        交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号
        2026-01-15 12:30:45,商户消费,瑞幸咖啡,拿铁,支出,¥15.00,零钱,支付成功,20260115123045001
        2026-01-16 08:00:00,微信红包,张三,,收入,¥8.80,零钱,已存入零钱,20260116080000002
        2026-01-17 09:00:00,商户消费,坏数据店,商品,支出,金额错误,零钱,支付成功,20260117090000003
        2026-01-17 09:30:00,商户消费,失败店,商品,支出,¥20.00,零钱,交易失败,20260117093000004
    """.trimIndent()

    @Test
    fun parsesValidRowsAndSkipsBadOnes() {
        val r = WechatCsvParser.parse(csv)

        assertNull(r.error)
        assertEquals(2, r.transactions.size)
        assertEquals(2, r.skippedLines)

        val first = r.transactions[0]
        assertEquals(1500L, first.amountCents)
        assertEquals(TransactionType.EXPENSE, first.type)
        assertEquals("瑞幸咖啡", first.merchant)
        assertEquals("20260115123045001", first.orderId)
        assertEquals(TransactionSource.WECHAT_CSV, first.source)
        assertEquals(2026, java.time.Instant.ofEpochMilli(first.transactionTimeMs)
            .atZone(java.time.ZoneId.systemDefault()).year)

        val second = r.transactions[1]
        assertEquals(880L, second.amountCents)
        assertEquals(TransactionType.INCOME, second.type)
        assertEquals("张三", second.merchant)
    }

    @Test
    fun neutralRowBecomesNeutral() {
        val csvNeutral = """
            交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号
            2026-01-15 12:30:45,零钱提现,微信零钱,,中性交易,¥100.00,零钱,已到账,20260115123045005
        """.trimIndent()
        val r = WechatCsvParser.parse(csvNeutral)
        assertEquals(1, r.transactions.size)
        assertEquals(TransactionType.NEUTRAL, r.transactions[0].type)
        assertEquals(10000L, r.transactions[0].amountCents)
    }

    @Test
    fun missingHeaderReturnsError() {
        val r = WechatCsvParser.parse("随便几行
没有表头")
        assertTrue(r.error != null)
        assertTrue(r.transactions.isEmpty())
    }

    @Test
    fun thousandsSeparatorParsed() {
        val csvBig = """
            交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号
            2026-02-01 10:00:00,商户消费,大额店,电脑,支出,¥1,200.50,银行卡,支付成功,20260201100000006
        """.trimIndent()
        val r = WechatCsvParser.parse(csvBig)
        assertEquals(120050L, r.transactions[0].amountCents)
    }
}
