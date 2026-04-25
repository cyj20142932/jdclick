package com.jdhelper.app.data.repository

import com.jdhelper.app.data.local.ClickTaskDao
import com.jdhelper.app.data.local.ClickTaskEntity
import com.jdhelper.app.domain.model.ClickTask
import com.jdhelper.app.domain.repository.ClickTaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ClickTaskRepositoryImpl @Inject constructor(
    private val clickTaskDao: ClickTaskDao
) : ClickTaskRepository {

    override suspend fun saveTask(task: ClickTask) {
        clickTaskDao.insert(task.toEntity())
    }

    override suspend fun getTaskById(id: String): ClickTask? {
        return clickTaskDao.getById(id)?.toDomain()
    }

    override fun getAllTasks(): Flow<List<ClickTask>> {
        return clickTaskDao.getAllTasks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getTasksByType(type: ClickTask.TaskType): Flow<List<ClickTask>> {
        return clickTaskDao.getTasksByType(type.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getTasksByStatus(status: ClickTask.TaskStatus): Flow<List<ClickTask>> {
        return clickTaskDao.getTasksByStatus(status.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun deleteTask(id: String) {
        clickTaskDao.deleteById(id)
    }

    override suspend fun updateTaskStatus(id: String, status: ClickTask.TaskStatus) {
        clickTaskDao.updateStatus(id, status.name)
    }

    override suspend fun cleanupOldTasks(keepCount: Int) {
        clickTaskDao.cleanupOldTasks(keepCount)
    }

    override fun getRunningTasks(): Flow<List<ClickTask>> {
        return clickTaskDao.getRunningTasks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getPendingTasks(): Flow<List<ClickTask>> {
        return clickTaskDao.getPendingTasks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    // 扩展函数：Entity <-> Domain 转换
    private fun ClickTask.toEntity(): ClickTaskEntity {
        return ClickTaskEntity(
            id = id,
            type = type.name,
            targetX = targetX,
            targetY = targetY,
            delayMillis = delayMillis,
            scheduledTime = scheduledTime,
            status = status.name,
            retryCount = retryCount,
            maxRetries = maxRetries,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun ClickTaskEntity.toDomain(): ClickTask {
        return ClickTask(
            id = id,
            type = ClickTask.TaskType.valueOf(type),
            targetX = targetX,
            targetY = targetY,
            delayMillis = delayMillis,
            scheduledTime = scheduledTime,
            status = ClickTask.TaskStatus.valueOf(status),
            retryCount = retryCount,
            maxRetries = maxRetries,
            createdAt = createdAt
        )
    }
}