package com.jdhelper.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ClickSettings::class, GiftClickHistory::class],
    version = 3,
    exportSchema = false
)
abstract class AutoClickerDatabase : RoomDatabase() {
    abstract fun clickSettingsDao(): ClickSettingsDao
    abstract fun giftClickHistoryDao(): GiftClickHistoryDao
}