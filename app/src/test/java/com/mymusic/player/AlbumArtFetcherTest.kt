package com.mymusic.player

import com.mymusic.player.data.network.AlbumArtFetcher
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumArtFetcherTest {

    @Test
    fun testCleanSearchQueryWithJunkTags() {
        val rawTitle = "01 - Bohemian Rhapsody (Official Video) [Remastered] [320kbps].mp3"
        val rawArtist = "Queen"

        val (cleanTitle, cleanArtist) = AlbumArtFetcher.cleanSearchQuery(rawTitle, rawArtist)

        assertEquals("Bohemian Rhapsody", cleanTitle)
        assertEquals("Queen", cleanArtist)
    }

    @Test
    fun testCleanSearchQuerySplitArtistHyphen() {
        val rawTitle = "The Beatles - Hey Jude.m4a"
        val rawArtist = "" // Empty artist tag

        val (cleanTitle, cleanArtist) = AlbumArtFetcher.cleanSearchQuery(rawTitle, rawArtist)

        assertEquals("Hey Jude", cleanTitle)
        assertEquals("The Beatles", cleanArtist)
    }

    @Test
    fun testCleanSearchQueryWithUnknownTag() {
        val rawTitle = "Hotel California"
        val rawArtist = "<unknown>"

        val (cleanTitle, cleanArtist) = AlbumArtFetcher.cleanSearchQuery(rawTitle, rawArtist)

        assertEquals("Hotel California", cleanTitle)
        assertEquals("", cleanArtist)
    }
}
