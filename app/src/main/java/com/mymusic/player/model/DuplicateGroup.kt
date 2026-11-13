package com.mymusic.player.model

data class DuplicateGroup(
    val id: String,
    val title: String,
    val artist: String,
    val songs: List<Song>
) {
    /**
     * Recommended song to keep: highest file size (higher audio quality/bitrate)
     * and prioritizing songs with existing play history.
     */
    val recommendedSong: Song
        get() = songs.maxWithOrNull(
            compareBy<Song> { it.fileSize }
                .thenBy { it.playCount }
        ) ?: songs.first()
}
