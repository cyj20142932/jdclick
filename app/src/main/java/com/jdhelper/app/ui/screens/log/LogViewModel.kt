package com.jdhelper.ui.screens.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdhelper.app.data.local.LogEntry
import com.jdhelper.domain.repository.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogUiState(
    val showDeleteDialog: Boolean = false,
    val showClearDialog: Boolean = false,
    val selectedLogId: Long? = null
)

@HiltViewModel
class LogViewModel @Inject constructor(
    private val logRepository: LogRepository
) : ViewModel() {

    // 所有日志
    val logs: StateFlow<List<LogEntry>> = logRepository.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI状态
    private val _uiState = MutableStateFlow(LogUiState())
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()

    // 显示删除确认对话框
    fun showDeleteDialog(logId: Long) {
        _uiState.value = _uiState.value.copy(
            showDeleteDialog = true,
            selectedLogId = logId
        )
    }

    // 隐藏删除对话框
    fun hideDeleteDialog() {
        _uiState.value = _uiState.value.copy(
            showDeleteDialog = false,
            selectedLogId = null
        )
    }

    // 删除单条日志
    fun deleteLog() {
        val logId = _uiState.value.selectedLogId ?: return
        viewModelScope.launch {
            logRepository.deleteLog(logId)
            hideDeleteDialog()
        }
    }

    // 显示清空确认对话框
    fun showClearDialog() {
        _uiState.value = _uiState.value.copy(showClearDialog = true)
    }

    // 隐藏清空对话框
    fun hideClearDialog() {
        _uiState.value = _uiState.value.copy(showClearDialog = false)
    }

    // 清空所有日志
    fun clearAllLogs() {
        viewModelScope.launch {
            logRepository.clearAllLogs()
            hideClearDialog()
        }
    }
}