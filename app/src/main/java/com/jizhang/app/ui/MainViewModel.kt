package com.jizhang.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.SettingsStore
import com.jizhang.app.data.repo.TransactionRepository
import com.jizhang.app.data.repo.TransactionUi
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    val transactions: StateFlow<List<TransactionUi>> = repository.observeTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val needsReviewCount: StateFlow<Int> = repository.observeNeedsReview()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val onboarded: StateFlow<Boolean> = settings.onboarded

    val aiBaseUrl: String get() = settings.aiBaseUrl
    val aiModel: String get() = settings.aiModel

    private val _summary = MutableStateFlow<TransactionRepository.MonthSummary?>(null)
    val summary: StateFlow<TransactionRepository.MonthSummary?> = _summary.asStateFlow()

    fun setOnboarded(value: Boolean) = settings.setOnboarded(value)

    fun saveAiSettings(baseUrl: String, model: String, apiKey: String) {
        settings.aiBaseUrl = baseUrl.trim()
        settings.aiModel = model.trim()
        settings.aiApiKey = apiKey.trim()
    }

    fun refreshSummary() {
        val now = LocalDateTime.now()
        val start = now.withDayOfMonth(1).toLocalDate().atStartOfDay()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = now.withDayOfMonth(1).plusMonths(1).toLocalDate().atStartOfDay()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        viewModelScope.launch {
            _summary.value = repository.monthSummary(start, end)
        }
    }
}
