package com.mymusic.player.data.backup

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mymusic.player.data.local.AppDatabase
import com.mymusic.player.data.local.entity.ListeningHistoryEntity
import com.mymusic.player.data.local.entity.PlaylistEntity
import com.mymusic.player.data.local.entity.PlaylistSongCrossRef
import com.mymusic.player.data.local.entity.SongRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class PlaylistBackupItem(
    val name: String,
    val createdAt: Long,
    val songIds: List<Long>
)

data class BackupPayload(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val songRecords: List<SongRecordEntity> = emptyList(),
    val playlists: List<PlaylistBackupItem> = emptyList(),
    val history: List<ListeningHistoryEntity> = emptyList()
)

class BackupManager(
    private val context: Context,
    private val database: AppDatabase
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportBackup(targetUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val songRecords = database.songDao().getAllRecords()
            val playlists = database.playlistDao().getAllPlaylists()
            val history = database.historyDao().getAllHistory()

            val playlistBackupList = playlists.map { p ->
                val songs = database.playlistDao().getPlaylistSongs(p.id)
                PlaylistBackupItem(
                    name = p.name,
                    createdAt = p.createdAt,
                    songIds = songs.map { it.songId }
                )
            }

            val payload = BackupPayload(
                version = 1,
                exportedAt = System.currentTimeMillis(),
                songRecords = songRecords,
                playlists = playlistBackupList,
                history = history
            )

            val json = gson.toJson(payload)
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                OutputStreamWriter(out).use { writer ->
                    writer.write(json)
                }
            } ?: return@withContext Result.failure(Exception("Failed to open output stream"))

            Result.success(songRecords.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importBackup(sourceUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    reader.readText()
                }
            } ?: return@withContext Result.failure(Exception("Failed to open input stream"))

            val payload = gson.fromJson(jsonString, BackupPayload::class.java)
                ?: return@withContext Result.failure(Exception("Invalid backup file"))

            // Restore song records
            if (payload.songRecords.isNotEmpty()) {
                database.songDao().insertOrUpdateAll(payload.songRecords)
            }

            // Restore playlists
            for (p in payload.playlists) {
                val playlistId = database.playlistDao().insertPlaylist(
                    PlaylistEntity(
                        name = p.name,
                        createdAt = p.createdAt
                    )
                )
                p.songIds.forEachIndexed { index, songId ->
                    database.playlistDao().addSongToPlaylist(
                        PlaylistSongCrossRef(
                            playlistId = playlistId,
                            songId = songId,
                            orderIndex = index
                        )
                    )
                }
            }

            // Restore history
            for (h in payload.history) {
                database.historyDao().insertHistory(
                    h.copy(id = 0) // autogenerate id to avoid conflict
                )
            }

            Result.success(payload.songRecords.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
