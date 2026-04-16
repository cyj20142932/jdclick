package com.jdhelper.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClickSettingsDao {
    @Query("SELECT * FROM click_settings WHERE id = 1")
    fun getSettings(): Flow<ClickSettings?>

    @Query("SELECT delayMillis FROM click_settings WHERE id = 1")
    fun getDelayMillis(): Flow<Double?>

    @Query("SELECT millisecondDigits FROM click_settings WHERE id = 1")
    fun getMillisecondDigits(): Flow<Int?>

    @Query("SELECT recordHistory FROM click_settings WHERE id = 1")
    fun getRecordHistory(): Flow<Boolean?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: ClickSettings)

    @Query("UPDATE click_settings SET delayMillis = :delay WHERE id = 1")
    suspend fun updateDelay(delay: Double)

    @Query("SELECT timeSource FROM click_settings WHERE id = 1")
    fun getTimeSource(): Flow<TimeSource?>

    @Query("UPDATE click_settings SET timeSource = :source WHERE id = 1")
    suspend fun updateTimeSource(source: TimeSource)
}