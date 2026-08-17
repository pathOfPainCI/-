package com.jizhang.app.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.CsvFileReader
import com.jizhang.app.data.CsvFormatDetector
import com.jizhang.app.data.repo.CsvImportResult
import com.jizhang.app.data.repo.TransactionRepository
import com.jizhang.app.domain.parser.CsvParseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ImportState {
    data object Idle : ImportState
    data class Preview(val parsed: CsvParseResult) : ImportState
    data class Done(val result: CsvImportResult) : ImportState
    data class Error(val message: String) : ImportState
}

/** CSV 账单导入：选文件 → 探测编码 → 解析预览 → 确认入库（去重兜底） */
@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TransactionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    fun loadAndParse(uri: Uri) {
        viewModelScope.launch {
            _state.value = ImportState.Idle
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: run {
                        _state.value = ImportState.Error("无法读取文件")
                        return@launch
                    }
                val csv = CsvFileReader.readAndDetect(bytes)
                val parsed = CsvFormatDetector.parseAuto(csv)
                _state.value = ImportState.Preview(parsed)
            } catch (e: Exception) {
                _state.value = ImportState.Error(e.message ?: "解析失败")
            }
        }
    }

    fun confirmImport() {
        val current = _state.value as? ImportState.Preview ?: return
        if (current.parsed.error != null) return
        viewModelScope.launch {
            _state.value = ImportState.Done(repository.importCsv(current.parsed))
        }
    }

    fun dismiss() {
        _state.value = ImportState.Idle
    }
}
