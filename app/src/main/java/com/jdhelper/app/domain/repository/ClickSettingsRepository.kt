package com.jdhelper.app.domain.repository

import com.jdhelper.app.data.local.TimeSource
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
    fun getClockFontFamily(): Flow<String>
    suspend fun setClockFontFamily(family: String)
    fun getClockFontSize(): Flow<Int>
    suspend fun setClockFontSize(size: Int)
    fun getClockFontColor(): Flow<Int>
    suspend fun setClockFontColor(color: Int)
    fun getClockBgColor(): Flow<Int>
    suspend fun setClockBgColor(color: Int)
    fun getClockAlpha(): Flow<Int>
    suspend fun setClockAlpha(alpha: Int)
    fun getClockPadding(): Flow<Int>
    suspend fun setClockPadding(padding: Int)
    fun getClockLetterSpacing(): Flow<Float>
    suspend fun setClockLetterSpacing(spacing: Float)
}