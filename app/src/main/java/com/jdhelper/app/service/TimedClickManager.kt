package com.jdhelper.app.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.locks.LockSupport
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimedClickManager @Inject constructor(
    private val timeService: TimeService
) {
    companion object {
        private const val TAG = "TimedClickManager"
        // 精确等待的阈值（毫秒），当剩余时间小于此值时使用自旋等待
        private const val SPIN_THRESHOLD_MS = 100L
    }

    private var job: Job? = null
    private var isRunning = false
    private var targetX: Int = 0
    private var targetY: Int = 0
    private var delayMillis: Double = 0.0
    private var plannedClickTime: Long = 0 // 计划点击时间（整分+延迟）
    private var clickCallback: ClickCallback? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    interface ClickCallback {
        fun onClickComplete(actualClickTime: Long, plannedClickTime: Long, delayMillis: Double, actualDiff: Long)
    }

    /**
     * 启动定时点击任务
     */
    fun start(targetX: Int, targetY: Int, delayMillis: Double) {
        if (isRunning) {
            stop()
        }

        this.targetX = targetX
        this.targetY = targetY
        this.delayMillis = delayMillis
        isRunning = true

        scheduleClick()
    }

    /**
     * 停止当前任务
     */
    fun stop() {
        isRunning = false
        job?.cancel()
        job = null
        LogConsole.d(TAG, "定时点击任务已停止")
    }

    /**
     * 检查是否有运行中的任务
     */
    fun isRunning(): Boolean = isRunning

    /**
     * 设置点击完成回调
     */
    fun setCallback(callback: ClickCallback?) {
        this.clickCallback = callback
    }

    /**
     * 调度点击任务 - 精确到毫秒级
     */
    private fun scheduleClick() {
        val ntpTime = timeService.getCurrentTime()
        // 计划点击时间：下一个整分时刻（时间服务已包含延迟）
        plannedClickTime = calculateNextMinuteTime(ntpTime)

        // 使用NTP时间计算延迟
        val delay = plannedClickTime - ntpTime
        LogConsole.d(TAG, "计划点击时间: $plannedClickTime, 当前NTP时间: $ntpTime, 等待: ${delay}ms")

        if (delay > 0) {
            job = scope.launch {
                // 第一阶段：使用协程delay等待大部分时间
                if (delay > SPIN_THRESHOLD_MS) {
                    delay(delay - SPIN_THRESHOLD_MS)
                }

                // 第二阶段：精确等待，使用LockSupport.parkNanos高精度等待
                while (isActive && timeService.getCurrentTime() < plannedClickTime) {
                    val remaining = plannedClickTime - timeService.getCurrentTime()
                    if (remaining > 0) {
                        // 使用parkNanos等待，精度可达纳秒级
                        LockSupport.parkNanos(remaining * 1_000_000) // 转换为纳秒
                    }
                }

                // 执行点击
                if (isRunning) {
                    performClick()
                }
            }
        } else {
            // 如果延迟时间已过，立即执行
            performClick()
        }
    }

    /**
     * 执行点击
     */
    private fun performClick() {
        val actualClickTime = timeService.getCurrentTime()
        // 使用计划点击时间计算偏差，而不是重新计算
        val actualDiff = actualClickTime - plannedClickTime

        AccessibilityClickService.getInstance()?.performGlobalClick(targetX, targetY)
        LogConsole.d(TAG, "执行点击: ($targetX, $targetY), 实际点击时间: $actualClickTime, 计划点击时间: $plannedClickTime, 偏差: ${actualDiff}ms")
        clickCallback?.onClickComplete(actualClickTime, plannedClickTime, delayMillis, actualDiff)
        isRunning = false
    }

    /**
     * 计算下一个整分时刻的时间
     */
    private fun calculateNextMinuteTime(ntpTime: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = ntpTime
        }

        // 设置为下一分钟的00秒000毫秒
        calendar.add(Calendar.MINUTE, 1)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // 不再在这里加上 delayMillis，时间服务已包含延迟补偿
        return calendar.timeInMillis
    }
}