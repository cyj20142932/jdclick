package com.jdhelper.app.domain.model

/**
 * 点击任务实体
 */
data class ClickTask(
    val id: String,
    val type: TaskType,
    val targetX: Int,
    val targetY: Int,
    val delayMillis: Double,
    val scheduledTime: Long? = null,
    val status: TaskStatus = TaskStatus.PENDING,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val createdAt: Long = System.currentTimeMillis()
) {
    enum class TaskType {
        LOOP, GIFT, TIMED, SINGLE
    }

    enum class TaskStatus {
        PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    }
}