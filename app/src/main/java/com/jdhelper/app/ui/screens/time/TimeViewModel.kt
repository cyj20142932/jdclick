package com.jdhelper.app.ui.screens.time

import com.jdhelper.app.service.LogConsole
import com.jdhelper.app.service.TimeService
import com.jdhelper.app.data.local.TimeSource
import com.jdhelper.app.domain.repository.ClickSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TimeManager"

/**
 * 时间管理器 - 单例模式
 * 集中管理时间状态，通过 StateFlow 供其他组件观察
 */
@Singleton
class TimeManager @Inject constructor(
    private val timeService: TimeService,
    private val clickSettingsRepository: ClickSettingsRepository
) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ==================== 公开的 StateFlow ====================

    /**
     * 当前使用的时间源 (NTP 或 JD)
     */
    val timeSource: StateFlow<TimeSource> = clickSettingsRepository.getTimeSource()
        .stateIn(scope, SharingStarted.Eagerly, TimeSource.JD)

    /**
     * 当前时间 - 每 10ms 更新一次
     */
    val currentTime: StateFlow<Long>

    /**
     * 时间偏移（毫秒）- 只在同步成功后更新
     */
    val timeOffset: StateFlow<Long>

    /**
     * 偏移显示文本，如 "+15ms" 或 "-23ms"
     */
    val offsetText: StateFlow<String>

    /**
     * 当前时间源是否已同步
     */
    val isSynced: StateFlow<Boolean>

    /**
     * 最后一次同步结果消息
     */
    var lastSyncMessage: String = ""
        private set

    /**
     * 最后一次同步是否成功
     */
    var lastSyncSuccess: Boolean = false
        private set

    init {
        // 观察当前时间 - 使用 timer 每 10ms 更新
        val timeFlow = flow {
            while (true) {
                emit(timeService.getCurrentTime())
                delay(10)
            }
        }
        currentTime = timeFlow.stateIn(
            scope,
            SharingStarted.Eagerly,
            System.currentTimeMillis()
        )

        // 时间偏移 - 只在 timeSource 变化时更新
        timeOffset = timeSource.map { _ ->
            timeService.getTimeOffset()
        }.stateIn(scope, SharingStarted.Eagerly, 0L)

        // 偏移文本
        offsetText = timeOffset.map { offset ->
            if (offset >= 0) "+${offset}ms" else "${offset}ms"
        }.stateIn(scope, SharingStarted.Eagerly, "--ms")

        // 是否已同步
        isSynced = timeSource.map { _ ->
            timeService.isSynced()
        }.stateIn(scope, SharingStarted.Eagerly, false)
    }

    // ==================== 方法 ====================

    /**
     * 同步当前时间源的时间
     * @return 同步是否成功
     */
    suspend fun syncTime(): Boolean {
        LogConsole.d(TAG, "开始同步时间...")
        val success = timeService.syncTime()

        if (success) {
            val source = timeService.getCurrentTimeSource()
            val offset = timeService.getTimeOffset()
            lastSyncMessage = if (source == TimeSource.JD) {
                "京东时间同步成功: ${if (offset >= 0) "+${offset}ms" else "${offset}ms"}"
            } else {
                "NTP时间同步成功: ${if (offset >= 0) "+${offset}ms" else "${offset}ms"}"
            }
            lastSyncSuccess = true
            LogConsole.d(TAG, lastSyncMessage)
        } else {
            val source = timeService.getCurrentTimeSource()
            lastSyncMessage = if (source == TimeSource.JD) {
                "京东时间同步失败，请检查网络"
            } else {
                "NTP时间同步失败，请检查网络"
            }
            lastSyncSuccess = false
            LogConsole.w(TAG, lastSyncMessage)
        }

        return success
    }

    /**
     * 切换时间源并自动同步
     * @return 同步是否成功
     */
    suspend fun switchTimeSource(source: TimeSource): Boolean {
        LogConsole.d(TAG, "切换时间源到: $source")

        // 先保存时间源设置
        clickSettingsRepository.setTimeSource(source)

        // 切换时间源后进行同步（timeService会根据当前设置的时间源同步）
        return syncTime()
    }

    /**
     * 获取当前时间（同步调用）
     */
    fun getCurrentTime(): Long = timeService.getCurrentTime()

    /**
     * 获取偏移显示文本（同步调用）
     */
    fun getOffsetText(): String = timeService.getOffsetText()

    /**
     * 获取最后一次同步的消息
     */
    fun getSyncResultMessage(): String = lastSyncMessage

    /**
     * 获取最后一次同步是否成功
     */
    fun wasLastSyncSuccessful(): Boolean = lastSyncSuccess
}