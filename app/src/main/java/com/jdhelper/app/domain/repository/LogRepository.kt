package com.jdhelper.app.domain.repository

import com.jdhelper.app.data.local.LogEntry
import kotlinx.coroutines.flow.Flow

interface LogRepository {
    fun getAllLogs(): Flow<List<LogEntry>>
    fun getLogsByLevel(level: Int): Flow<List<LogEntry>>
    suspend fun addLog(log: LogEntry)
    suspend fun deleteLog(id: Long)
    suspend fun clearAllLogs()
    suspend fun cleanupOldLogs()
}