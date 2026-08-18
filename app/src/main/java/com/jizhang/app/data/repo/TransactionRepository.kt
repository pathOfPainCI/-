package com.jizhang.app.data.repo

import com.jizhang.app.data.SettingsStore
import com.jizhang.app.data.ai.DeepSeekClient
import com.jizhang.app.data.db.CategoryDao
import com.jizhang.app.data.db.RuleDao
import com.jizhang.app.data.db.TransactionDao
import com.jizhang.app.data.db.TransactionEntity
import com.jizhang.app.domain.classify.AiClassifier
import com.jizhang.app.domain.classify.Categorizer
import com.jizhang.app.domain.dedup.DedupGuard
import com.jizhang.app.domain.model.TransactionSource
import com.jizhang.app.domain.model.TransactionType
import com.jizhang.app.domain.parser.CsvParseResult
import com.jizhang.app.domain.parser.NotificationParseResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class TransactionUi(
    val id: Long,
    val amountCents: Long,
    val type: TransactionType,
    val merchant: String?,
    val note: String?,
    val categoryName: String?,
    val source: TransactionSource,
    val transactionTime: Long,
    val needsReview: Boolean,
)

data class CsvImportResult(val inserted: Int, val duplicated: Int, val error: String?)

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val ruleDao: RuleDao,
    private val settings: SettingsStore,
) {
    companion object {
        const val WECHAT_PACKAGE = "com.tencent.mm"
        const val ALIPAY_PACKAGE = "com.eg.android.AlipayGphone"
    }

    // ---- AI 客户端（配置变化时重建） ----
    @Volatile private var cachedAi: AiClassifier? = null
    @Volatile private var aiCacheKey: String = ""

    private fun currentAi(): AiClassifier? {
        if (!settings.aiEnabled) return null
        val key = settings.aiBaseUrl + "|" + settings.aiModel + "|" + settings.aiApiKey
        if (key != aiCacheKey) {
            cachedAi = DeepSeekClient(settings.aiBaseUrl, settings.aiApiKey, settings.aiModel)
            aiCacheKey = key
        }
        return cachedAi
    }

    // ---- 通知入库 ----
    suspend fun handleNotification(
        packageName: String,
        postedTimeMs: Long,
        sbnKey: String,
        rawText: String?,
        result: NotificationParseResult,
    ): Boolean {
        val source = when (packageName) {
            WECHAT_PACKAGE -> TransactionSource.WECHAT_NOTIFICATION
            ALIPAY_PACKAGE -> TransactionSource.ALIPAY_NOTIFICATION
            else -> return false
        }
        // 噪声/空文本直接丢弃
        if (result.amountCents == null && result.reason == "疑似非支付类通知") return false

        val dedupKey = if (result.amountCents != null) {
            DedupGuard.notificationKey(packageName, postedTimeMs, result.amountCents, result.merchant)
        } else {
            // 金额解析失败：用 sbnKey 去重，入库待补录
            "notif|" + packageName + "|" + sbnKey
        }
        if (transactionDao.countByDedupKey(dedupKey) > 0) return false

        val categoryId = result.amountCents?.let { classify(result.merchant, null) }

        transactionDao.insert(
            TransactionEntity(
                amountCents = result.amountCents ?: 0L,
                type = result.type,
                merchant = result.merchant,
                note = rawText?.take(500),
                categoryId = categoryId,
                source = source,
                transactionTime = postedTimeMs,
                createdAt = System.currentTimeMillis(),
                dedupKey = dedupKey,
                needsReview = result.needsReview,
            )
        )
        return true
    }

    // ---- CSV 导入 ----
    suspend fun importCsv(parsed: CsvParseResult): CsvImportResult {
        if (parsed.error != null) return CsvImportResult(0, 0, parsed.error)
        var inserted = 0
        var duplicated = 0
        for (t in parsed.transactions) {
            val key = DedupGuard.csvKey(t.source, t.orderId, t.transactionTimeMs, t.amountCents, t.merchant)
            if (transactionDao.countByDedupKey(key) > 0) {
                duplicated++
                continue
            }
            val categoryId = classify(t.merchant, t.note)
            transactionDao.insert(
                TransactionEntity(
                    amountCents = t.amountCents,
                    type = t.type,
                    merchant = t.merchant,
                    note = t.note,
                    categoryId = categoryId,
                    source = t.source,
                    transactionTime = t.transactionTimeMs,
                    createdAt = System.currentTimeMillis(),
                    dedupKey = key,
                    needsReview = false,
                )
            )
            inserted++
        }
        return CsvImportResult(inserted, duplicated, null)
    }

    // ---- 观察 ----
    fun observeTransactions(): Flow<List<TransactionUi>> =
        combine(transactionDao.observeAll(), categoryDao.observeAll()) { txns, cats ->
            val nameById = cats.associate { it.id to it.name }
            txns.map { t ->
                TransactionUi(
                    id = t.id,
                    amountCents = t.amountCents,
                    type = t.type,
                    merchant = t.merchant,
                    note = t.note,
                    categoryName = t.categoryId?.let { nameById[it] },
                    source = t.source,
                    transactionTime = t.transactionTime,
                    needsReview = t.needsReview,
                )
            }
        }

    fun observeRange(start: Long, end: Long): Flow<List<TransactionUi>> =
        combine(transactionDao.observeByRange(start, end), categoryDao.observeAll()) { txns, cats ->
            val nameById = cats.associate { it.id to it.name }
            txns.map { t ->
                TransactionUi(
                    id = t.id, amountCents = t.amountCents, type = t.type,
                    merchant = t.merchant, note = t.note,
                    categoryName = t.categoryId?.let { nameById[it] },
                    source = t.source, transactionTime = t.transactionTime,
                    needsReview = t.needsReview,
                )
            }
        }

    fun searchTransactions(query: String): Flow<List<TransactionUi>> =
        combine(transactionDao.observeSearch(query), categoryDao.observeAll()) { txns, cats ->
            val nameById = cats.associate { it.id to it.name }
            txns.map { t ->
                TransactionUi(
                    id = t.id, amountCents = t.amountCents, type = t.type,
                    merchant = t.merchant, note = t.note,
                    categoryName = t.categoryId?.let { nameById[it] },
                    source = t.source, transactionTime = t.transactionTime,
                    needsReview = t.needsReview,
                )
            }
        }

    fun observeNeedsReview(): Flow<List<TransactionUi>> =
        combine(transactionDao.observeNeedsReview(), categoryDao.observeAll()) { txns, cats ->
            val nameById = cats.associate { it.id to it.name }
            txns.map { t ->
                TransactionUi(
                    id = t.id, amountCents = t.amountCents, type = t.type,
                    merchant = t.merchant, note = t.note,
                    categoryName = t.categoryId?.let { nameById[it] },
                    source = t.source, transactionTime = t.transactionTime,
                    needsReview = t.needsReview,
                )
            }
        }

    suspend fun monthSummary(monthStart: Long, monthEnd: Long): MonthSummary {
        return MonthSummary(
            expense = transactionDao.sumByType(TransactionType.EXPENSE.name, monthStart, monthEnd),
            income = transactionDao.sumByType(TransactionType.INCOME.name, monthStart, monthEnd),
        )
    }

    data class MonthSummary(val expense: Long, val income: Long)

    private suspend fun classify(merchant: String?, note: String?): Long? {
        val rules = ruleDao.getAll().map { r ->
            com.jizhang.app.domain.model.Rule(
                id = r.id, categoryId = r.categoryId, categoryName = r.categoryName,
                matchType = r.matchType, pattern = r.pattern, priority = r.priority,
            )
        }
        val name = Categorizer(rules, currentAi()).classify(merchant, note) ?: return null
        return categoryDao.findByName(name)?.id
    }
}
