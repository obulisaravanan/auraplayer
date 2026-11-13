package com.mymusic.player.data.network

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class FetchedMetadata(
    val artworkFilePath: String?,
    val albumName: String?,
    val artistName: String?,
    val year: Int?,
    val genre: String?
)

object AlbumArtFetcher {

    /**
     * Cleans up common noisy patterns found in downloaded MP3 filenames and metadata
     */
    fun cleanSearchQuery(rawTitle: String, rawArtist: String): Pair<String, String> {
        var title = rawTitle
            .replace(Regex("""(?i)\.mp3$|\.m4a$|\.flac$|\.wav$"""), "")
            .replace(Regex("""(?i)\[.*?\]|\(.*?(official|video|audio|lyrics|remaster|hd|4k|remix|feat|ft).*?\)"""), "")
            .replace(Regex("""(?i)\b(320kbps|128kbps|hq|audio|official video|official audio)\b"""), "")
            .replace(Regex("""^\d{1,3}[\s._-]+"""), "") // Remove leading track numbers like "01 - " or "01. "
            .trim()

        var artist = rawArtist
            .takeIf { it != "<unknown>" && it != "Unknown Artist" && it.isNotBlank() }
            ?.replace(Regex("""(?i)\[.*?\]|\(.*?\)"""), "")
            ?.trim() ?: ""

        // If title contains "Artist - Title" format, split them
        if (title.contains(" - ") && artist.isBlank()) {
            val parts = title.split(" - ", limit = 2)
            if (parts.size == 2) {
                artist = parts[0].trim()
                title = parts[1].trim()
            }
        }

        return Pair(title, artist)
    }

    suspend fun fetchArtworkAndMetadata(
        context: Context,
        songId: Long,
        rawTitle: String,
        rawArtist: String
    ): FetchedMetadata? = withContext(Dispatchers.IO) {
        try {
            val (cleanTitle, cleanArtist) = cleanSearchQuery(rawTitle, rawArtist)
            val query = if (cleanArtist.isNotBlank()) "$cleanArtist $cleanTitle" else cleanTitle
            if (query.isBlank()) return@withContext null

            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val apiUrl = "https://itunes.apple.com/search?term=$encodedQuery&entity=song&limit=1"

            val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "MyMusicApp/1.0")
            }

            if (connection.responseCode != 200) {
                return@withContext null
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JsonParser.parseString(responseBody).asJsonObject
            val resultCount = jsonObject.get("resultCount")?.asInt ?: 0
            if (resultCount == 0) return@withContext null

            val firstResult = jsonObject.getAsJsonArray("results").get(0).asJsonObject

            // High-resolution artwork URL (upgrade 100x100 to 600x600)
            val rawArtUrl = firstResult.get("artworkUrl100")?.asString
            val highResArtUrl = rawArtUrl?.replace("100x100bb", "600x600bb")

            val albumName = firstResult.get("collectionName")?.asString
            val artistName = firstResult.get("artistName")?.asString
            val genre = firstResult.get("primaryGenreName")?.asString
            val releaseDate = firstResult.get("releaseDate")?.asString // e.g. "2015-11-13T08:00:00Z"
            val year = releaseDate?.take(4)?.toIntOrNull()

            var savedArtworkPath: String? = null
            if (!highResArtUrl.isNullOrBlank()) {
                val artDir = File(context.filesDir, "album_art")
                if (!artDir.exists()) artDir.mkdirs()

                val artFile = File(artDir, "art_${songId}.jpg")
                val artConnection = (URL(highResArtUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                }

                if (artConnection.responseCode == 200) {
                    artConnection.inputStream.use { input ->
                        FileOutputStream(artFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    savedArtworkPath = artFile.absolutePath
                }
            }

            FetchedMetadata(
                artworkFilePath = savedArtworkPath,
                albumName = albumName,
                artistName = artistName,
                year = year,
                genre = genre
            )
        } catch (e: Exception) {
            // Fails silently when offline or timeout occurs
            null
        }
    }
}
