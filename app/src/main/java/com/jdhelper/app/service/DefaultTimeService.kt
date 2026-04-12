package com.jdhelper.app.service

import com.jdhelper.data.local.TimeSource
import com.jdhelper.domain.repository.ClickSettingsRepository
import com.jdhelper.service.NtpTimeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    override fun getCurrentTime(): Long {
        val source = getCurrentTimeSourceSync()
        return when (source) {
            TimeSource.NTP -> ntpTimeService.getCurrentTime()
            TimeSource.JD -> jdTimeService.getCurrentJdTime()
        }
    }

    override fun getTimeOffset(): Long {
        val source = getCurrentTimeSourceSync()
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
        val source = getCurrentTimeSourceSync()
        return when (source) {
            TimeSource.NTP -> ntpTimeService.syncTime()
            TimeSource.JD -> jdTimeService.syncJdTime()
        }
    }

    override fun getCurrentTimeSource(): TimeSource {
        return getCurrentTimeSourceSync()
    }

    private fun getCurrentTimeSourceSync(): TimeSource {
        return try {
            runBlocking {
                clickSettingsRepository.getTimeSource().first()
            }
        } catch (e: Exception) {
            TimeSource.NTP // 默认使用 NTP
        }
    }

    override fun isSynced(): Boolean {
        val source = getCurrentTimeSourceSync()
        return when (source) {
            TimeSource.NTP -> ntpTimeService.isSynced()
            TimeSource.JD -> jdTimeService.isSynced()
        }
    }

    override fun observeTimeSource(): Flow<TimeSource> {
        return clickSettingsRepository.getTimeSource()
    }
}