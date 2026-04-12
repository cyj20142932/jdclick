package com.jdhelper.app.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.jdhelper.data.local.TimeSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 悬浮窗状态管理器 - 单例模式
 * 负责管理所有悬浮窗的共享状态，并通过广播通知状态变化
 */
@Singleton
class FloatingStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "FloatingStateManager"

        // Broadcast Actions
        const val ACTION_NTP_SYNC_CHANGED = "com.jdhelper.NTP_SYNC_CHANGED"
        const val ACTION_TASK_STATE_CHANGED = "com.jdhelper.TASK_STATE_CHANGED"
        const val ACTION_REFRESH_TIME = "com.jdhelper.REFRESH_TIME"
        const val ACTION_TIME_SOURCE_CHANGED = "com.jdhelper.TIME_SOURCE_CHANGED"

        // Extra Keys
        const val EXTRA_NTP_SYNCED = "ntp_synced"
        const val EXTRA_NTP_OFFSET = "ntp_offset"
        const val EXTRA_TASK_TYPE = "task_type"
        const val EXTRA_TASK_RUNNING = "task_running"
        const val EXTRA_TIME_SOURCE = "time_source"

        // Task Types
        const val TASK_TYPE_LOOP = "loop"
        const val TASK_TYPE_GIFT = "gift"
        const val TASK_TYPE_TIMED = "timed"

        @Volatile
        private var instance: FloatingStateManager? = null

        fun getInstance(): FloatingStateManager {
            return instance ?: throw IllegalStateException("FloatingStateManager not initialized")
        }

        fun setInstance(manager: FloatingStateManager) {
            instance = manager
        }
    }

    // NTP同步状态
    private val _ntpSynced = MutableStateFlow(false)
    val ntpSynced: StateFlow<Boolean> = _ntpSynced.asStateFlow()

    private val _ntpOffset = MutableStateFlow(0L)
    val ntpOffset: StateFlow<Long> = _ntpOffset.asStateFlow()

    // 任务运行状态
    private val _loopRunning = MutableStateFlow(false)
    val loopRunning: StateFlow<Boolean> = _loopRunning.asStateFlow()

    private val _giftRunning = MutableStateFlow(false)
    val giftRunning: StateFlow<Boolean> = _giftRunning.asStateFlow()

    private val _timedRunning = MutableStateFlow(false)
    val timedRunning: StateFlow<Boolean> = _timedRunning.asStateFlow()

    init {
        setInstance(this)
    }

    /**
     * 通知NTP同步状态变化
     */
    fun notifyNtpSyncChanged(synced: Boolean, offset: Long) {
        Log.d(TAG, "notifyNtpSyncChanged: synced=$synced, offset=$offset")
        _ntpSynced.value = synced
        _ntpOffset.value = offset

        val intent = Intent(ACTION_NTP_SYNC_CHANGED).apply {
            putExtra(EXTRA_NTP_SYNCED, synced)
            putExtra(EXTRA_NTP_OFFSET, offset)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    /**
     * 通知任务状态变化
     */
    fun notifyTaskStateChanged(taskType: String, running: Boolean) {
        Log.d(TAG, "notifyTaskStateChanged: taskType=$taskType, running=$running")

        when (taskType) {
            TASK_TYPE_LOOP -> _loopRunning.value = running
            TASK_TYPE_GIFT -> _giftRunning.value = running
            TASK_TYPE_TIMED -> _timedRunning.value = running
        }

        val intent = Intent(ACTION_TASK_STATE_CHANGED).apply {
            putExtra(EXTRA_TASK_TYPE, taskType)
            putExtra(EXTRA_TASK_RUNNING, running)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    /**
     * 请求刷新时间显示
     */
    fun requestRefreshTime() {
        Log.d(TAG, "requestRefreshTime")
        val intent = Intent(ACTION_REFRESH_TIME).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    /**
     * 通知时间源变化
     */
    fun notifyTimeSourceChanged(source: TimeSource) {
        Log.d(TAG, "notifyTimeSourceChanged: source=$source")
        val intent = Intent(ACTION_TIME_SOURCE_CHANGED).apply {
            putExtra(EXTRA_TIME_SOURCE, source.name)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}