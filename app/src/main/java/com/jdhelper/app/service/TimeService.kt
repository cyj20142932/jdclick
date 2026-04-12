package com.jdhelper.app.service

import com.jdhelper.data.local.TimeSource
import kotlinx.coroutines.flow.Flow

/**
 * 统一时间服务接口
 * 根据设置的 TimeSource 自动返回对应时间
 */
interface TimeService {
    /**
     * 获取当前时间（根据设置的 TimeSource 自动选择）
     */
    fun getCurrentTime(): Long

    /**
     * 获取时间偏移（当前时间源相对于系统时间的偏移，毫秒）
     */
    fun getTimeOffset(): Long

    /**
     * 获取偏移显示文本，如 "+15ms" 或 "-23ms"
     */
    fun getOffsetText(): String

    /**
     * 同步当前时间源的时间
     * @return 同步是否成功
     */
    suspend fun syncTime(): Boolean

    /**
     * 获取当前使用的时间源
     */
    fun getCurrentTimeSource(): TimeSource

    /**
     * 检查当前时间源是否已同步
     */
    fun isSynced(): Boolean

    /**
     * 监听时间源变化
     */
    fun observeTimeSource(): Flow<TimeSource>
}