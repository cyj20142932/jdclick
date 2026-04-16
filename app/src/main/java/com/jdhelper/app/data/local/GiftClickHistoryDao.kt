package com.jdhelper.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GiftClickHistoryDao {
    @Query("SELECT * FROM gift_click_history ORDER BY createTime DESC LIMIT 100")
    fun getRecentHistory(): Flow<List<GiftClickHistory>>

    @Query("SELECT * FROM gift_click_history ORDER BY createTime DESC")
    fun getAllHistory(): Flow<List<GiftClickHistory>>

    @Query("SELECT COUNT(*) FROM gift_click_history")
    fun getHistoryCount(): Flow<Int>

    @Insert
    suspend fun insert(history: GiftClickHistory)

    @Query("DELETE FROM gift_click_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE gift_click_history SET success = :success WHERE id = :id")
    suspend fun updateSuccess(id: Long, success: Boolean)

    @Query("DELETE FROM gift_click_history")
    suspend fun clearAll()

    @Query("DELETE FROM gift_click_history WHERE createTime < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}