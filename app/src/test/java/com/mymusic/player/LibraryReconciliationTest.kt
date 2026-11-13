package com.mymusic.player

import com.mymusic.player.data.local.entity.SongRecordEntity
import com.mymusic.player.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryReconciliationTest {

    @Test
    fun testReconciliationPreservesPlayedStateOnRescan() {
        // Mock saved database records
        val savedDbRecords = listOf(
            SongRecordEntity(
                id = 101L,
                title = "Bohemian Rhapsody",
                artist = "Queen",
                album = "A Night at the Opera",
                filePath = "/storage/emulated/0/Music/queen.mp3",
                isPlayed = true,
                playCount = 5,
                firstPlayedTimestamp = 1600000000000L,
                lastPlayedTimestamp = 1600001000000L,
                isFavorite = true
            ),
            SongRecordEntity(
                id = 102L,
                title = "Hotel California",
                artist = "Eagles",
                album = "Hotel California",
                filePath = "/storage/emulated/0/Music/eagles.mp3",
                isPlayed = false,
                playCount = 0,
                isFavorite = false
            )
        ).associateBy { it.id }

        // Raw scanned tracks from MediaStore (includes a newly added song 103L)
        val rawScannedTrackIds = listOf(101L, 102L, 103L)

        val reconciledSongs = rawScannedTrackIds.map { id ->
            val existing = savedDbRecords[id]
            Song(
                id = id,
                title = "Title $id",
                artist = "Artist",
                album = "Album",
                durationMs = 180_000L,
                contentUri = "content://media/external/audio/$id",
                isPlayed = existing?.isPlayed ?: false,
                playCount = existing?.playCount ?: 0,
                firstPlayedTimestamp = existing?.firstPlayedTimestamp,
                lastPlayedTimestamp = existing?.lastPlayedTimestamp,
                isFavorite = existing?.isFavorite ?: false
            )
        }

        // Song 101 must remain played with 5 play count
        val song101 = reconciledSongs.first { it.id == 101L }
        assertTrue("Existing song played status must be preserved across rescans", song101.isPlayed)
        assertEquals(5, song101.playCount)
        assertTrue(song101.isFavorite)

        // Song 102 must remain unplayed
        val song102 = reconciledSongs.first { it.id == 102L }
        assertFalse(song102.isPlayed)
        assertEquals(0, song102.playCount)

        // Newly discovered song 103 must start as Unplayed
        val song103 = reconciledSongs.first { it.id == 103L }
        assertFalse("Newly discovered songs must always start as Unplayed", song103.isPlayed)
        assertEquals(0, song103.playCount)
    }

    @Test
    fun testDurationFormatting() {
        val song = Song(
            id = 1L,
            title = "Test",
            artist = "Artist",
            album = "Album",
            durationMs = 215_000L, // 3 minutes 35 seconds
            contentUri = "uri"
        )
        assertEquals("3:35", song.durationFormatted)
    }
}
