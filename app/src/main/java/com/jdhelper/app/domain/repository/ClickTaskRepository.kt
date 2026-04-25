package com.jdhelper.app.domain.repository

import com.jdhelper.app.domain.model.ClickTask
import kotlinx.coroutines.flow.Flow

interface ClickTaskRepository {
    suspend fun saveTask(task: ClickTask)
    suspend fun getTaskById(id: String): ClickTask?
    fun getAllTasks(): Flow<List<ClickTask>>
    fun getTasksByType(type: ClickTask.TaskType): Flow<List<ClickTask>>
    fun getTasksByStatus(status: ClickTask.TaskStatus): Flow<List<ClickTask>>
    suspend fun deleteTask(id: String)
    suspend fun updateTaskStatus(id: String, status: ClickTask.TaskStatus)
    suspend fun cleanupOldTasks(keepCount: Int = 100)
    fun getRunningTasks(): Flow<List<ClickTask>>
    fun getPendingTasks(): Flow<List<ClickTask>>
}