package com.jizhang.app.domain.classify

import com.jizhang.app.domain.model.Rule
import com.jizhang.app.domain.model.RuleMatchType
import java.util.concurrent.ConcurrentHashMap

/** AI 分类接口：实现放 data 层（OkHttp/DeepSeek），领域层不依赖网络库 */
interface AiClassifier {
    /** 返回内置分类名（见 Categories.DEFAULT），无法分类返回 null */
    suspend fun classify(merchant: String?, note: String?): String?
}

/**
 * 分类引擎：规则优先（离线免费快）→ 商户名缓存 → AI 兜底 → null（未分类）。
 * 降级链保证 AI 失败/无 key 时绝不阻塞记账。
 */
class Categorizer(
    private val rules: List<Rule>,
    private val ai: AiClassifier?,
    private val cache: MutableMap<String, String?> = ConcurrentHashMap(),
) {

    suspend fun classify(merchant: String?, note: String?): String? {
        classifyByRules(merchant, note)?.let { return it }

        // 同商户名结果缓存（高频场景省钱省延迟）
        if (merchant != null) {
            cache[merchant]?.let { return it }
        }

        val aiResult = ai?.classify(merchant, note)
        if (aiResult != null && merchant != null) {
            cache[merchant] = aiResult
        }
        return aiResult
    }

    fun classifyByRules(merchant: String?, note: String?): String? {
        val text = listOfNotNull(merchant, note).joinToString(" ")
        return rules.sortedByDescending { it.priority }
            .firstOrNull { rule ->
                val hit = when (rule.matchType) {
                    RuleMatchType.MERCHANT_EXACT ->
                        merchant != null && merchant.equals(rule.pattern, ignoreCase = true)
                    RuleMatchType.CONTAINS -> text.contains(rule.pattern, ignoreCase = true)
                    RuleMatchType.REGEX ->
                        runCatching { Regex(rule.pattern).containsMatchIn(text) }.getOrDefault(false)
                }
                hit
            }
            ?.categoryName
    }
}
