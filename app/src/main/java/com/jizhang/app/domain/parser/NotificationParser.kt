package com.jizhang.app.domain.parser

import com.jizhang.app.domain.model.TransactionType
import java.math.BigDecimal

enum class Direction { INCOME, EXPENSE, UNKNOWN }

data class NotificationParseResult(
    val amountCents: Long?,      // null = 金额解析失败
    val direction: Direction,    // UNKNOWN = 方向无法判定
    val merchant: String?,
    val reason: String?,         // 解析不完整的原因（needsReview 提示用）
    val refund: Boolean = false, // 退款到账（收入语义、冲抵支出）
) {
    val needsReview: Boolean get() = amountCents == null || direction == Direction.UNKNOWN

    val type: TransactionType
        get() = when {
            refund && direction == Direction.INCOME -> TransactionType.REFUND
            direction == Direction.INCOME -> TransactionType.INCOME
            direction == Direction.EXPENSE -> TransactionType.EXPENSE
            else -> TransactionType.NEUTRAL
        }
}

/**
 * 微信/支付宝支付通知解析器。
 * 解析基线来自设计文档 §6.1；真实文案随版本变化，
 * 需在目标真机上 dump 通知后持续补充样本（见 NotificationParserTest）。
 */
object NotificationParser {

    // 金额提取三级降级
    private val AMOUNT_YUAN_PREFIX = Regex("""[¥￥]\s*(\d+(?:\.\d{1,2})?)""")
    private val AMOUNT_YUAN_SUFFIX = Regex("""(\d+(?:\.\d{1,2})?)\s*元""")
    private val AMOUNT_CONTEXT = Regex("""(?:金额|收款|付款|转账|红包|到账|成功)[：:\s]*(\d+\.?\d*)""")

    private val INCOME_WORDS = listOf("收款", "到账", "已收钱", "收到", "转入", "退回", "退款到账", "收益到账")
    private val EXPENSE_WORDS = listOf("已支付", "支付成功", "付款", "消费", "转出", "扣费", "支出")
    // 非支付类 / 未成功交易，直接丢弃（返回 null 金额，由上层过滤）
    private val NOISE_WORDS = listOf(
        "积分", "花呗还款", "账单提醒", "还款成功", "还款日",
        "支付失败", "交易失败", "未成功", "已取消", "逾期", "验证码", "登录",
    )

    fun parse(rawText: String?): NotificationParseResult {
        if (rawText.isNullOrBlank()) {
            return NotificationParseResult(null, Direction.UNKNOWN, null, "通知文本为空")
        }
        val text = Normalizer.normalize(rawText)

        // 1. 噪声过滤：非支付类通知（积分/还款/验证码等）直接判为不可记
        if (NOISE_WORDS.any { text.contains(it) }) {
            return NotificationParseResult(null, Direction.UNKNOWN, null, "疑似非支付类通知")
        }

        // 2. 收支方向
        val direction = detectDirection(text)

        // 3. 金额（三级降级，取第一个命中的正则）
        val amountCents = extractAmount(text)

        // 4. 商户（尽力而为，失败不影响入库）
        val merchant = extractMerchant(text)

        // 5. 退款标记（退款到账/退回）
        val refund = text.contains("退款") || text.contains("退回")

        val reason = when {
            amountCents == null -> "金额解析失败"
            direction == Direction.UNKNOWN -> "收支方向无法判定"
            else -> null
        }
        return NotificationParseResult(amountCents, direction, merchant, reason, refund)
    }

    /**
     * 判断通知是否为支付类（防止聊天消息被误记）。
     * - 标题特征：微信支付/支付宝/支付凭证/收款/到账/转账/红包/账单/退款 等
     * - 或 文本强金额特征：¥/￥/xx元
     * 聊天消息（标题=联系人名，正文无金额符号）→ 不是支付类 → 丢弃。
     */
    fun isPaymentLike(title: String?, text: String): Boolean {
        val t = title ?: ""
        if (listOf("微信支付", "支付宝", "支付凭证", "收款", "到账", "转账", "红包", "账单", "退款", "支付成功", "付款").any { t.contains(it) }) {
            return true
        }
        if (text.contains("¥") || text.contains("￥")) return true
        if (Regex("""\d+(\.\d{1,2})?\s*元""").containsMatchIn(text)) return true
        return false
    }

    fun detectDirection(text: String): Direction {
        // 支出强关键词优先（"付款"与"收款"同现时按支出，如"向XX付款"）
        for (w in EXPENSE_WORDS) if (text.contains(w)) return Direction.EXPENSE
        for (w in INCOME_WORDS) if (text.contains(w)) return Direction.INCOME

        if (text.contains("转账")) {
            val sent = (text.contains("向") || text.contains("给")) && !text.contains("收到") && !text.contains("到账")
            return if (sent) Direction.EXPENSE else Direction.INCOME
        }
        if (text.contains("红包")) {
            return when {
                text.contains("收到") || text.contains("领取") -> Direction.INCOME
                text.contains("发出") -> Direction.EXPENSE
                else -> Direction.UNKNOWN
            }
        }
        return Direction.UNKNOWN
    }

    fun extractAmount(text: String): Long? {
        val m = AMOUNT_YUAN_PREFIX.find(text)
            ?: AMOUNT_YUAN_SUFFIX.find(text)
            ?: AMOUNT_CONTEXT.find(text)
            ?: return null
        return parseAmountCents(m.groupValues[1])
    }

    /** 商户提取：多格式兜底（"向XX付款" / "商户:XX" / "收款方:XX" / "来自XX"） */
    fun extractMerchant(text: String): String? {
        val patterns = listOf(
            Regex("""(?:向|在|至|给)([^，。,.、\s:：]{1,24}?)(?:付款|支付|转账|消费|收款|购买)"""),
            Regex("""商户(?:名称|名)?[:：]\s*([^\s，。,.、;；]{1,24})"""),
            Regex("""(?:收款方|对方|收款人|付款方)[:：]\s*([^\s，。,.、;；]{1,24})"""),
            Regex("""来自\s*([^\s，。,.、;；]{1,24})"""),
        )
        for (p in patterns) {
            val m = p.find(text) ?: continue
            val name = m.groupValues[1].trim()
            if (name.isNotBlank() && name != "微信" && name != "支付宝") {
                return name
            }
        }
        return null
    }

    /** 字符串金额 → 分（BigDecimal，避免浮点误差） */
    fun parseAmountCents(raw: String): Long? {
        return try {
            BigDecimal(raw.trim().replace(",", "")).movePointRight(2).longValueExact()
        } catch (e: Exception) {
            null
        }
    }
}
