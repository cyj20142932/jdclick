package com.jdhelper.ui.screens.settings

import android.app.Application
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdhelper.data.local.GiftClickHistory
import com.jdhelper.data.local.GiftClickHistoryDao
import com.jdhelper.domain.repository.ClickSettingsRepository
import com.jdhelper.service.NtpTimeService
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
    val ntpServer: String = "ntp.aliyun.com",
    val lastSyncTime: String = "从未同步",
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val isAccessibilityEnabled: Boolean = false,
    val isOverlayEnabled: Boolean = false,
    val delayMillis: Double = 0.0
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val ntpTimeService: NtpTimeService,
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

    init {
        // 初始化当前服务器
        _uiState.update { it.copy(ntpServer = ntpTimeService.getCurrentServer()) }
    }

    fun getNtpServer(): String = ntpTimeService.getCurrentServer()

    fun getNtpServers(): List<String> = NtpTimeService.NTP_SERVERS

    fun getLastSyncTimeText(): String = _uiState.value.lastSyncTime

    fun isSyncing(): Boolean = _uiState.value.isSyncing

    fun getLastSyncTime(): Long = ntpTimeService.getLastSyncTime()

    fun isNtpSynced(): Boolean = ntpTimeService.isSynced()

    fun setNtpServer(server: String) {
        ntpTimeService.setServer(server)
        _uiState.update { it.copy(ntpServer = server) }
        Log.d(TAG, "切换NTP服务器: $server")
    }

    suspend fun syncTime(): Boolean {
        Log.d(TAG, "开始同步NTP时间...")
        _uiState.update { it.copy(isSyncing = true, syncError = null) }

        try {
            val success = ntpTimeService.syncTime()
            Log.d(TAG, "NTP同步结果: $success")

            if (success) {
                val syncTime = ntpTimeService.getLastSyncTime()
                val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                val timeText = format.format(java.util.Date(syncTime))
                _uiState.update { it.copy(lastSyncTime = timeText, isSyncing = false) }
                Log.d(TAG, "同步成功，时间: $timeText")
            } else {
                _uiState.update { it.copy(lastSyncTime = "同步失败", isSyncing = false, syncError = "同步失败，请检查网络") }
                Log.w(TAG, "NTP同步失败")
            }

            return success
        } catch (e: Exception) {
            Log.e(TAG, "NTP同步异常", e)
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
            val isAccessibilityEnabled = enabledServices.any {
                it.resolveInfo.serviceInfo.packageName == context.packageName &&
                        it.resolveInfo.serviceInfo.name == "com.jdhelper.service.AccessibilityClickService"
            }
            _uiState.update { it.copy(isAccessibilityEnabled = isAccessibilityEnabled) }
            Log.d(TAG, "无障碍服务状态: $isAccessibilityEnabled")
        } catch (e: Exception) {
            Log.e(TAG, "检查无障碍服务状态失败", e)
            _uiState.update { it.copy(isAccessibilityEnabled = false) }
        }
    }

    fun checkOverlayPermission() {
        try {
            val isOverlayEnabled = android.provider.Settings.canDrawOverlays(context)
            _uiState.update { it.copy(isOverlayEnabled = isOverlayEnabled) }
            Log.d(TAG, "悬浮窗权限状态: $isOverlayEnabled")
        } catch (e: Exception) {
            Log.e(TAG, "检查悬浮窗权限失败", e)
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
        val validDuration = duration.coerceAtLeast(50) // 最小50ms
        clickSettingsRepository.setClickDuration(validDuration)
        // 同时更新 AccessibilityClickService 的静态变量
        com.jdhelper.service.AccessibilityClickService.clickDuration = validDuration
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