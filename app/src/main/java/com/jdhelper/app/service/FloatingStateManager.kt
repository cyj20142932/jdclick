package com.jdhelper.app.service

import android.content.Context
import android.content.Intent
import android.util.LogConsole
import com.jdhelper.app.service.LogConsoleConsole
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
        const val ACTION_TASK_STATE_CHANGED = "com.jdhelper.TASK_STATE_CHANGED"

        // Extra Keys
        const val EXTRA_TASK_TYPE = "task_type"
        const val EXTRA_TASK_RUNNING = "task_running"

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
     * 通知任务状态变化
     */
    fun notifyTaskStateChanged(taskType: String, running: Boolean) {
        LogConsole.d(TAG, "notifyTaskStateChanged: taskType=$taskType, running=$running")

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
}