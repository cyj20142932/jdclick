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
        private const val KEY_CLOCK_FONT_FAMILY = "clock_font_family"
        private const val KEY_CLOCK_FONT_SIZE = "clock_font_size"
        private const val KEY_CLOCK_FONT_COLOR = "clock_font_color"
        private const val KEY_CLOCK_BG_COLOR = "clock_bg_color"
        private const val KEY_CLOCK_ALPHA = "clock_alpha"
        private const val KEY_CLOCK_PADDING = "clock_padding"
        private const val KEY_CLOCK_LETTER_SPACING = "clock_letter_spacing"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // 使用 MutableStateFlow 来支持 Flow 订阅
    private val _delayMillisFlow = MutableStateFlow(prefs.getFloat(KEY_DELAY_MILLIS, 0f).toDouble())
    private val _millisecondDigitsFlow = MutableStateFlow(prefs.getInt(KEY_MILLISECOND_DIGITS, 1))
    private val _recordHistoryFlow = MutableStateFlow(prefs.getBoolean(KEY_RECORD_HISTORY, false))
    private val _clickDurationFlow = MutableStateFlow(prefs.getLong(KEY_CLICK_DURATION, DEFAULT_CLICK_DURATION))

    private val _clockFontFamilyFlow = MutableStateFlow(
        prefs.getString(KEY_CLOCK_FONT_FAMILY, "monospace") ?: "monospace"
    )
    private val _clockFontSizeFlow = MutableStateFlow(prefs.getInt(KEY_CLOCK_FONT_SIZE, 22))
    private val _clockFontColorFlow = MutableStateFlow(prefs.getInt(KEY_CLOCK_FONT_COLOR, -1))
    private val _clockBgColorFlow = MutableStateFlow(prefs.getInt(KEY_CLOCK_BG_COLOR, -865704669))
    private val _clockAlphaFlow = MutableStateFlow(prefs.getInt(KEY_CLOCK_ALPHA, 204))
    private val _clockPaddingFlow = MutableStateFlow(prefs.getInt(KEY_CLOCK_PADDING, 16))
    private val _clockLetterSpacingFlow = MutableStateFlow(prefs.getFloat(KEY_CLOCK_LETTER_SPACING, 0f))

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

    override fun getClockFontFamily(): Flow<String> = _clockFontFamilyFlow.asStateFlow()

    override suspend fun setClockFontFamily(family: String) {
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_CLOCK_FONT_FAMILY, family).apply()
            _clockFontFamilyFlow.value = family
        }
    }

    override fun getClockFontSize(): Flow<Int> = _clockFontSizeFlow.asStateFlow()

    override suspend fun setClockFontSize(size: Int) {
        withContext(Dispatchers.IO) {
            prefs.edit().putInt(KEY_CLOCK_FONT_SIZE, size).apply()
            _clockFontSizeFlow.value = size
        }
    }

    override fun getClockFontColor(): Flow<Int> = _clockFontColorFlow.asStateFlow()

    override suspend fun setClockFontColor(color: Int) {
        withContext(Dispatchers.IO) {
            prefs.edit().putInt(KEY_CLOCK_FONT_COLOR, color).apply()
            _clockFontColorFlow.value = color
        }
    }

    override fun getClockBgColor(): Flow<Int> = _clockBgColorFlow.asStateFlow()

    override suspend fun setClockBgColor(color: Int) {
        withContext(Dispatchers.IO) {
            prefs.edit().putInt(KEY_CLOCK_BG_COLOR, color).apply()
            _clockBgColorFlow.value = color
        }
    }

    override fun getClockAlpha(): Flow<Int> = _clockAlphaFlow.asStateFlow()

    override suspend fun setClockAlpha(alpha: Int) {
        withContext(Dispatchers.IO) {
            prefs.edit().putInt(KEY_CLOCK_ALPHA, alpha.coerceIn(0, 255)).apply()
            _clockAlphaFlow.value = alpha.coerceIn(0, 255)
        }
    }

    override fun getClockPadding(): Flow<Int> = _clockPaddingFlow.asStateFlow()

    override suspend fun setClockPadding(padding: Int) {
        withContext(Dispatchers.IO) {
            prefs.edit().putInt(KEY_CLOCK_PADDING, padding).apply()
            _clockPaddingFlow.value = padding
        }
    }

    override fun getClockLetterSpacing(): Flow<Float> = _clockLetterSpacingFlow.asStateFlow()

    override suspend fun setClockLetterSpacing(spacing: Float) {
        withContext(Dispatchers.IO) {
            prefs.edit().putFloat(KEY_CLOCK_LETTER_SPACING, spacing).apply()
            _clockLetterSpacingFlow.value = spacing
        }
    }

    private suspend fun ensureSettingsExist() {
        withContext(Dispatchers.IO) {
            val settings = ClickSettings()
            clickSettingsDao.insert(settings)
        }
    }
}