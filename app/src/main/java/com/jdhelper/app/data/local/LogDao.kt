package com.jdhelper.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT :limit")
    fun getAllLogs(limit: Int = 1000): Flow<List<LogEntry>>

    @Query("SELECT * FROM logs WHERE level = :level ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsBySingleLevel(level: Int, limit: Int = 1000): Flow<List<LogEntry>>

    @Query("SELECT * FROM logs WHERE level >= :minLevel AND level <= :maxLevel ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsByLevelRange(minLevel: Int, maxLevel: Int, limit: Int = 1000): Flow<List<LogEntry>>

    @Insert
    suspend fun insert(log: LogEntry)

    @Query("DELETE FROM logs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM logs")
    suspend fun clearAll()

    @Query("DELETE FROM logs WHERE timestamp < :beforeTime")
    suspend fun deleteOlderThan(beforeTime: Long)

    @Query("SELECT COUNT(*) FROM logs")
    suspend fun getCount(): Int

    @Query("DELETE FROM logs WHERE id IN (SELECT id FROM logs ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)
}