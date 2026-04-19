package com.jdhelper.app.data.repository

import android.content.Context
import com.jdhelper.app.data.local.ClickSettings
import com.jdhelper.app.data.local.ClickSettingsDao
import com.jdhelper.app.data.local.TimeSource
import com.jdhelper.app.domain.repository.ClickSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClickSettingsRepositoryImpl @Inject constructor(
    private val clickSettingsDao: ClickSettingsDao,
    @ApplicationContext private val context: Context
) : ClickSettingsRepository {

    companion object {
        private const val PREFS_NAME = "click_settings"
        private const val KEY_MILLISECOND_DIGITS = "millisecond_digits"
        private const val KEY_RECORD_HISTORY = "record_history"
        private const val KEY_DELAY_MILLIS = "delay_millis"
        private const val KEY_CLICK_DURATION = "click_duration"
        private const val DEFAULT_CLICK_DURATION = 100L
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // 使用 MutableStateFlow 来支持 Flow 订阅
    private val _delayMillisFlow = MutableStateFlow(prefs.getFloat(KEY_DELAY_MILLIS, 0f).toDouble())
    private val _millisecondDigitsFlow = MutableStateFlow(prefs.getInt(KEY_MILLISECOND_DIGITS, 1))
    private val _recordHistoryFlow = MutableStateFlow(prefs.getBoolean(KEY_RECORD_HISTORY, false))
    private val _clickDurationFlow = MutableStateFlow(prefs.getLong(KEY_CLICK_DURATION, DEFAULT_CLICK_DURATION))

    override fun getDelayMillis(): Flow<Double> = _delayMillisFlow.asStateFlow()

    override suspend fun setDelayMillis(delay: Double) {
        withContext(Dispatchers.IO) {
            prefs.edit().putFloat(KEY_DELAY_MILLIS, delay.toFloat()).apply()
            _delayMillisFlow.value = delay
        }
    }

    override fun getMillisecondDigits(): Flow<Int> = _millisecondDigitsFlow.asStateFlow()

    override suspend fun setMillisecondDigits(digits: Int) {
        withContext(Dispatchers.IO) {
            prefs.edit().putInt(KEY_MILLISECOND_DIGITS, digits).apply()
            _millisecondDigitsFlow.value = digits
        }
    }

    override fun getRecordHistory(): Flow<Boolean> = _recordHistoryFlow.asStateFlow()

    override suspend fun setRecordHistory(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            prefs.edit().putBoolean(KEY_RECORD_HISTORY, enabled).apply()
            _recordHistoryFlow.value = enabled
        }
    }

    override fun getClickDuration(): Flow<Long> = _clickDurationFlow.asStateFlow()

    override suspend fun setClickDuration(duration: Long) {
        val validDuration = duration.coerceAtLeast(50) // 最小50ms
        withContext(Dispatchers.IO) {
            prefs.edit().putLong(KEY_CLICK_DURATION, validDuration).apply()
            _clickDurationFlow.value = validDuration
        }
    }

    override fun getTimeSource(): Flow<TimeSource> {
        return clickSettingsDao.getTimeSource()
            .map { it ?: TimeSource.JD }
            .stateIn(CoroutineScope(Dispatchers.Main), SharingStarted.Eagerly, TimeSource.JD)
    }

    override suspend fun setTimeSource(source: TimeSource) {
        ensureSettingsExist()
        clickSettingsDao.updateTimeSource(source)
    }

    private suspend fun ensureSettingsExist() {
        withContext(Dispatchers.IO) {
            val settings = ClickSettings()
            clickSettingsDao.insert(settings)
        }
    }
}