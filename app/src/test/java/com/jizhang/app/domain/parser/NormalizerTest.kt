package com.jizhang.app.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizerTest {

    @Test
    fun fullwidthYuanAndPunctuationBecomeHalfwidth() {
        assertEquals("¥15.00, 测试:OK", Normalizer.normalize("￥15．00， 测试：OK"))
    }

    @Test
    fun fullwidthDigitsBecomeHalfwidth() {
        assertEquals("¥1500.50", Normalizer.normalize("￥１５００．５０"))
    }

    @Test
    fun fullwidthSpaceBecomesHalfwidth() {
        assertEquals("a b", Normalizer.normalize("a　b"))
    }

    @Test
    fun halfwidthTextUnchanged() {
        assertEquals("ABC 123", Normalizer.normalize("ABC 123"))
    }
}
