package com.jdhelper.app.data.repository

import com.jdhelper.app.data.local.TimeSource
import com.jdhelper.app.domain.model.TimeSyncStatus
import com.jdhelper.app.domain.repository.ClickSettingsRepository
import com.jdhelper.app.domain.repository.TimeSyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeSyncRepositoryImpl @Inject constructor(
    private val clickSettingsRepository: ClickSettingsRepository
) : TimeSyncRepository {

    private val _syncStatus = MutableStateFlow(
        TimeSyncStatus.initial(TimeSource.JD)
    )

    private val syncHistory = mutableListOf<TimeSyncStatus>()

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    init {
        scope.launch {
            val source = clickSettingsRepository.getTimeSource().first()
            _syncStatus.value = TimeSyncStatus.initial(source)
        }
    }

    override fun getCurrentStatus(): TimeSyncStatus = _syncStatus.value

    override fun getStatusFlow(): Flow<TimeSyncStatus> = _syncStatus.asStateFlow()

    override suspend fun updateStatus(status: TimeSyncStatus) {
        _syncStatus.value = status
        syncHistory.add(0, status)
        if (syncHistory.size > 100) {
            syncHistory.removeAt(syncHistory.lastIndex)
        }
    }

    override suspend fun switchTimeSource(source: TimeSource) {
        clickSettingsRepository.setTimeSource(source)
        _syncStatus.value = TimeSyncStatus.initial(source)
    }

    override fun getCurrentTimeSource(): TimeSource = _syncStatus.value.timeSource

    override fun shouldSync(): Boolean {
        val status = _syncStatus.value
        if (!status.isSynced) return true
        val syncAge = System.currentTimeMillis() - status.lastSyncTime
        return syncAge > 5 * 60 * 1000
    }

    override fun getSyncHistory(): Flow<List<TimeSyncStatus>> {
        return MutableStateFlow(syncHistory.toList()).asStateFlow()
    }

    override suspend fun cleanupOldSyncRecords(keepCount: Int) {
        while (syncHistory.size > keepCount) {
            syncHistory.removeAt(syncHistory.lastIndex)
        }
    }
}