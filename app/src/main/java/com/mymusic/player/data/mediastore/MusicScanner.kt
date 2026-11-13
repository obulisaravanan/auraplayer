package com.mymusic.player.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.mymusic.player.data.local.dao.SongDao
import com.mymusic.player.data.local.entity.SongRecordEntity
import com.mymusic.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MusicScanner(
    private val context: Context,
    private val songDao: SongDao
) {

    suspend fun scanDeviceAudio(): List<Song> = withContext(Dispatchers.IO) {
        val songList = mutableListOf<Song>()
        val contentResolver = context.contentResolver

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val existingRecordsMap = try {
            songDao.getAllRecords().associateBy { it.id }
        } catch (e: Exception) {
            emptyMap()
        }

        val newRecordsToInsert = mutableListOf<SongRecordEntity>()

        try {
            contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)

                val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown Title"
                    val artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Unknown Artist"
                    val album = cursor.getString(albumCol)?.takeIf { it != "<unknown>" } ?: "Unknown Album"
                    val durationMs = cursor.getLong(durationCol)
                    val filePath = cursor.getString(dataCol) ?: ""
                    val fileSize = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateAddedCol)
                    val albumId = cursor.getLong(albumIdCol)
                    val track = if (trackCol >= 0) cursor.getInt(trackCol) else 0
                    val year = if (yearCol >= 0) cursor.getInt(yearCol) else 0

                    val contentUri = ContentUris.withAppendedId(collection, id).toString()
                    val albumArtUri = ContentUris.withAppendedId(albumArtBaseUri, albumId).toString()
                    val folderName = if (filePath.isNotEmpty()) {
                        File(filePath).parentFile?.name ?: ""
                    } else ""

                    // Reconcile with saved Room record
                    val existing = existingRecordsMap[id]
                    val isPlayed: Boolean
                    val playCount: Int
                    val firstPlayed: Long?
                    val lastPlayed: Long?
                    val lastPosition: Long
                    val isFav: Boolean
                    val userMarked: Boolean?

                    if (existing != null) {
                        isPlayed = existing.isPlayed
                        playCount = existing.playCount
                        firstPlayed = existing.firstPlayedTimestamp
                        lastPlayed = existing.lastPlayedTimestamp
                        lastPosition = existing.lastPositionMs
                        isFav = existing.isFavorite
                        userMarked = existing.userMarkedPlayed
                    } else {
                        // Newly discovered song starts as Unplayed!
                        isPlayed = false
                        playCount = 0
                        firstPlayed = null
                        lastPlayed = null
                        lastPosition = 0L
                        isFav = false
                        userMarked = null

                        newRecordsToInsert.add(
                            SongRecordEntity(
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                filePath = filePath,
                                isPlayed = false,
                                playCount = 0,
                                firstPlayedTimestamp = null,
                                lastPlayedTimestamp = null,
                                lastPositionMs = 0L,
                                isFavorite = false,
                                userMarkedPlayed = null
                            )
                        )
                    }

                    val customArt = existing?.customArtworkPath
                    val hasFetched = existing?.hasFetchedOnlineArtwork ?: false
                    val resolvedAlbum = if (!existing?.album.isNullOrBlank() && existing?.album != "Unknown Album") existing!!.album else album

                    songList.add(
                        Song(
                            id = id,
                            title = title,
                            artist = artist,
                            album = resolvedAlbum,
                            durationMs = durationMs,
                            contentUri = contentUri,
                            albumArtUri = albumArtUri,
                            filePath = filePath,
                            fileSize = fileSize,
                            dateAdded = dateAdded,
                            trackNumber = track,
                            year = year,
                            genre = "Music",
                            folderName = folderName,
                            isPlayed = isPlayed,
                            playCount = playCount,
                            firstPlayedTimestamp = firstPlayed,
                            lastPlayedTimestamp = lastPlayed,
                            lastPositionMs = lastPosition,
                            isFavorite = isFav,
                            userMarkedPlayed = userMarked,
                            customArtworkPath = customArt,
                            hasFetchedOnlineArtwork = hasFetched
                        )
                    )
                }
            }

            if (newRecordsToInsert.isNotEmpty()) {
                songDao.insertOrUpdateAll(newRecordsToInsert)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        songList
    }
}
