package com.jizhang.app.data.repo

import com.jizhang.app.data.SettingsStore
import com.jizhang.app.data.ai.DeepSeekClient
import com.jizhang.app.data.db.BudgetDao
import com.jizhang.app.data.db.BudgetEntity
import com.jizhang.app.data.db.CategoryDao
import com.jizhang.app.data.db.CategorySum
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

data class CsvImportResult(val inserted: Int, val merged: Int, val duplicated: Int, val error: String?)

data class MonthPoint(val label: String, val expense: Long, val income: Long)

data class StatsData(
    val expense: Long,
    val income: Long,
    val categorySlices: List<Pair<String, Long>>, // 分类名 to 金额（降序）
    val trend: List<MonthPoint>,                  // 近 N 个月
)

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val ruleDao: RuleDao,
    private val budgetDao: BudgetDao,
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
        if (parsed.error != null) return CsvImportResult(0, 0, 0, parsed.error)
        var inserted = 0
        var merged = 0
        var duplicated = 0
        val windowMs = 5 * 60 * 1000L // 通知与交易时间最多差 5 分钟
        for (t in parsed.transactions) {
            // 1) 与通知自动记账的同一笔合并：同来源、同金额、同类型、±5 分钟内
            if (t.source == TransactionSource.WECHAT_CSV || t.source == TransactionSource.ALIPAY_CSV) {
                val notifSource = if (t.source == TransactionSource.WECHAT_CSV) {
                    TransactionSource.WECHAT_NOTIFICATION.name
                } else {
                    TransactionSource.ALIPAY_NOTIFICATION.name
                }
                val match = transactionDao.findNotificationMatch(
                    notifSource, t.type, t.amountCents,
                    t.transactionTimeMs - windowMs, t.transactionTimeMs + windowMs,
                )
                if (match != null) {
                    val categoryId = classify(t.merchant, t.note)
                    transactionDao.updateDetail(match.id, t.merchant, t.note, categoryId)
                    merged++
                    continue
                }
            }
            // 2) CSV 自身去重（交易单号优先）
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
        return CsvImportResult(inserted, merged, duplicated, null)
    }

    // ---- 手动记账 ----
    suspend fun getAllCategoryNames(): List<String> = categoryDao.getAll().map { it.name }

    suspend fun addManual(
        type: TransactionType,
        amountCents: Long,
        categoryName: String?,
        note: String?,
    ): Boolean {
        if (amountCents <= 0) return false
        val now = System.currentTimeMillis()
        val key = DedupGuard.manualKey(now, amountCents, note)
        if (transactionDao.countByDedupKey(key) > 0) return false
        val categoryId = categoryName?.let { categoryDao.findByName(it)?.id }
        transactionDao.insert(
            TransactionEntity(
                amountCents = amountCents,
                type = type,
                merchant = null,
                note = note,
                categoryId = categoryId,
                source = TransactionSource.MANUAL,
                transactionTime = now,
                createdAt = now,
                dedupKey = key,
                needsReview = false,
            )
        )
        return true
    }

    // ---- 备份导出 ----
    suspend fun exportAllCsv(): String {
        val txns = transactionDao.getAll()
        val nameById = categoryDao.getAll().associate { it.id to it.name }
        val sb = StringBuilder()
        sb.append("时间,类型,金额(元),商户,分类,备注,来源\n")
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        for (t in txns) {
            val time = java.time.Instant.ofEpochMilli(t.transactionTime)
                .atZone(java.time.ZoneId.systemDefault()).format(fmt)
            val type = when (t.type) {
                TransactionType.EXPENSE -> "支出"
                TransactionType.INCOME -> "收入"
                TransactionType.REFUND -> "退款"
                TransactionType.NEUTRAL -> "中性"
            }
            sb.append(escCsv(time)).append(',')
                .append(escCsv(type)).append(',')
                .append(String.format(java.util.Locale.US, "%.2f", t.amountCents / 100.0)).append(',')
                .append(escCsv(t.merchant ?: "")).append(',')
                .append(escCsv(nameById[t.categoryId] ?: "未分类")).append(',')
                .append(escCsv(t.note ?: "")).append(',')
                .append(escCsv(t.source.name)).append("\n")
        }
        return sb.toString()
    }

    private fun escCsv(v: String): String =
        if (v.contains(',') || v.contains('"') || v.contains('\n')) {
            "\"" + v.replace("\"", "\"\"") + "\""
        } else {
            v
        }

    // ---- 预算 ----
    suspend fun getTotalBudget(month: String): Long? = budgetDao.getTotalBudget(month)

    suspend fun setTotalBudget(month: String, amountCents: Long) {
        budgetDao.deleteTotalBudget(month)
        budgetDao.insert(BudgetEntity(categoryId = null, amountCents = amountCents, month = month))
    }

    suspend fun clearTotalBudget(month: String) = budgetDao.deleteTotalBudget(month)

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

    /** 统计页数据：本月收支 + 分类占比 + 近 N 个月趋势 */
    suspend fun loadStats(months: Int): StatsData {
        val today = java.time.LocalDate.now()
        val monthStart = today.withDayOfMonth(1).atStartOfDay()
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val monthEnd = today.withDayOfMonth(1).plusMonths(1).atStartOfDay()
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        val expense = transactionDao.sumByType(TransactionType.EXPENSE.name, monthStart, monthEnd)
        val income = transactionDao.sumByType(TransactionType.INCOME.name, monthStart, monthEnd)

        val sums = transactionDao.expenseByCategory(monthStart, monthEnd)
        val nameById = categoryDao.getAll().associate { it.id to it.name }
        val slices = sums.map { (nameById[it.categoryId] ?: "未分类") to it.total }

        val trend = mutableListOf<MonthPoint>()
        for (i in months - 1 downTo 0) {
            val d = today.withDayOfMonth(1).minusMonths(i.toLong())
            val start = d.atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val end = d.plusMonths(1).atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            trend.add(
                MonthPoint(
                    label = d.monthValue.toString() + "月",
                    expense = transactionDao.sumByType(TransactionType.EXPENSE.name, start, end),
                    income = transactionDao.sumByType(TransactionType.INCOME.name, start, end),
                )
            )
        }
        return StatsData(expense, income, slices, trend)
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
