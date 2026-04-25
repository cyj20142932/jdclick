package com.jdhelper.app.domain.repository

import com.jdhelper.app.data.local.TimeSource
import com.jdhelper.app.domain.model.TimeSyncStatus
import kotlinx.coroutines.flow.Flow

interface TimeSyncRepository {
    fun getCurrentStatus(): TimeSyncStatus
    fun getStatusFlow(): Flow<TimeSyncStatus>
    suspend fun updateStatus(status: TimeSyncStatus)
    suspend fun switchTimeSource(source: TimeSource)
    fun getCurrentTimeSource(): TimeSource
    fun shouldSync(): Boolean
    fun getSyncHistory(): Flow<List<TimeSyncStatus>>
    suspend fun cleanupOldSyncRecords(keepCount: Int = 50)
}