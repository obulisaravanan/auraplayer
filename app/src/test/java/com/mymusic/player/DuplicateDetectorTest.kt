package com.mymusic.player

import com.mymusic.player.data.duplicates.DuplicateDetector
import com.mymusic.player.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateDetectorTest {

    @Test
    fun testFindDuplicatesAndPicksHigherQualityAsRecommended() {
        val songA = Song(
            id = 101L,
            title = "01. Bohemian Rhapsody (Official Video) [320kbps].mp3",
            artist = "Queen",
            album = "A Night at the Opera",
            durationMs = 354_000L,
            contentUri = "content://media/101",
            filePath = "/storage/emulated/0/Music/queen_hq.mp3",
            fileSize = 8_500_000L, // 8.5 MB
            playCount = 5,
            isPlayed = true
        )

        val songB = Song(
            id = 102L,
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "Unknown Album",
            durationMs = 355_000L, // within 1 second
            contentUri = "content://media/102",
            filePath = "/storage/emulated/0/Download/queen.mp3",
            fileSize = 3_200_000L, // 3.2 MB
            playCount = 0,
            isPlayed = false
        )

        val songC = Song(
            id = 103L,
            title = "Hotel California",
            artist = "Eagles",
            album = "Hotel California",
            durationMs = 390_000L,
            contentUri = "content://media/103",
            fileSize = 9_000_000L
        )

        val duplicates = DuplicateDetector.findDuplicates(listOf(songA, songB, songC))

        assertEquals(1, duplicates.size)
        val group = duplicates.first()
        assertEquals(2, group.songs.size)

        // Recommended copy to keep MUST be songA because it has higher file size (bitrate) and listening history!
        assertEquals(songA.id, group.recommendedSong.id)
    }

    @Test
    fun testDoNotGroupDifferentDurationsEvenIfTitlesMatch() {
        val studioVersion = Song(
            id = 1L,
            title = "Comfortably Numb",
            artist = "Pink Floyd",
            album = "The Wall",
            durationMs = 382_000L, // 6:22
            contentUri = "content://1"
        )

        val liveExtendedVersion = Song(
            id = 2L,
            title = "Comfortably Numb (Live in Berlin)",
            artist = "Pink Floyd",
            album = "Pulse",
            durationMs = 530_000L, // 8:50 (duration differs by over 2 minutes!)
            contentUri = "content://2"
        )

        val duplicates = DuplicateDetector.findDuplicates(listOf(studioVersion, liveExtendedVersion))
        assertTrue("Live extended tracks with large duration difference should not be considered duplicates", duplicates.isEmpty())
    }

    @Test
    fun testTitleNormalizationCleansJunkPrefixes() {
        val norm1 = DuplicateDetector.normalizeTitle("01 - Song Title [Remastered] (Official Video) [320kbps].mp3")
        val norm2 = DuplicateDetector.normalizeTitle("Song Title")

        assertEquals(norm2, norm1)
    }
}
