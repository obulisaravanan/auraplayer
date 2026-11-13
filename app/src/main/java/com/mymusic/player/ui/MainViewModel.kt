package com.mymusic.player.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mymusic.player.MyMusicApp
import com.mymusic.player.data.preferences.SongSortOrder
import com.mymusic.player.data.preferences.ThemeMode
import com.mymusic.player.model.DuplicateGroup
import com.mymusic.player.model.EqualizerState
import com.mymusic.player.model.LibraryStats
import com.mymusic.player.model.PlaybackState
import com.mymusic.player.model.PlayedThreshold
import com.mymusic.player.model.Playlist
import com.mymusic.player.model.Song
import com.mymusic.player.service.MusicPlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MyMusicApp
    private val repository = app.repository
    val controller = app.musicControllerConnection

    val playbackState: StateFlow<PlaybackState> = controller.playbackState
    val equalizerState: StateFlow<EqualizerState> = MusicPlaybackService.currentEqualizerState

    val playedThreshold: StateFlow<PlayedThreshold> = repository.playedThreshold
    val themeMode: StateFlow<ThemeMode> = repository.themeMode
    val sortOrder: StateFlow<SongSortOrder> = repository.sortOrder
    val sortAscending: StateFlow<Boolean> = repository.sortAscending
    val isScanning: StateFlow<Boolean> = repository.isScanning
    val autoFetchOnlineArtwork: StateFlow<Boolean> = repository.autoFetchOnlineArtwork

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Filtered songs
    val allSongs: StateFlow<List<Song>> = combine(repository.allSongs, _searchQuery) { songs, query ->
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true) ||
            it.album.contains(query, ignoreCase = true) ||
            it.filePath.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val unplayedSongs: StateFlow<List<Song>> = combine(repository.unplayedSongs, _searchQuery) { songs, query ->
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true) ||
            it.album.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val playedSongs: StateFlow<List<Song>> = repository.playedSongs
    val favoriteSongs: StateFlow<List<Song>> = repository.favoriteSongs
    val recentlyPlayedSongs: StateFlow<List<Song>> = repository.recentlyPlayedSongs
    val libraryStats: StateFlow<LibraryStats> = repository.libraryStats
    val playlists: StateFlow<List<Playlist>> = repository.playlists.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList()
    )

    val duplicateGroups: StateFlow<List<DuplicateGroup>> = repository.duplicateGroups

    // UI Sheets State
    val isFullPlayerVisible = MutableStateFlow(false)
    val isQueueSheetVisible = MutableStateFlow(false)
    val isEqualizerSheetVisible = MutableStateFlow(false)
    val isDuplicatesSheetVisible = MutableStateFlow(false)

    // User Message Toast
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun playSong(song: Song, contextList: List<Song>) {
        val index = contextList.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        controller.playSongList(contextList, startIndex = index)
    }

    fun playAllUnplayed(shuffle: Boolean = false) {
        val list = repository.unplayedSongs.value
        if (list.isEmpty()) {
            _userMessage.value = "No unplayed songs remaining!"
            return
        }
        val targetList = if (shuffle) list.shuffled() else list
        controller.playSongList(targetList, startIndex = 0)
    }

    fun playPause() = controller.playPause()
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)
    fun skipToNext() = controller.skipToNext()
    fun skipToPrevious() = controller.skipToPrevious()
    fun toggleShuffle() = controller.toggleShuffle()
    fun toggleRepeatMode() = controller.toggleRepeatMode()
    fun setPlaybackSpeed(speed: Float) = controller.setPlaybackSpeed(speed)

    fun startSleepTimer(minutes: Int) {
        controller.startSleepTimer(minutes)
        _userMessage.value = if (minutes > 0) "Sleep timer set for $minutes minutes" else "Sleep timer cancelled"
    }

    fun cancelSleepTimer() {
        controller.cancelSleepTimer()
        _userMessage.value = "Sleep timer cancelled"
    }

    fun setEqualizerEnabled(enabled: Boolean) = controller.setEqualizerEnabled(enabled)
    fun setEqualizerPreset(presetIndex: Short) = controller.setEqualizerPreset(presetIndex)
    fun setEqualizerBandLevel(band: Short, level: Short) = controller.setEqualizerBandLevel(band, level)
    fun setBassBoost(strength: Short) = controller.setBassBoost(strength)

    fun togglePlayedStatus(song: Song) {
        viewModelScope.launch {
            if (song.isPlayed) {
                repository.markAsUnplayed(song.id)
                _userMessage.value = "Marked \"${song.title}\" as Unplayed"
            } else {
                repository.markAsPlayed(song.id, userMarked = true)
                _userMessage.value = "Marked \"${song.title}\" as Played"
            }
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.toggleFavorite(song.id)
            val isNowFav = !song.isFavorite
            _userMessage.value = if (isNowFav) "Added to Favorites" else "Removed from Favorites"
        }
    }

    fun setPlayedThreshold(threshold: PlayedThreshold) {
        viewModelScope.launch {
            repository.setPlayedThreshold(threshold)
            _userMessage.value = "Played threshold: ${threshold.title}"
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun setSorting(order: SongSortOrder, ascending: Boolean) {
        viewModelScope.launch {
            repository.setSorting(order, ascending)
        }
    }

    fun setAutoFetchOnlineArtwork(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoFetchOnlineArtwork(enabled)
            _userMessage.value = if (enabled) "Online album art auto-fetch enabled" else "Online album art auto-fetch disabled"
        }
    }

    fun fetchMetadataForSong(song: Song) {
        viewModelScope.launch {
            _userMessage.value = "Fetching online artwork for \"${song.title}\"..."
            repository.fetchAndApplyOnlineMetadata(song)
        }
    }

    fun rescanMusic() {
        repository.rescanMusic()
        _userMessage.value = "Scanning local storage for audio..."
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name.trim())
            _userMessage.value = "Created playlist \"$name\""
        }
    }

    fun renamePlaylist(id: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renamePlaylist(id, newName.trim())
            _userMessage.value = "Playlist renamed"
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(id)
            _userMessage.value = "Playlist deleted"
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
            _userMessage.value = "Added to playlist"
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
            _userMessage.value = "Removed from playlist"
        }
    }

    fun resetAllHistory() {
        viewModelScope.launch {
            repository.resetAllHistory()
            _userMessage.value = "Listening history reset. All songs marked unplayed."
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val result = repository.backupManager.exportBackup(uri)
            _userMessage.value = if (result.isSuccess) {
                "Backup exported successfully (${result.getOrNull()} songs)"
            } else {
                "Export failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val result = repository.backupManager.importBackup(uri)
            _userMessage.value = if (result.isSuccess) {
                "Backup restored successfully! Rescanning library..."
            } else {
                "Import failed: ${result.exceptionOrNull()?.message}"
            }
            repository.rescanMusic()
        }
    }

    fun removeQueueItem(index: Int) {
        controller.removeQueueItem(index)
    }

    fun moveQueueItem(from: Int, to: Int) {
        controller.moveQueueItem(from, to)
    }

    fun resolveDuplicateGroup(group: DuplicateGroup, songToKeep: Song, deleteFile: Boolean = false) {
        viewModelScope.launch {
            repository.resolveDuplicateGroup(group, songToKeep.id, deleteFile)
            _userMessage.value = "Kept \"${songToKeep.title}\" and resolved duplicates"
        }
    }

    fun autoCleanAllDuplicates(deleteFiles: Boolean = false) {
        viewModelScope.launch {
            val count = duplicateGroups.value.size
            repository.autoResolveAllDuplicates(deleteFiles)
            _userMessage.value = "Automatically resolved $count duplicate groups (kept best quality)"
        }
    }

    fun hideDuplicateSong(songId: Long) {
        viewModelScope.launch {
            repository.hideDuplicateSong(songId)
            _userMessage.value = "Duplicate removed from library"
        }
    }
}
