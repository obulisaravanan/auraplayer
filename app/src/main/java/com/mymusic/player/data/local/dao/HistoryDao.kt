package com.mymusic.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mymusic.player.data.local.entity.ListeningHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM listening_history ORDER BY timestamp DESC")
    fun getAllHistoryFlow(): Flow<List<ListeningHistoryEntity>>

    @Query("SELECT * FROM listening_history ORDER BY timestamp DESC")
    suspend fun getAllHistory(): List<ListeningHistoryEntity>

    @Query("SELECT * FROM listening_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int): Flow<List<ListeningHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: ListeningHistoryEntity)

    @Query("DELETE FROM listening_history")
    suspend fun clearAllHistory()
}
