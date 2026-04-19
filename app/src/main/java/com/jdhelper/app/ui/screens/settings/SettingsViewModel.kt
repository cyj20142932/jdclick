package com.jdhelper.app.ui.screens.settings

import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdhelper.app.data.local.GiftClickHistoryDao
import com.jdhelper.app.domain.repository.ClickSettingsRepository
import com.jdhelper.app.service.AccessibilityClickService
import com.jdhelper.app.service.JdTimeService
import com.jdhelper.app.service.LogConsole
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

private const val TAG = "SettingsViewModel"

data class SettingsUiState(
    val lastSyncTime: String = "从未同步",
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val isAccessibilityEnabled: Boolean = false,
    val isOverlayEnabled: Boolean = false,
    val delayMillis: Double = 0.0
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val jdTimeService: JdTimeService,
    private val clickSettingsRepository: ClickSettingsRepository,
    private val giftClickHistoryDao: GiftClickHistoryDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val delayMillis: StateFlow<Double> = clickSettingsRepository.getDelayMillis()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val millisecondDigits: StateFlow<Int> = clickSettingsRepository.getMillisecondDigits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val recordHistory: StateFlow<Boolean> = clickSettingsRepository.getRecordHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val clickDuration: StateFlow<Long> = clickSettingsRepository.getClickDuration()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100L)

    val historyCount: StateFlow<Int> = giftClickHistoryDao.getHistoryCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun getLastSyncTimeText(): String = _uiState.value.lastSyncTime

    fun isSyncing(): Boolean = _uiState.value.isSyncing

    fun isJdSynced(): Boolean = jdTimeService.isSynced()

    suspend fun syncTime(): Boolean {
        LogConsole.d(TAG, "开始同步京东时间...")
        _uiState.update { it.copy(isSyncing = true, syncError = null) }

        try {
            val success = jdTimeService.syncJdTime()
            LogConsole.d(TAG, "京东时间同步结果：$success")

            if (success) {
                _uiState.update { it.copy(lastSyncTime = "已同步", isSyncing = false) }
                LogConsole.d(TAG, "同步成功")
            } else {
                _uiState.update { it.copy(lastSyncTime = "同步失败", isSyncing = false, syncError = "同步失败，请检查网络") }
                LogConsole.w(TAG, "京东时间同步失败")
            }

            return success
        } catch (e: Exception) {
            LogConsole.e(TAG, "京东时间同步异常", e)
            _uiState.update { it.copy(lastSyncTime = "同步失败", isSyncing = false, syncError = e.message) }
            return false
        }
    }

    fun clearError() {
        _uiState.update { it.copy(syncError = null) }
    }

    fun checkAccessibilityService() {
        try {
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_GENERIC
            )
            val isAccessibilityEnabled = enabledServices.any { serviceInfo ->
                serviceInfo.resolveInfo.serviceInfo.packageName == context.packageName &&
                        serviceInfo.resolveInfo.serviceInfo.name == "com.jdhelper.service.AccessibilityClickService"
            }
            _uiState.update { it.copy(isAccessibilityEnabled = isAccessibilityEnabled) }
            LogConsole.d(TAG, "无障碍服务状态：$isAccessibilityEnabled")
        } catch (e: Exception) {
            LogConsole.e(TAG, "检查无障碍服务状态失败", e)
            _uiState.update { it.copy(isAccessibilityEnabled = false) }
        }
    }

    fun checkOverlayPermission() {
        try {
            val isOverlayEnabled = android.provider.Settings.canDrawOverlays(context)
            _uiState.update { it.copy(isOverlayEnabled = isOverlayEnabled) }
            LogConsole.d(TAG, "悬浮窗权限状态：$isOverlayEnabled")
        } catch (e: Exception) {
            LogConsole.e(TAG, "检查悬浮窗权限失败", e)
            _uiState.update { it.copy(isOverlayEnabled = false) }
        }
    }

    fun isAccessibilityEnabled(): Boolean = _uiState.value.isAccessibilityEnabled

    fun isOverlayEnabled(): Boolean = _uiState.value.isOverlayEnabled

    suspend fun setDelayMillis(delay: Double) {
        clickSettingsRepository.setDelayMillis(delay)
    }

    suspend fun setMillisecondDigits(digits: Int) {
        clickSettingsRepository.setMillisecondDigits(digits)
    }

    suspend fun setRecordHistory(enabled: Boolean) {
        clickSettingsRepository.setRecordHistory(enabled)
    }

    suspend fun setClickDuration(duration: Long) {
        val validDuration = duration.coerceAtLeast(50) // 最小 50ms
        clickSettingsRepository.setClickDuration(validDuration)
        // 同时更新 AccessibilityClickService 的静态变量
        AccessibilityClickService.clickDuration = validDuration
    }

    fun getGiftClickHistory() = giftClickHistoryDao.getRecentHistory()

    suspend fun deleteHistoryItem(id: Long) {
        giftClickHistoryDao.deleteById(id)
    }

    suspend fun updateHistorySuccess(id: Long, success: Boolean) {
        giftClickHistoryDao.updateSuccess(id, success)
    }

    suspend fun clearHistory() {
        giftClickHistoryDao.clearAll()
    }

    suspend fun restoreDelayFromHistory(delay: Double) {
        clickSettingsRepository.setDelayMillis(delay)
    }
}
