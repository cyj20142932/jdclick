package com.jdhelper.app.domain.model

import com.jdhelper.app.data.local.TimeSource

data class TimeSyncStatus(
    val timeSource: TimeSource,
    val isSynced: Boolean,
    val offsetMillis: Long,
    val lastSyncTime: Long,
    val syncDuration: Long,
    val errorMessage: String? = null
) {
    companion object {
        fun initial(source: TimeSource): TimeSyncStatus = TimeSyncStatus(
            timeSource = source,
            isSynced = false,
            offsetMillis = 0L,
            lastSyncTime = 0L,
            syncDuration = 0L
        )
    }

    fun withSyncSuccess(offset: Long, duration: Long): TimeSyncStatus = copy(
        isSynced = true,
        offsetMillis = offset,
        lastSyncTime = System.currentTimeMillis(),
        syncDuration = duration,
        errorMessage = null
    )

    fun withSyncFailure(error: String, duration: Long): TimeSyncStatus = copy(
        isSynced = false,
        lastSyncTime = System.currentTimeMillis(),
        syncDuration = duration,
        errorMessage = error
    )

    fun getOffsetText(): String = if (offsetMillis >= 0) "+${offsetMillis}ms" else "${offsetMillis}ms"
}