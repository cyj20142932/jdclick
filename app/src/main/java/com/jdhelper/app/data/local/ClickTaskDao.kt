package com.jdhelper.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClickTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: ClickTaskEntity)

    @Update
    suspend fun update(task: ClickTaskEntity)

    @Query("SELECT * FROM click_tasks WHERE id = :id")
    suspend fun getById(id: String): ClickTaskEntity?

    @Query("SELECT * FROM click_tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<ClickTaskEntity>>

    @Query("SELECT * FROM click_tasks WHERE type = :type ORDER BY createdAt DESC")
    fun getTasksByType(type: String): Flow<List<ClickTaskEntity>>

    @Query("SELECT * FROM click_tasks WHERE status = :status ORDER BY createdAt DESC")
    fun getTasksByStatus(status: String): Flow<List<ClickTaskEntity>>

    @Query("DELETE FROM click_tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE click_tasks SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM click_tasks WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED') AND id NOT IN (SELECT id FROM click_tasks WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED') ORDER BY createdAt DESC LIMIT :keepCount)")
    suspend fun cleanupOldTasks(keepCount: Int = 100)

    @Query("SELECT * FROM click_tasks WHERE status = 'RUNNING' ORDER BY createdAt DESC")
    fun getRunningTasks(): Flow<List<ClickTaskEntity>>

    @Query("SELECT * FROM click_tasks WHERE status = 'PENDING' AND scheduledTime IS NOT NULL ORDER BY scheduledTime ASC")
    fun getPendingTasks(): Flow<List<ClickTaskEntity>>
}