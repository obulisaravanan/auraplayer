package com.mymusic.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mymusic.player.data.local.entity.SongRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM song_records")
    fun getAllRecordsFlow(): Flow<List<SongRecordEntity>>

    @Query("SELECT * FROM song_records")
    suspend fun getAllRecords(): List<SongRecordEntity>

    @Query("SELECT * FROM song_records WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: Long): SongRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: SongRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(records: List<SongRecordEntity>)

    @Query("UPDATE song_records SET isPlayed = :isPlayed, userMarkedPlayed = :userMarked WHERE id = :id")
    suspend fun updatePlayedStatus(id: Long, isPlayed: Boolean, userMarked: Boolean?)

    @Query("""
        UPDATE song_records 
        SET playCount = playCount + 1, 
            isPlayed = 1, 
            lastPlayedTimestamp = :timestamp,
            firstPlayedTimestamp = CASE WHEN firstPlayedTimestamp IS NULL THEN :timestamp ELSE firstPlayedTimestamp END
        WHERE id = :id
    """)
    suspend fun markPlayedAndIncrementCount(id: Long, timestamp: Long)

    @Query("UPDATE song_records SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE song_records SET lastPositionMs = :positionMs WHERE id = :id")
    suspend fun updatePlaybackPosition(id: Long, positionMs: Long)

    @Query("""
        UPDATE song_records 
        SET customArtworkPath = :customArtworkPath, 
            album = CASE WHEN :album != '' THEN :album ELSE album END,
            hasFetchedOnlineArtwork = :hasFetched
        WHERE id = :id
    """)
    suspend fun updateArtworkAndMetadata(id: Long, customArtworkPath: String?, album: String, hasFetched: Boolean)

    @Query("UPDATE song_records SET isHidden = :isHidden WHERE id = :id")
    suspend fun updateHiddenStatus(id: Long, isHidden: Boolean)

    @Query("""
        UPDATE song_records 
        SET playCount = playCount + :addedCount, 
            isPlayed = CASE WHEN :isPlayed = 1 THEN 1 ELSE isPlayed END,
            isFavorite = CASE WHEN :isFavorite = 1 THEN 1 ELSE isFavorite END
        WHERE id = :targetId
    """)
    suspend fun mergeListeningHistory(targetId: Long, addedCount: Int, isPlayed: Boolean, isFavorite: Boolean)

    @Query("""
        UPDATE song_records 
        SET isPlayed = 0, 
            playCount = 0, 
            firstPlayedTimestamp = NULL, 
            lastPlayedTimestamp = NULL, 
            lastPositionMs = 0, 
            userMarkedPlayed = NULL
    """)
    suspend fun resetAllListeningHistory()

    @Query("DELETE FROM song_records WHERE id NOT IN (:validIds)")
    suspend fun deleteStaleRecords(validIds: List<Long>)
}
