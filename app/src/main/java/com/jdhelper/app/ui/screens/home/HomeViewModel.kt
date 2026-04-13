package com.jdhelper.ui.screens.home

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.content.Context
import android.provider.Settings
import android.util.LogConsole
import com.jdhelper.app.service.LogConsoleConsole
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdhelper.service.FloatingMenuService
import com.jdhelper.service.FloatingService
import com.jdhelper.app.service.TimeService
import com.jdhelper.app.service.JdTimeService
import com.jdhelper.app.service.FloatingStateManager
import com.jdhelper.data.local.TimeSource
import com.jdhelper.domain.repository.ClickSettingsRepository
import com.jdhelper.service.NtpTimeService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private const val TAG = "HomeViewModel"

data class HomeUiState(
    val isAccessibilityEnabled: Boolean = false,
    val isOverlayEnabled: Boolean = false,
    val isFloatingEnabled: Boolean = false,
    val isFloatingMenuEnabled: Boolean = false,
    val isNtpSyncing: Boolean = false,
    val ntpLastSyncTime: String = "从未同步",
    val ntpTimeOffset: String = "",
    val isLoading: Boolean = false,
    val syncMessage: String = ""  // 同步结果消息
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val ntpTimeService: NtpTimeService,
    private val jdTimeService: JdTimeService,
    private val timeService: TimeService,
    private val timeManager: com.jdhelper.ui.screens.time.TimeManager,
    private val clickSettingsRepository: ClickSettingsRepository,
    private val floatingStateManager: FloatingStateManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // 点击延迟状态
    val delayMillis: StateFlow<Double> = clickSettingsRepository.getDelayMillis()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // 毫秒显示位数
    val millisecondDigits: StateFlow<Int> = clickSettingsRepository.getMillisecondDigits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    // 时间源状态
    val timeSource: StateFlow<TimeSource> = clickSettingsRepository.getTimeSource()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimeSource.NTP)

    // 京东时间偏移
    private val _jdOffset = MutableStateFlow("--ms")
    val jdOffset: StateFlow<String> = _jdOffset.asStateFlow()

    // 时间状态
    private val _ntpTime = MutableStateFlow("00:00:00")
    val ntpTime: StateFlow<String> = _ntpTime.asStateFlow()

    private val _millis = MutableStateFlow("000")
    val millis: StateFlow<String> = _millis.asStateFlow()

    private val _nextClickCountdown = MutableStateFlow("")
    val nextClickCountdown: StateFlow<String> = _nextClickCountdown.asStateFlow()

    // NTP偏差状态 - 现在根据当前时间源显示偏移
    private val _ntpOffset = MutableStateFlow("--")
    val ntpOffset: StateFlow<String> = _ntpOffset.asStateFlow()

    init {
        checkAllPermissions()
        // 启动定时器，定期刷新服务状态
        startStatusRefreshTimer()
    }

    private fun startStatusRefreshTimer() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(2000) // 每2秒刷新一次
                try {
                    checkServiceStatus(context)
                } catch (e: Exception) {
                    LogConsole.e(TAG, "刷新服务状态失败", e)
                }
            }
        }
    }

    fun checkAllPermissions() {
        checkAccessibilityService()
        checkOverlayPermission()
    }

    fun checkAccessibilityService() {
        try {
            val ctx = context
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_GENERIC
            )
            val isAccessibilityEnabled = enabledServices.any {
                it.resolveInfo.serviceInfo.packageName == context.packageName &&
                        it.resolveInfo.serviceInfo.name == "com.jdhelper.service.AccessibilityClickService"
            }
            _uiState.update { it.copy(isAccessibilityEnabled = isAccessibilityEnabled) }
            LogConsole.d(TAG, "无障碍服务状态: $isAccessibilityEnabled")
        } catch (e: Exception) {
            LogConsole.e(TAG, "检查无障碍服务失败", e)
            _uiState.update { it.copy(isAccessibilityEnabled = false) }
        }
    }

    fun checkOverlayPermission() {
        try {
            val ctx = context
            val isOverlayEnabled = Settings.canDrawOverlays(context)
            _uiState.update { it.copy(isOverlayEnabled = isOverlayEnabled) }
            LogConsole.d(TAG, "悬浮窗权限状态: $isOverlayEnabled")
        } catch (e: Exception) {
            LogConsole.e(TAG, "检查悬浮窗权限失败", e)
            _uiState.update { it.copy(isOverlayEnabled = false) }
        }
    }

    fun checkServiceStatus(context: Context) {
        // 检查无障碍服务
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        val isAccessibilityEnabled = enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
                    it.resolveInfo.serviceInfo.name == "com.jdhelper.service.AccessibilityClickService"
        }

        // 检查悬浮窗是否显示
        val isFloatingEnabled = isFloatingServiceRunning(context)
        val isFloatingMenuEnabled = isFloatingMenuServiceRunning(context)

        _uiState.update {
            it.copy(
                isAccessibilityEnabled = isAccessibilityEnabled,
                isFloatingEnabled = isFloatingEnabled,
                isFloatingMenuEnabled = isFloatingMenuEnabled
            )
        }
    }

    private fun isFloatingServiceRunning(context: Context): Boolean {
        return FloatingService.isRunning()
    }

    private fun isFloatingMenuServiceRunning(context: Context): Boolean {
        return FloatingMenuService.isRunning()
    }

    /**
     * 切换悬浮时钟状态 - 主动更新UI状态，不等待服务回调
     */
    fun toggleFloatingService(context: Context) {
        if (FloatingService.isRunning()) {
            FloatingService.stopService(context)
            _uiState.update { it.copy(isFloatingEnabled = false) }
        } else {
            FloatingService.startService(context)
            _uiState.update { it.copy(isFloatingEnabled = true) }
        }
    }

    /**
     * 切换悬浮菜单状态 - 主动更新UI状态，不等待服务回调
     */
    fun toggleFloatingMenuService(context: Context) {
        if (FloatingMenuService.isRunning()) {
            FloatingMenuService.stopService(context)
            _uiState.update { it.copy(isFloatingMenuEnabled = false) }
        } else {
            FloatingMenuService.startService(context)
            _uiState.update { it.copy(isFloatingMenuEnabled = true) }
        }
    }

    suspend fun syncNtpTime(): Boolean {
        _uiState.update { it.copy(isNtpSyncing = true) }

        // 使用 timeManager 同步，会自动根据当前时间源同步
        val success = timeManager.syncTime()

        // 获取同步消息
        val message = timeManager.getSyncResultMessage()

        // 更新 UI 状态，显示同步结果
        val currentSource = timeService.getCurrentTimeSource()
        _uiState.update {
            it.copy(
                isNtpSyncing = false,
                syncMessage = message,
                ntpLastSyncTime = if (success) {
                    val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    format.format(java.util.Date())
                } else {
                    "同步失败"
                },
                ntpTimeOffset = if (success) {
                    val offset = timeService.getTimeOffset()
                    if (offset >= 0) "+${offset}ms" else "${offset}ms"
                } else {
                    ""
                }
            )
        }

        LogConsole.d(TAG, "同步完成: source=$currentSource, success=$success, message=$message")
        return success
    }

    /**
     * NTP时间同步（内部方法）
     */
    private suspend fun syncNtpTimeInternal(): Boolean {
        LogConsole.d(TAG, "开始同步NTP时间...")
        _uiState.update { it.copy(isNtpSyncing = true) }

        try {
            val success = ntpTimeService.syncTime()

            if (success) {
                val syncTime = ntpTimeService.getLastSyncTime()
                val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                val timeText = format.format(java.util.Date(syncTime))

                // 计算时间偏差
                val offset = ntpTimeService.getTimeOffset()
                val offsetText = if (offset >= 0) "+${offset}ms" else "${offset}ms"

                _uiState.update { it.copy(ntpLastSyncTime = timeText, ntpTimeOffset = offsetText, isNtpSyncing = false) }
                // 更新偏差显示
                _ntpOffset.value = offsetText
                LogConsole.d(TAG, "NTP同步成功: $timeText, 偏差: $offsetText")
            } else {
                _uiState.update { it.copy(ntpLastSyncTime = "同步失败", isNtpSyncing = false) }
                LogConsole.w(TAG, "NTP同步失败")
            }

            return success
        } catch (e: Exception) {
            LogConsole.e(TAG, "NTP同步异常", e)
            _uiState.update { it.copy(ntpLastSyncTime = "同步失败", isNtpSyncing = false) }
            return false
        }
    }

    fun getNtpLastSyncTime(): String {
        return if (ntpTimeService.isSynced()) {
            val syncTime = ntpTimeService.getLastSyncTime()
            val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            format.format(java.util.Date(syncTime))
        } else {
            "从未同步"
        }
    }

    /**
     * 设置时间源
     */
    fun setTimeSource(source: TimeSource) {
        viewModelScope.launch {
            _uiState.update { it.copy(isNtpSyncing = true) }

            val success = timeManager.switchTimeSource(source)

            // 获取同步消息
            val message = timeManager.getSyncResultMessage()
            _uiState.update {
                it.copy(
                    isNtpSyncing = false,
                    syncMessage = message
                )
            }

            LogConsole.d(TAG, "时间源已切换为: $source, 同步结果: $success, 消息: $message")
        }
    }

    /**
     * 同步京东时间
     */
    suspend fun syncJdTime(): Boolean {
        var result = false
        _uiState.update { it.copy(isNtpSyncing = true) }
        try {
            val success = jdTimeService.syncJdTime()
            if (success) {
                val offset = jdTimeService.getJdOffset()
                _jdOffset.value = if (offset >= 0) "+${offset}ms" else "${offset}ms"
                LogConsole.d(TAG, "京东时间同步成功: ${_jdOffset.value}")
                result = true
            } else {
                _jdOffset.value = "同步失败"
                LogConsole.w(TAG, "京东时间同步失败")
            }
        } catch (e: Exception) {
            LogConsole.e(TAG, "京东时间同步异常", e)
            _jdOffset.value = "同步失败"
        }
        _uiState.update { it.copy(isNtpSyncing = false) }
        return result
    }

    fun isNtpSyncing(): Boolean = _uiState.value.isNtpSyncing

    /**
     * 显示同步消息（需要在主线程调用）
     */
    fun showSyncMessage(context: android.content.Context) {
        val message = _uiState.value.syncMessage
        if (message.isNotEmpty()) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun isAccessibilityEnabled(): Boolean = _uiState.value.isAccessibilityEnabled

    fun isOverlayEnabled(): Boolean = _uiState.value.isOverlayEnabled

    /**
     * 设置点击延迟
     */
    suspend fun setDelayMillis(delay: Double) {
        clickSettingsRepository.setDelayMillis(delay)
    }

    /**
     * 启动时间更新 - 每10ms更新一次毫秒显示
     */
    fun startTimeUpdates() {
        viewModelScope.launch {
            timeManager.currentTime.collect { time ->
                try {
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    val millisFormat = SimpleDateFormat("SSS", Locale.getDefault())
                    _ntpTime.value = sdf.format(Date(time))
                    _millis.value = millisFormat.format(Date(time))
                    updateTimeOffset()
                } catch (e: Exception) {
                    LogConsole.e(TAG, "更新时间失败", e)
                }
            }
        }
    }

    private fun updateTimeOffset() {
        val currentSource = timeService.getCurrentTimeSource()
        if (currentSource == TimeSource.JD) {
            // 京东时间源
            if (jdTimeService.isSynced()) {
                val offset = jdTimeService.getJdOffset()
                _ntpOffset.value = if (offset >= 0) "+${offset}ms" else "${offset}ms"
                _jdOffset.value = _ntpOffset.value  // 保持同步
            } else {
                _ntpOffset.value = "--ms"
            }
        } else {
            // NTP时间源
            if (ntpTimeService.isSynced()) {
                val offset = ntpTimeService.getTimeOffset()
                _ntpOffset.value = if (offset >= 0) "+${offset}ms" else "${offset}ms"
            } else {
                _ntpOffset.value = "--ms"
            }
        }
    }

    /**
     * 设置下次点击倒计时
     * @param timeMillis 下次点击的目标时间戳（毫秒）
     */
    fun setNextClickCountdown(timeMillis: Long) {
        viewModelScope.launch {
            while (true) {
                try {
                    val remaining = timeMillis - System.currentTimeMillis()
                    if (remaining <= 0) {
                        _nextClickCountdown.value = ""
                        break
                    }
                    val seconds = remaining / 1000
                    val minutes = seconds / 60
                    val secs = seconds % 60
                    _nextClickCountdown.value = String.format("%02d:%02d", minutes, secs)
                } catch (e: Exception) {
                    LogConsole.e(TAG, "更新倒计时失败", e)
                }
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 取消所有协程，确保资源清理
        viewModelScope.cancel()
    }
}