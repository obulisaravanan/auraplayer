package com.mymusic.player.model

data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val songCount: Int = 0,
    val songs: List<Song> = emptyList()
)
