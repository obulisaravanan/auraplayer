package com.mymusic.player.data.repository

import android.content.Context
import com.mymusic.player.data.backup.BackupManager
import com.mymusic.player.data.local.AppDatabase
import com.mymusic.player.data.local.entity.ListeningHistoryEntity
import com.mymusic.player.data.local.entity.PlaylistEntity
import com.mymusic.player.data.local.entity.PlaylistSongCrossRef
import com.mymusic.player.data.duplicates.DuplicateDetector
import com.mymusic.player.data.mediastore.MusicScanner
import com.mymusic.player.data.network.AlbumArtFetcher
import com.mymusic.player.data.preferences.SongSortOrder
import com.mymusic.player.data.preferences.ThemeMode
import com.mymusic.player.data.preferences.UserPreferencesRepository
import com.mymusic.player.model.DuplicateGroup
import com.mymusic.player.model.LibraryStats
import com.mymusic.player.model.PlayedThreshold
import com.mymusic.player.model.Playlist
import com.mymusic.player.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val musicScanner = MusicScanner(context, database.songDao())
    val backupManager = BackupManager(context, database)

    private val _scannedSongs = MutableStateFlow<List<Song>>(emptyList())
    val scannedSongs: StateFlow<List<Song>> = _scannedSongs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val playedThreshold: StateFlow<PlayedThreshold> = preferencesRepository.playedThresholdFlow
        .stateIn(scope, SharingStarted.Eagerly, PlayedThreshold.SECONDS_30)

    val themeMode: StateFlow<ThemeMode> = preferencesRepository.themeModeFlow
        .stateIn(scope, SharingStarted.Eagerly, ThemeMode.DARK)

    val sortOrder: StateFlow<SongSortOrder> = preferencesRepository.sortOrderFlow
        .stateIn(scope, SharingStarted.Eagerly, SongSortOrder.TITLE)

    val sortAscending: StateFlow<Boolean> = preferencesRepository.sortAscendingFlow
        .stateIn(scope, SharingStarted.Eagerly, true)

    val autoFetchOnlineArtwork: StateFlow<Boolean> = preferencesRepository.autoFetchOnlineArtworkFlow
        .stateIn(scope, SharingStarted.Eagerly, true)

    val lastPlayedSongId: StateFlow<Long> = preferencesRepository.lastPlayedSongIdFlow
        .stateIn(scope, SharingStarted.Eagerly, -1L)

    val lastPlayedPosition: StateFlow<Long> = preferencesRepository.lastPlayedPositionFlow
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    val lastPlayedQueueIds: StateFlow<List<Long>> = preferencesRepository.lastPlayedQueueIdsFlow
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Room DB records flow
    private val dbRecordsFlow = database.songDao().getAllRecordsFlow()

    // Combined live songs stream
    val allSongs: StateFlow<List<Song>> = combine(
        _scannedSongs,
        dbRecordsFlow,
        sortOrder,
        sortAscending
    ) { scanned, records, order, ascending ->
        val recordMap = records.associateBy { it.id }
        val updated = scanned.map { s ->
            val record = recordMap[s.id]
            if (record != null) {
                s.copy(
                    album = if (record.album.isNotBlank() && record.album != "Unknown Album") record.album else s.album,
                    isPlayed = record.isPlayed,
                    playCount = record.playCount,
                    firstPlayedTimestamp = record.firstPlayedTimestamp,
                    lastPlayedTimestamp = record.lastPlayedTimestamp,
                    lastPositionMs = record.lastPositionMs,
                    isFavorite = record.isFavorite,
                    userMarkedPlayed = record.userMarkedPlayed,
                    customArtworkPath = record.customArtworkPath,
                    hasFetchedOnlineArtwork = record.hasFetchedOnlineArtwork,
                    isHidden = record.isHidden
                )
            } else {
                s
            }
        }

        val visibleOnly = updated.filter { !it.isHidden }

        // Apply sorting
        val sorted = when (order) {
            SongSortOrder.TITLE -> if (ascending) visibleOnly.sortedBy { it.title.lowercase() } else visibleOnly.sortedByDescending { it.title.lowercase() }
            SongSortOrder.ARTIST -> if (ascending) visibleOnly.sortedBy { it.artist.lowercase() } else visibleOnly.sortedByDescending { it.artist.lowercase() }
            SongSortOrder.ALBUM -> if (ascending) visibleOnly.sortedBy { it.album.lowercase() } else visibleOnly.sortedByDescending { it.album.lowercase() }
            SongSortOrder.DATE_ADDED -> if (ascending) visibleOnly.sortedBy { it.dateAdded } else visibleOnly.sortedByDescending { it.dateAdded }
            SongSortOrder.DURATION -> if (ascending) visibleOnly.sortedBy { it.durationMs } else visibleOnly.sortedByDescending { it.durationMs }
            SongSortOrder.PLAY_COUNT -> if (ascending) visibleOnly.sortedBy { it.playCount } else visibleOnly.sortedByDescending { it.playCount }
        }
        sorted
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Duplicate songs detector stream
    val duplicateGroups: StateFlow<List<DuplicateGroup>> = combine(_scannedSongs, dbRecordsFlow) { scanned, records ->
        val recordMap = records.associateBy { it.id }
        val allWithRecords = scanned.map { s ->
            val record = recordMap[s.id]
            if (record != null) {
                s.copy(
                    album = if (record.album.isNotBlank() && record.album != "Unknown Album") record.album else s.album,
                    isPlayed = record.isPlayed,
                    playCount = record.playCount,
                    firstPlayedTimestamp = record.firstPlayedTimestamp,
                    lastPlayedTimestamp = record.lastPlayedTimestamp,
                    isFavorite = record.isFavorite,
                    customArtworkPath = record.customArtworkPath,
                    isHidden = record.isHidden
                )
            } else s
        }
        DuplicateDetector.findDuplicates(allWithRecords)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // High priority: Unplayed songs list
    val unplayedSongs: StateFlow<List<Song>> = allSongs.combine(playedThreshold) { songs, _ ->
        songs.filter { !it.isPlayed }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val playedSongs: StateFlow<List<Song>> = allSongs.map { songs ->
        songs.filter { it.isPlayed }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val favoriteSongs: StateFlow<List<Song>> = allSongs.map { songs ->
        songs.filter { it.isFavorite }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val recentlyPlayedSongs: StateFlow<List<Song>> = allSongs.map { songs ->
        songs.filter { it.lastPlayedTimestamp != null }
            .sortedByDescending { it.lastPlayedTimestamp }
            .take(30)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val libraryStats: StateFlow<LibraryStats> = allSongs.map { songs ->
        val total = songs.size
        val unplayed = songs.count { !it.isPlayed }
        val played = total - unplayed
        val favs = songs.count { it.isFavorite }
        val totalDur = songs.sumOf { it.durationMs }
        LibraryStats(
            totalSongs = total,
            unplayedCount = unplayed,
            playedCount = played,
            favoritesCount = favs,
            totalDurationMs = totalDur
        )
    }.stateIn(scope, SharingStarted.Eagerly, LibraryStats())

    // Playlists
    val playlists: Flow<List<Playlist>> = database.playlistDao().getAllPlaylistsFlow().combine(allSongs) { playlistEntities, songs ->
        val songMap = songs.associateBy { it.id }
        playlistEntities.map { entity ->
            val crossRefs = database.playlistDao().getPlaylistSongs(entity.id)
            val playlistSongs = crossRefs.mapNotNull { songMap[it.songId] }
            Playlist(
                id = entity.id,
                name = entity.name,
                createdAt = entity.createdAt,
                songCount = playlistSongs.size,
                songs = playlistSongs
            )
        }
    }

    init {
        rescanMusic()
    }

    fun rescanMusic() {
        scope.launch {
            _isScanning.value = true
            try {
                val songs = musicScanner.scanDeviceAudio()
                _scannedSongs.value = songs
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    suspend fun markAsPlayed(songId: Long, userMarked: Boolean = true) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        database.songDao().markPlayedAndIncrementCount(songId, now)
        database.songDao().updatePlayedStatus(songId, true, userMarked)

        val song = _scannedSongs.value.find { it.id == songId }
        if (song != null) {
            database.historyDao().insertHistory(
                ListeningHistoryEntity(
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    timestamp = now,
                    durationListenedMs = song.durationMs,
                    thresholdMet = true
                )
            )
        }
    }

    suspend fun markAsUnplayed(songId: Long) = withContext(Dispatchers.IO) {
        database.songDao().updatePlayedStatus(songId, false, userMarked = false)
    }

    suspend fun toggleFavorite(songId: Long) = withContext(Dispatchers.IO) {
        val current = database.songDao().getRecordById(songId)
        val newFav = !(current?.isFavorite ?: false)
        database.songDao().updateFavorite(songId, newFav)
    }

    suspend fun onPlaybackThresholdSatisfied(song: Song, durationListenedMs: Long) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        database.songDao().markPlayedAndIncrementCount(song.id, now)
        database.historyDao().insertHistory(
            ListeningHistoryEntity(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                timestamp = now,
                durationListenedMs = durationListenedMs,
                thresholdMet = true
            )
        )
    }

    suspend fun updatePlaybackPosition(songId: Long, positionMs: Long) = withContext(Dispatchers.IO) {
        database.songDao().updatePlaybackPosition(songId, positionMs)
        preferencesRepository.saveLastPlayback(songId, positionMs)
    }

    suspend fun savePlaybackSession(songId: Long, positionMs: Long, queueIds: List<Long>? = null) = withContext(Dispatchers.IO) {
        database.songDao().updatePlaybackPosition(songId, positionMs)
        preferencesRepository.saveLastPlayback(songId, positionMs, queueIds)
    }

    suspend fun setPlayedThreshold(threshold: PlayedThreshold) {
        preferencesRepository.setPlayedThreshold(threshold)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        preferencesRepository.setThemeMode(mode)
    }

    suspend fun setSorting(order: SongSortOrder, ascending: Boolean) {
        preferencesRepository.setSortOrder(order, ascending)
    }

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        database.playlistDao().insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun renamePlaylist(id: Long, newName: String) = withContext(Dispatchers.IO) {
        database.playlistDao().renamePlaylist(id, newName)
    }

    suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) {
        database.playlistDao().deletePlaylist(id)
        database.playlistDao().clearPlaylistSongs(id)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        val currentSongs = database.playlistDao().getPlaylistSongs(playlistId)
        val order = currentSongs.size
        database.playlistDao().addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId, order))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        database.playlistDao().removeSongFromPlaylist(playlistId, songId)
    }

    suspend fun resetAllHistory() = withContext(Dispatchers.IO) {
        database.songDao().resetAllListeningHistory()
        database.historyDao().clearAllHistory()
    }

    suspend fun setAutoFetchOnlineArtwork(enabled: Boolean) {
        preferencesRepository.setAutoFetchOnlineArtwork(enabled)
    }

    suspend fun fetchAndApplyOnlineMetadata(song: Song) = withContext(Dispatchers.IO) {
        if (!autoFetchOnlineArtwork.value) return@withContext
        if (song.hasFetchedOnlineArtwork && !song.customArtworkPath.isNullOrBlank()) return@withContext

        try {
            val result = AlbumArtFetcher.fetchArtworkAndMetadata(
                context = context,
                songId = song.id,
                rawTitle = song.title,
                rawArtist = song.artist
            )

            if (result != null) {
                database.songDao().updateArtworkAndMetadata(
                    id = song.id,
                    customArtworkPath = result.artworkFilePath,
                    album = result.albumName ?: song.album,
                    hasFetched = true
                )
            } else {
                database.songDao().updateArtworkAndMetadata(
                    id = song.id,
                    customArtworkPath = song.customArtworkPath,
                    album = song.album,
                    hasFetched = true
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun hideDuplicateSong(songId: Long) = withContext(Dispatchers.IO) {
        database.songDao().updateHiddenStatus(songId, true)
    }

    suspend fun resolveDuplicateGroup(
        group: DuplicateGroup,
        songToKeepId: Long,
        deleteFiles: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val songToKeep = group.songs.find { it.id == songToKeepId } ?: return@withContext
        val duplicatesToRemove = group.songs.filter { it.id != songToKeepId }

        // Merge stats from duplicates into songToKeep
        val addedPlayCount = duplicatesToRemove.sumOf { it.playCount }
        val anyPlayed = duplicatesToRemove.any { it.isPlayed } || songToKeep.isPlayed
        val anyFav = duplicatesToRemove.any { it.isFavorite } || songToKeep.isFavorite

        database.songDao().mergeListeningHistory(
            targetId = songToKeep.id,
            addedCount = addedPlayCount,
            isPlayed = anyPlayed,
            isFavorite = anyFav
        )

        // Remove or hide extra duplicates
        for (dup in duplicatesToRemove) {
            if (deleteFiles) {
                try {
                    val file = java.io.File(dup.filePath)
                    if (file.exists()) file.delete()
                    context.contentResolver.delete(android.net.Uri.parse(dup.contentUri), null, null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            database.songDao().updateHiddenStatus(dup.id, true)
        }
    }

    suspend fun autoResolveAllDuplicates(deleteFiles: Boolean = false) = withContext(Dispatchers.IO) {
        val groups = duplicateGroups.value
        for (group in groups) {
            val bestSong = group.recommendedSong
            resolveDuplicateGroup(group, bestSong.id, deleteFiles)
        }
    }
}
