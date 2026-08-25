package com.jizhang.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.SettingsStore
import com.jizhang.app.data.db.RuleEntity
import com.jizhang.app.data.repo.StatsData
import com.jizhang.app.data.repo.TransactionRepository
import com.jizhang.app.data.repo.TransactionUi
import com.jizhang.app.domain.model.RuleMatchType
import com.jizhang.app.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    enum class RangeMode { ALL, DAY, MONTH, YEAR }

    data class FilterState(
        val mode: RangeMode = RangeMode.ALL,
        val dayOffset: Int = 0,
        val monthOffset: Int = 0,
        val yearOffset: Int = 0,
        val query: String = "",
    )

    private val filter = MutableStateFlow(FilterState())

    val transactions: StateFlow<List<TransactionUi>> = filter.flatMapLatest { f ->
        if (f.query.isNotBlank()) {
            repository.searchTransactions(f.query.trim())
        } else {
            val range = rangeOf(f)
            repository.observeRange(range.first, range.second)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val needsReviewCount: StateFlow<Int> = repository.observeNeedsReview()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val onboarded: StateFlow<Boolean> = settings.onboarded

    val aiBaseUrl: String get() = settings.aiBaseUrl
    val aiModel: String get() = settings.aiModel

    private val _summary = MutableStateFlow<TransactionRepository.MonthSummary?>(null)
    val summary: StateFlow<TransactionRepository.MonthSummary?> = _summary.asStateFlow()

    private val _stats = MutableStateFlow<StatsData?>(null)
    val stats: StateFlow<StatsData?> = _stats.asStateFlow()

    private val _monthBudget = MutableStateFlow<Long>(0L)
    val monthBudget: StateFlow<Long> = _monthBudget.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    val rules: StateFlow<List<RuleEntity>> = repository.observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshStats()
        refreshBudget()
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            _categories.value = repository.getAllCategoryNames()
        }
    }

    /** 手动记账 */
    fun addManual(type: TransactionType, amountCents: Long, categoryName: String?, note: String) {
        viewModelScope.launch {
            repository.addManual(type, amountCents, categoryName, note.takeIf { it.isNotBlank() })
        }
    }

    /** 备份导出（返回 CSV 文本） */
    suspend fun exportCsv(): String = repository.exportAllCsv()

    /** 分类规则 */
    fun addRule(categoryName: String, matchType: RuleMatchType, pattern: String) {
        viewModelScope.launch {
            repository.addRule(categoryName, matchType, pattern)
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch {
            repository.deleteRule(id)
        }
    }

    fun setOnboarded(value: Boolean) = settings.setOnboarded(value)

    fun saveAiSettings(baseUrl: String, model: String, apiKey: String) {
        settings.aiBaseUrl = baseUrl.trim()
        settings.aiModel = model.trim()
        settings.aiApiKey = apiKey.trim()
    }

    // ---- 筛选控制 ----
    fun setMode(mode: RangeMode) = filter.update { it.copy(mode = mode) }

    fun shiftDay(delta: Int) = filter.update { it.copy(dayOffset = it.dayOffset + delta) }

    fun shiftMonth(delta: Int) = filter.update { it.copy(monthOffset = it.monthOffset + delta) }

    fun shiftYear(delta: Int) = filter.update { it.copy(yearOffset = it.yearOffset + delta) }

    /** 按当前模式切换上一/下一时间段 */
    fun shift(delta: Int) {
        when (filter.value.mode) {
            RangeMode.ALL -> Unit
            RangeMode.DAY -> shiftDay(delta)
            RangeMode.MONTH -> shiftMonth(delta)
            RangeMode.YEAR -> shiftYear(delta)
        }
    }

    /** 供 UI 读取当前筛选状态（只读快照） */
    val filterValue: FilterState get() = filter.value

    fun setQuery(q: String) = filter.update { it.copy(query = q) }

    /** 当前筛选范围的标题，如 "全部记录" / "8月18日" / "2026年8月" / "2026年" */
    fun rangeTitle(f: FilterState = filter.value): String {
        val today = LocalDate.now()
        return when (f.mode) {
            RangeMode.ALL -> "全部记录"
            RangeMode.DAY -> today.plusDays(f.dayOffset.toLong())
                .let { it.monthValue.toString() + "月" + it.dayOfMonth + "日" }
            RangeMode.MONTH -> today.withDayOfMonth(1).plusMonths(f.monthOffset.toLong())
                .let { it.year.toString() + "年" + it.monthValue + "月" }
            RangeMode.YEAR -> today.withDayOfMonth(1).withMonth(1).plusYears(f.yearOffset.toLong())
                .let { it.year.toString() + "年" }
        }
    }

    private fun rangeOf(f: FilterState): Pair<Long, Long> {
        val today = LocalDate.now()
        return when (f.mode) {
            RangeMode.ALL -> 0L to Long.MAX_VALUE
            RangeMode.DAY -> {
                val d = today.plusDays(f.dayOffset.toLong())
                atStart(d) to atStart(d.plusDays(1))
            }
            RangeMode.MONTH -> {
                val d = today.withDayOfMonth(1).plusMonths(f.monthOffset.toLong())
                atStart(d) to atStart(d.plusMonths(1))
            }
            RangeMode.YEAR -> {
                val d = today.withDayOfMonth(1).withMonth(1).plusYears(f.yearOffset.toLong())
                atStart(d) to atStart(d.plusYears(1))
            }
        }
    }

    private fun atStart(d: LocalDate): Long =
        d.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** 当前月份 "yyyy-MM" */
    private fun currentMonth(): String =
        java.time.LocalDate.now().toString().substring(0, 7)

    fun refreshBudget() {
        viewModelScope.launch {
            _monthBudget.value = repository.getTotalBudget(currentMonth()) ?: 0L
        }
    }

    /** 设置本月总额预算（输入为元字符串，如 "500" 或 "500.5"） */
    fun setMonthBudget(yuan: String) {
        val cents = ((yuan.toDoubleOrNull() ?: 0.0) * 100).toLong()
        if (cents <= 0) return
        viewModelScope.launch {
            repository.setTotalBudget(currentMonth(), cents)
            _monthBudget.value = cents
        }
    }

    fun clearMonthBudget() {
        viewModelScope.launch {
            repository.clearTotalBudget(currentMonth())
            _monthBudget.value = 0L
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            _stats.value = repository.loadStats(6)
        }
    }

    fun refreshSummary() {
        val now = java.time.LocalDateTime.now()
        val start = now.withDayOfMonth(1).toLocalDate().atStartOfDay()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = now.withDayOfMonth(1).plusMonths(1).toLocalDate().atStartOfDay()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        viewModelScope.launch {
            _summary.value = repository.monthSummary(start, end)
        }
    }
}
