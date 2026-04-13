package com.jdhelper.app.service

import android.util.LogConsole
import com.jdhelper.app.service.LogConsoleConsole
import com.jdhelper.data.local.TimeSource
import com.jdhelper.domain.repository.ClickSettingsRepository
import com.jdhelper.service.NtpTimeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DefaultTimeService"

/**
 * 默认时间服务实现
 * 根据 TimeSource 路由到 NTP 或京东时间服务
 */
@Singleton
class DefaultTimeService @Inject constructor(
    private val ntpTimeService: NtpTimeService,
    private val jdTimeService: JdTimeService,
    private val clickSettingsRepository: ClickSettingsRepository
) : TimeService {

    private val scope = CoroutineScope(Dispatchers.Main)

    // 缓存当前时间源，避免每次都从数据库读取
    @Volatile
    private var cachedTimeSource: TimeSource = TimeSource.NTP

    init {
        // 观察时间源变化，更新缓存
        scope.launch {
            clickSettingsRepository.getTimeSource().collect { source ->
                cachedTimeSource = source
                LogConsole.d(TAG, "时间源已更新缓存: $source")
            }
        }
    }

    override fun getCurrentTime(): Long {
        val source = cachedTimeSource
        return when (source) {
            TimeSource.NTP -> ntpTimeService.getCurrentTime()
            TimeSource.JD -> jdTimeService.getCurrentJdTime()
        }
    }

    override fun getTimeOffset(): Long {
        val source = cachedTimeSource
        return when (source) {
            TimeSource.NTP -> ntpTimeService.getTimeOffset()
            TimeSource.JD -> jdTimeService.getJdOffset()
        }
    }

    override fun getOffsetText(): String {
        val offset = getTimeOffset()
        return if (offset >= 0) "+${offset}ms" else "${offset}ms"
    }

    override suspend fun syncTime(): Boolean {
        val source = cachedTimeSource
        LogConsole.d(TAG, "syncTime: 当前时间源 = $source")
        return when (source) {
            TimeSource.NTP -> ntpTimeService.syncTime()
            TimeSource.JD -> jdTimeService.syncJdTime()
        }
    }

    override fun getCurrentTimeSource(): TimeSource {
        return cachedTimeSource
    }

    override fun isSynced(): Boolean {
        val source = cachedTimeSource
        return when (source) {
            TimeSource.NTP -> ntpTimeService.isSynced()
            TimeSource.JD -> jdTimeService.isSynced()
        }
    }

    override fun observeTimeSource(): Flow<TimeSource> {
        return clickSettingsRepository.getTimeSource()
    }
}