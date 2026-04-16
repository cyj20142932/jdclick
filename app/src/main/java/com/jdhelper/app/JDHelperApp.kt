package com.jdhelper.app

import android.app.Application
import android.content.Context
import com.jdhelper.app.service.AccessibilityClickService
import com.jdhelper.app.di.LogConsoleInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

private const val PREFS_NAME = "click_settings"
private const val KEY_CLICK_DURATION = "click_duration"
private const val DEFAULT_CLICK_DURATION = 100L
private const val MIN_CLICK_DURATION = 50L

@HiltAndroidApp
class JDHelperApp : Application() {

    @Inject
    lateinit var logConsoleInitializer: LogConsoleInitializer

    override fun onCreate() {
        super.onCreate()
        // 加载点击持续时间配置
        loadClickDuration(this)
    }

    private fun loadClickDuration(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val duration = prefs.getLong(KEY_CLICK_DURATION, DEFAULT_CLICK_DURATION)
        // 确保最小值为50ms
        AccessibilityClickService.clickDuration = duration.coerceAtLeast(MIN_CLICK_DURATION)
    }
}