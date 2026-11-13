package com.mymusic.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "song_records")
data class SongRecordEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val filePath: String,
    val isPlayed: Boolean = false,
    val playCount: Int = 0,
    val firstPlayedTimestamp: Long? = null,
    val lastPlayedTimestamp: Long? = null,
    val lastPositionMs: Long = 0L,
    val isFavorite: Boolean = false,
    val userMarkedPlayed: Boolean? = null,
    val customArtworkPath: String? = null,
    val hasFetchedOnlineArtwork: Boolean = false,
    val isHidden: Boolean = false
)
