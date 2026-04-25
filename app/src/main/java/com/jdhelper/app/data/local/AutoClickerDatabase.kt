package com.jdhelper.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ClickSettings::class, GiftClickHistory::class, LogEntry::class, ClickTaskEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AutoClickerDatabase : RoomDatabase() {
    abstract fun clickSettingsDao(): ClickSettingsDao
    abstract fun giftClickHistoryDao(): GiftClickHistoryDao
    abstract fun logDao(): LogDao
    abstract fun clickTaskDao(): ClickTaskDao
}