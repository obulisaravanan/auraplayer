package com.mymusic.player.data.duplicates

import com.mymusic.player.model.DuplicateGroup
import com.mymusic.player.model.Song
import kotlin.math.abs

object DuplicateDetector {

    fun normalizeTitle(rawTitle: String): String {
        return rawTitle
            .replace(Regex("""(?i)\.mp3$|\.m4a$|\.flac$|\.wav$"""), "")
            .replace(Regex("""(?i)\[.*?\]|\(.*?(official|video|audio|lyrics|remaster|hd|4k|remix|feat|ft).*?\)"""), "")
            .replace(Regex("""(?i)\b(320kbps|128kbps|hq|audio|official video|official audio)\b"""), "")
            .replace(Regex("""^\d{1,3}[\s._-]+"""), "")
            .replace(Regex("""[^\p{L}\p{Nd}]+"""), "")
            .lowercase()
            .trim()
    }

    fun normalizeArtist(rawArtist: String): String {
        val cleaned = rawArtist
            .replace(Regex("""(?i)\[.*?\]|\(.*?\)"""), "")
            .replace(Regex("""[^\p{L}\p{Nd}]+"""), "")
            .lowercase()
            .trim()

        return if (cleaned == "unknown" || cleaned == "unknownartist") "" else cleaned
    }

    /**
     * Finds and groups all duplicate songs in the library.
     */
    fun findDuplicates(songs: List<Song>): List<DuplicateGroup> {
        val nonHiddenSongs = songs.filter { !it.isHidden }
        val visitedSongIds = mutableSetOf<Long>()
        val duplicateGroups = mutableListOf<DuplicateGroup>()

        for (i in nonHiddenSongs.indices) {
            val songA = nonHiddenSongs[i]
            if (songA.id in visitedSongIds) continue

            val groupMatches = mutableListOf<Song>()
            groupMatches.add(songA)

            val normTitleA = normalizeTitle(songA.title)
            val normArtistA = normalizeArtist(songA.artist)

            for (j in (i + 1) until nonHiddenSongs.size) {
                val songB = nonHiddenSongs[j]
                if (songB.id in visitedSongIds) continue

                val normTitleB = normalizeTitle(songB.title)
                val normArtistB = normalizeArtist(songB.artist)

                val durationDiff = abs(songA.durationMs - songB.durationMs)

                // Condition 1: Same normalized title + artist, and duration within 3.5 seconds
                val isTitleAndArtistMatch = (normTitleA.isNotBlank() && normTitleA == normTitleB) &&
                        (normArtistA.isBlank() || normArtistB.isBlank() || normArtistA == normArtistB) &&
                        durationDiff <= 3500L

                // Condition 2: Identical file size (> 100KB) and duration within 1.5 seconds
                val isExactFileMatch = (songA.fileSize > 100_000L && songA.fileSize == songB.fileSize) &&
                        durationDiff <= 1500L

                if (isTitleAndArtistMatch || isExactFileMatch) {
                    groupMatches.add(songB)
                    visitedSongIds.add(songB.id)
                }
            }

            if (groupMatches.size > 1) {
                visitedSongIds.add(songA.id)
                duplicateGroups.add(
                    DuplicateGroup(
                        id = "group_${songA.id}",
                        title = songA.title,
                        artist = songA.artist,
                        songs = groupMatches.sortedByDescending { it.fileSize }
                    )
                )
            }
        }

        return duplicateGroups
    }
}
