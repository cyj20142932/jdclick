package com.jdhelper.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "logs")
data class LogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val level: Int,                    // 0=VERBOSE, 1=DEBUG, 2=INFO, 3=WARN, 4=ERROR
    val tag: String,                   // 日志标签
    val message: String,               // 日志内容
    val timestamp: Long = System.currentTimeMillis(),
    val threadName: String = Thread.currentThread().name
)