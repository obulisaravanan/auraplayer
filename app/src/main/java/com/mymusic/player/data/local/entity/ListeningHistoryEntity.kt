package com.mymusic.player.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "listening_history")
data class ListeningHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationListenedMs: Long = 0L,
    val thresholdMet: Boolean = true
)
