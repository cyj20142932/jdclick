package com.jdhelper.app.service

import com.jdhelper.app.data.local.TimeSource
import com.jdhelper.app.domain.repository.ClickSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DefaultTimeService"

/**
 * 默认时间服务实现
 * 根据 TimeSource 路由到京东时间服务
 */
@Singleton
class DefaultTimeService @Inject constructor(
    private val jdTimeService: JdTimeService,
    private val clickSettingsRepository: ClickSettingsRepository
) : TimeService {

    private val scope = CoroutineScope(Dispatchers.Main)

    // 缓存当前时间源，避免每次都从数据库读取
    @Volatile
    private var cachedTimeSource: TimeSource = TimeSource.JD

    // 缓存延迟补偿值
    @Volatile
    private var cachedDelayMillis: Double = 0.0

    init {
        // 观察时间源变化，更新缓存
        scope.launch {
            clickSettingsRepository.getTimeSource().collect { source ->
                cachedTimeSource = source
                LogConsole.d(TAG, "时间源已更新缓存: $source")
            }
        }
        // 观察延迟补偿变化，更新缓存
        scope.launch {
            clickSettingsRepository.getDelayMillis().collect { delay ->
                cachedDelayMillis = delay
                LogConsole.d(TAG, "延迟补偿已更新缓存: ${delay}ms")
            }
        }
    }

    override fun getCurrentTime(): Long {
        val source = cachedTimeSource
        val baseTime = when (source) {
            TimeSource.JD -> jdTimeService.getCurrentJdTime()
            else -> jdTimeService.getCurrentJdTime()
        }
        // 加上延迟补偿
        return baseTime + cachedDelayMillis.toLong()
    }

    override fun getTimeOffset(): Long {
        val source = cachedTimeSource
        return when (source) {
            TimeSource.JD -> jdTimeService.getJdOffset()
            else -> jdTimeService.getJdOffset()
        }
    }

    override fun getOffsetText(): String {
        val offset = getTimeOffset()
        return if (offset >= 0) "+${offset}ms" else "${offset}ms"
    }

    override suspend fun syncTime(): Boolean {
        // 使用缓存的时间源，但在切换时间源后确保已同步
        // 由于 cachedTimeSource 通过 Flow 收集更新，切换时间源时会自动更新
        val source = cachedTimeSource
        LogConsole.d(TAG, "syncTime: 当前时间源 = $source")
        return when (source) {
            TimeSource.JD -> jdTimeService.syncJdTime()
            else -> jdTimeService.syncJdTime()
        }
    }

    override fun getCurrentTimeSource(): TimeSource {
        return cachedTimeSource
    }

    override fun isSynced(): Boolean {
        val source = cachedTimeSource
        return when (source) {
            TimeSource.JD -> jdTimeService.isSynced()
            else -> jdTimeService.isSynced()
        }
    }

    override fun observeTimeSource(): Flow<TimeSource> {
        return clickSettingsRepository.getTimeSource()
    }
}