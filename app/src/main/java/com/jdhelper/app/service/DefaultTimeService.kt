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
        return jdTimeService.getCurrentJdTime() + cachedDelayMillis.toLong()
    }

    override fun getTimeOffset(): Long = jdTimeService.getJdOffset()

    override fun getOffsetText(): String {
        val offset = getTimeOffset()
        return if (offset >= 0) "+${offset}ms" else "${offset}ms"
    }

    override suspend fun syncTime(): Boolean {
        LogConsole.d(TAG, "syncTime: 当前时间源 = JD")
        return jdTimeService.syncJdTime()
    }

    override fun getCurrentTimeSource(): TimeSource {
        return cachedTimeSource
    }

    override fun isSynced(): Boolean = jdTimeService.isSynced()

    override fun observeTimeSource(): Flow<TimeSource> {
        return clickSettingsRepository.getTimeSource()
    }
}