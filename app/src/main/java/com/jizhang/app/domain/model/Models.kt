package com.jizhang.app.domain.model

/** 收支类型：REFUND 用于退款到账（收入语义、冲抵支出，统计时单独处理） */
enum class TransactionType { EXPENSE, INCOME, NEUTRAL, REFUND }

enum class TransactionSource { WECHAT_NOTIFICATION, ALIPAY_NOTIFICATION, WECHAT_CSV, ALIPAY_CSV, MANUAL }

enum class RuleMatchType { MERCHANT_EXACT, CONTAINS, REGEX }

data class Transaction(
    val id: Long = 0,
    val amountCents: Long,          // 单位「分」，正数
    val type: TransactionType,
    val merchant: String?,
    val note: String?,
    val categoryId: Long?,
    val source: TransactionSource,
    val transactionTime: Long,      // epoch ms
    val createdAt: Long,            // 入库时间 epoch ms
    val dedupKey: String,           // 去重键，唯一索引
    val needsReview: Boolean,
)

data class Category(
    val id: Long = 0,
    val name: String,
    val icon: String?,
    val type: TransactionType,
    val sortOrder: Int,
)

/** 分类规则。categoryName 直接存名字，规则引擎返回名字，Repository 再映射到 id */
data class Rule(
    val id: Long = 0,
    val categoryId: Long?,
    val categoryName: String,
    val matchType: RuleMatchType,
    val pattern: String,
    val priority: Int,
)

data class Budget(
    val id: Long = 0,
    val categoryId: Long?,          // null = 总额预算
    val amountCents: Long,
    val month: String,              // "yyyy-MM"
)
