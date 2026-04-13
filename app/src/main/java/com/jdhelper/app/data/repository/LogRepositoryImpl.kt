package com.jdhelper.data.repository

import com.jdhelper.data.local.LogDao
import com.jdhelper.data.local.LogEntry
import com.jdhelper.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepositoryImpl @Inject constructor(
    private val logDao: LogDao
) : LogRepository {

    companion object {
        private const val MAX_LOG_COUNT = 1000
        private const val DELETE_COUNT = 200
    }

    override fun getAllLogs(): Flow<List<LogEntry>> = logDao.getAllLogs()

    override fun getLogsByLevel(level: Int): Flow<List<LogEntry>> =
        logDao.getLogsBySingleLevel(level)

    override suspend fun addLog(log: LogEntry) {
        logDao.insert(log)
        // 每次添加后检查是否需要清理
        cleanupOldLogs()
    }

    override suspend fun deleteLog(id: Long) {
        logDao.delete(id)
    }

    override suspend fun clearAllLogs() {
        logDao.clearAll()
    }

    override suspend fun cleanupOldLogs() {
        val count = logDao.getCount()
        if (count > MAX_LOG_COUNT) {
            logDao.deleteOldest(count - MAX_LOG_COUNT + DELETE_COUNT)
        }
    }
}