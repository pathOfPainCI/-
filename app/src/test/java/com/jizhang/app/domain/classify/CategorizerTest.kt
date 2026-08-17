package com.jizhang.app.domain.classify

import com.jizhang.app.domain.model.Rule
import com.jizhang.app.domain.model.RuleMatchType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeAi(var result: String? = "餐饮") : AiClassifier {
    var calls = 0
    override suspend fun classify(merchant: String?, note: String?): String? {
        calls++
        return result
    }
}

class CategorizerTest {

    private fun rule(category: String, match: RuleMatchType, pattern: String, priority: Int = 100) =
        Rule(categoryId = 1L, categoryName = category, matchType = match, pattern = pattern, priority = priority)

    @Test
    fun containsRuleHit() = runBlocking {
        val c = Categorizer(listOf(rule("餐饮", RuleMatchType.CONTAINS, "瑞幸")), null)
        assertEquals("餐饮", c.classify("瑞幸咖啡", null))
    }

    @Test
    fun exactMatchRuleHit() = runBlocking {
        val c = Categorizer(listOf(rule("测试", RuleMatchType.MERCHANT_EXACT, "瑞幸咖啡")), null)
        assertEquals("测试", c.classify("瑞幸咖啡", null))
        assertNull(c.classify("瑞幸咖啡北京店", null))
    }

    @Test
    fun regexRuleHit() = runBlocking {
        val c = Categorizer(listOf(rule("交通", RuleMatchType.REGEX, "滴滴|高德")), null)
        assertEquals("交通", c.classify("滴滴出行", null))
        assertNull(c.classify("瑞幸咖啡", null))
    }

    @Test
    fun higherPriorityWins() = runBlocking {
        val c = Categorizer(
            listOf(
                rule("餐饮", RuleMatchType.CONTAINS, "瑞幸", priority = 1),
                rule("咖啡", RuleMatchType.CONTAINS, "瑞幸", priority = 200),
            ),
            null,
        )
        assertEquals("咖啡", c.classify("瑞幸咖啡", null))
    }

    @Test
    fun noteIsAlsoMatched() = runBlocking {
        val c = Categorizer(listOf(rule("交通", RuleMatchType.CONTAINS, "加油")), null)
        assertEquals("交通", c.classify(null, "在加油站加油"))
    }

    @Test
    fun aiFallbackWhenNoRuleHits() = runBlocking {
        val ai = FakeAi("购物")
        val c = Categorizer(emptyList(), ai)
        assertEquals("购物", c.classify("淘宝网", "商品"))
        assertEquals(1, ai.calls)
    }

    @Test
    fun aiResultCachedByMerchant() = runBlocking {
        val ai = FakeAi("购物")
        val c = Categorizer(emptyList(), ai)
        c.classify("淘宝网", null)
        c.classify("淘宝网", null)
        assertEquals(1, ai.calls)
    }

    @Test
    fun aiNullNotCached() = runBlocking {
        val ai = FakeAi(null)
        val c = Categorizer(emptyList(), ai)
        assertNull(c.classify("神秘店", null))
        assertNull(c.classify("神秘店", null))
        assertEquals(2, ai.calls)
    }

    @Test
    fun ruleWinsOverAi() = runBlocking {
        val ai = FakeAi("购物")
        val c = Categorizer(listOf(rule("餐饮", RuleMatchType.CONTAINS, "瑞幸")), ai)
        assertEquals("餐饮", c.classify("瑞幸咖啡", null))
        assertEquals(0, ai.calls)
    }

    @Test
    fun invalidRegexDoesNotCrash() = runBlocking {
        val c = Categorizer(listOf(rule("餐饮", RuleMatchType.REGEX, "[")), null)
        assertNull(c.classify("任意商户", null))
    }
}
