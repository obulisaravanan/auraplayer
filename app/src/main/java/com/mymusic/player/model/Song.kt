package com.mymusic.player.model

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: String,
    val albumArtUri: String? = null,
    val filePath: String = "",
    val fileSize: Long = 0L,
    val dateAdded: Long = 0L,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val genre: String = "Unknown",
    val folderName: String = "",
    // Persistent state
    val isPlayed: Boolean = false,
    val playCount: Int = 0,
    val firstPlayedTimestamp: Long? = null,
    val lastPlayedTimestamp: Long? = null,
    val lastPositionMs: Long = 0L,
    val isFavorite: Boolean = false,
    val userMarkedPlayed: Boolean? = null, // Explicit user override if any
    val customArtworkPath: String? = null,
    val hasFetchedOnlineArtwork: Boolean = false,
    val isHidden: Boolean = false
) {
    val effectiveArtworkUri: String?
        get() = customArtworkPath ?: albumArtUri

    val fileSizeFormatted: String
        get() = String.format(java.util.Locale.US, "%.1f MB", fileSize.toDouble() / (1024 * 1024))

    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}
