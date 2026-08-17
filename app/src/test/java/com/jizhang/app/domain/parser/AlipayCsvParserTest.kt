package com.jizhang.app.domain.parser

import com.jizhang.app.domain.model.TransactionSource
import com.jizhang.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlipayCsvParserTest {

    private val csv = """
        支付宝交易记录明细查询,,,,,,,,
        账号:[xxx]@example.com,,,,,,,,
        # 备注：仅用于对账
        # 导出时间：2026-01-31
        交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注
        2026-01-15 12:30:45,餐饮美食,瑞幸咖啡,ruixing@alipay.com,拿铁,支出,15.00,余额宝,交易成功,20260115123045001,,备注一
        2026-01-16 08:00:00,转账,张三,,转账,收入,100.00,余额,交易成功,20260116080000002,,
        2026-01-17 09:00:00,退款,淘宝网店,,退款,收入,5.00,余额,交易成功,20260117090000003,,
        2026-01-17 10:00:00,商户消费,失败店,,商品,支出,20.00,余额,交易关闭,20260117100000004,,
    """.trimIndent()

    @Test
    fun parsesValidRowsWithCommentsAndMetadataSkipped() {
        val r = AlipayCsvParser.parse(csv)

        assertNull(r.error)
        assertEquals(3, r.transactions.size)
        assertEquals(1, r.skippedLines)

        val first = r.transactions[0]
        assertEquals(1500L, first.amountCents)
        assertEquals(TransactionType.EXPENSE, first.type)
        assertEquals("瑞幸咖啡", first.merchant)
        assertEquals("备注一", first.note)
        assertEquals(TransactionSource.ALIPAY_CSV, first.source)
        assertEquals("20260115123045001", first.orderId)

        assertEquals(TransactionType.INCOME, r.transactions[1].type)
        assertEquals(10000L, r.transactions[1].amountCents)
    }

    @Test
    fun refundRowBecomesRefund() {
        val r = AlipayCsvParser.parse(csv)
        assertEquals(TransactionType.REFUND, r.transactions[2].type)
        assertEquals(500L, r.transactions[2].amountCents)
    }

    @Test
    fun goodsAsNoteFallback() {
        val csvGoods = """
            交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注
            2026-01-15 12:30:45,餐饮美食,测试店,,牛肉面,支出,18.00,余额,交易成功,20260115123045005,,
        """.trimIndent()
        val r = AlipayCsvParser.parse(csvGoods)
        assertEquals("牛肉面", r.transactions[0].note)
    }

    @Test
    fun utf8BomAtFirstLineDoesNotBreakHeaderDetection() {
        val withBom = "﻿" + csv
        val r = AlipayCsvParser.parse(withBom)
        assertEquals(3, r.transactions.size)
    }
}
