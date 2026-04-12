package com.jdhelper.domain.repository

import com.jdhelper.data.local.TimeSource
import kotlinx.coroutines.flow.Flow

interface ClickSettingsRepository {
    fun getDelayMillis(): Flow<Double>
    suspend fun setDelayMillis(delay: Double)
    fun getMillisecondDigits(): Flow<Int>
    suspend fun setMillisecondDigits(digits: Int)
    fun getRecordHistory(): Flow<Boolean>
    suspend fun setRecordHistory(enabled: Boolean)
    fun getClickDuration(): Flow<Long>
    suspend fun setClickDuration(duration: Long)
    fun getTimeSource(): Flow<TimeSource>
    suspend fun setTimeSource(source: TimeSource)
}