package com.jdhelper.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "click_tasks")
data class ClickTaskEntity(
    @PrimaryKey
    val id: String,
    val type: String, // LOOP, GIFT, TIMED, SINGLE
    val targetX: Int,
    val targetY: Int,
    val delayMillis: Double,
    val scheduledTime: Long? = null,
    val status: String, // PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)