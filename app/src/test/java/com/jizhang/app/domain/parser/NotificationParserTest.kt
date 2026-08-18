package com.jizhang.app.domain.parser

import com.jizhang.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 解析基线样本。注意：微信/支付宝通知文案无官方公开资料，
 * 需在目标真机上 dump 真实通知后持续补充/修正样本。
 */
class NotificationParserTest {

    // ---- 支出 ----
    @Test
    fun wechatExpenseWithYuanPrefix() {
        val r = NotificationParser.parse("微信支付凭证 你已向瑞幸咖啡付款 ¥15.00")
        assertEquals(1500L, r.amountCents)
        assertEquals(Direction.EXPENSE, r.direction)
        assertEquals("瑞幸咖啡", r.merchant)
        assertTrue(!r.needsReview)
    }

    @Test
    fun wechatExpensePlain() {
        val r = NotificationParser.parse("你已付款¥15.00")
        assertEquals(1500L, r.amountCents)
        assertEquals(Direction.EXPENSE, r.direction)
    }

    @Test
    fun alipayExpenseWithYuanSuffix() {
        val r = NotificationParser.parse("你向全家便利店付款12.50元")
        assertEquals(1250L, r.amountCents)
        assertEquals(Direction.EXPENSE, r.direction)
        assertEquals("全家便利店", r.merchant)
    }

    @Test
    fun fullwidthAmountNormalized() {
        val r = NotificationParser.parse("向测试商户付款￥12．50")
        assertEquals(1250L, r.amountCents)
        assertEquals(Direction.EXPENSE, r.direction)
        assertEquals("测试商户", r.merchant)
    }

    @Test
    fun transferOutIsExpense() {
        val r = NotificationParser.parse("你向张三转账 ¥100.00")
        assertEquals(10000L, r.amountCents)
        assertEquals(Direction.EXPENSE, r.direction)
    }

    @Test
    fun redPacketSentIsExpense() {
        val r = NotificationParser.parse("你发出了一个红包 ¥5.00")
        assertEquals(500L, r.amountCents)
        assertEquals(Direction.EXPENSE, r.direction)
    }

    // ---- 收入 ----
    @Test
    fun wechatIncome() {
        val r = NotificationParser.parse("微信支付 收款到账 ¥10.00")
        assertEquals(1000L, r.amountCents)
        assertEquals(Direction.INCOME, r.direction)
    }

    @Test
    fun redPacketReceivedIsIncome() {
        val r = NotificationParser.parse("你收到一个红包 ¥0.01")
        assertEquals(1L, r.amountCents)
        assertEquals(Direction.INCOME, r.direction)
    }

    @Test
    fun transferReceivedIsIncome() {
        val r = NotificationParser.parse("收到转账 ¥88.00")
        assertEquals(8800L, r.amountCents)
        assertEquals(Direction.INCOME, r.direction)
    }

    @Test
    fun alipayIncomeWithMerchant() {
        val r = NotificationParser.parse("收款到账 50.00元 来自 李四")
        assertEquals(5000L, r.amountCents)
        assertEquals(Direction.INCOME, r.direction)
    }

    @Test
    fun wealthManagementIncome() {
        val r = NotificationParser.parse("零钱通收益到账 ¥0.35")
        assertEquals(35L, r.amountCents)
        assertEquals(Direction.INCOME, r.direction)
    }

    @Test
    fun refundNotificationIsRefund() {
        val r = NotificationParser.parse("微信支付 退款到账 ¥15.00")
        assertEquals(1500L, r.amountCents)
        assertEquals(Direction.INCOME, r.direction)
        assertEquals(TransactionType.REFUND, r.type)
    }

    // ---- 噪声/失败 ----
    @Test
    fun pointsNotificationIsNoise() {
        val r = NotificationParser.parse("您有1条新的积分提醒")
        assertNull(r.amountCents)
        assertNotNull(r.reason)
    }

    @Test
    fun huabeiRepaymentReminderIsNoise() {
        val r = NotificationParser.parse("花呗还款提醒：本月应还 ¥500.00")
        assertNull(r.amountCents)
    }

    @Test
    fun failedPaymentIsNoise() {
        val r = NotificationParser.parse("支付失败，请重试")
        assertNull(r.amountCents)
    }

    // ---- 解析失败 ----
    @Test
    fun noAmountMeansReviewNeeded() {
        val r = NotificationParser.parse("交易成功")
        assertNull(r.amountCents)
        assertTrue(r.needsReview)
    }

    @Test
    fun blankTextMeansReviewNeeded() {
        val r = NotificationParser.parse("")
        assertNull(r.amountCents)
        assertTrue(r.needsReview)
    }

    // ---- 金额精度 ----
    @Test
    fun amountCentsNoFloatError() {
        assertEquals(1990L, NotificationParser.parseAmountCents("19.9"))
        assertEquals(1999L, NotificationParser.parseAmountCents("19.99"))
        assertEquals(100L, NotificationParser.parseAmountCents("1"))
        assertEquals(100050L, NotificationParser.parseAmountCents("1000.50"))
    }
}
