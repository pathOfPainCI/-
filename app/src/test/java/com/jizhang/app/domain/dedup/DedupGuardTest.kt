package com.jizhang.app.domain.dedup

import com.jizhang.app.domain.model.TransactionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DedupGuardTest {

    @Test
    fun sameWindowSameAmountSameMerchantSameKey() {
        val k1 = DedupGuard.notificationKey("com.tencent.mm", 1_700_000_000_000L, 1500L, "瑞幸咖啡")
        val k2 = DedupGuard.notificationKey("com.tencent.mm", 1_700_000_000_002L, 1500L, "瑞幸咖啡")
        assertEquals(k1, k2)
    }

    @Test
    fun beyondWindowDifferentKey() {
        // 差 10 秒 > 5 秒窗口
        val k1 = DedupGuard.notificationKey("com.tencent.mm", 1_700_000_000_000L, 1500L, "瑞幸咖啡")
        val k2 = DedupGuard.notificationKey("com.tencent.mm", 1_700_000_010_000L, 1500L, "瑞幸咖啡")
        assertNotEquals(k1, k2)
    }

    @Test
    fun differentAmountDifferentKey() {
        val k1 = DedupGuard.notificationKey("com.tencent.mm", 1_700_000_000_000L, 1500L, "瑞幸咖啡")
        val k2 = DedupGuard.notificationKey("com.tencent.mm", 1_700_000_000_001L, 1501L, "瑞幸咖啡")
        assertNotEquals(k1, k2)
    }

    @Test
    fun csvKeyUsesOrderIdWhenPresent() {
        val withId = DedupGuard.csvKey(TransactionSource.WECHAT_CSV, "ORD123", 1000L, 1500L, "瑞幸咖啡")
        val same = DedupGuard.csvKey(TransactionSource.WECHAT_CSV, "ORD123", 9999L, 9999L, "别家")
        assertEquals(withId, same)
    }

    @Test
    fun csvKeyFallsBackWithoutOrderId() {
        val a = DedupGuard.csvKey(TransactionSource.WECHAT_CSV, null, 1000L, 1500L, "瑞幸咖啡")
        val b = DedupGuard.csvKey(TransactionSource.WECHAT_CSV, null, 1000L, 1500L, "瑞幸咖啡")
        assertEquals(a, b)
        // 差 61 秒 > 60 秒窗口
        val c = DedupGuard.csvKey(TransactionSource.WECHAT_CSV, null, 61_000L, 1500L, "瑞幸咖啡")
        assertNotEquals(a, c)
    }

    @Test
    fun sourcesAreSeparated() {
        val wechat = DedupGuard.csvKey(TransactionSource.WECHAT_CSV, "ORD1", 1000L, 100L, "A")
        val alipay = DedupGuard.csvKey(TransactionSource.ALIPAY_CSV, "ORD1", 1000L, 100L, "A")
        assertNotEquals(wechat, alipay)
    }
}
