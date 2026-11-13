package com.mymusic.player.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.mymusic.player.MyMusicApp
import com.mymusic.player.model.PlaybackState
import com.mymusic.player.model.RepeatMode
import com.mymusic.player.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class MusicControllerConnection(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var currentPlaylistSongs: List<Song> = emptyList()
    private var positionUpdateJob: Job? = null

    init {
        connectToService()
    }

    fun connectToService() {
        val sessionToken = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                setupControllerListener()
                updatePlaybackState()
                restoreLastPlaybackIfIdle()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())

        // Collect sleep timer updates from service
        scope.launch {
            MusicPlaybackService.sleepTimerSecondsRemaining.collect { remaining ->
                _playbackState.value = _playbackState.value.copy(
                    sleepTimerRemainingSeconds = remaining
                )
            }
        }
    }

    private fun startServiceExplicitly() {
        try {
            val intent = Intent(context, MusicPlaybackService::class.java)
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restoreLastPlaybackIfIdle() {
        val controller = mediaController ?: return
        if (controller.mediaItemCount > 0) return

        val app = context.applicationContext as? MyMusicApp ?: return
        scope.launch {
            var attempts = 0
            while (app.repository.allSongs.value.isEmpty() && attempts < 10) {
                delay(200)
                attempts++
            }

            val allSongs = app.repository.allSongs.value
            if (allSongs.isEmpty()) return@launch

            val lastSongId = app.repository.lastPlayedSongId.value
            val lastPos = app.repository.lastPlayedPosition.value
            val lastQueueIds = app.repository.lastPlayedQueueIds.value

            val targetSong = if (lastSongId > 0) allSongs.find { it.id == lastSongId } else allSongs.firstOrNull()
            if (targetSong != null) {
                val map = allSongs.associateBy { it.id }
                val queue = if (lastQueueIds.isNotEmpty()) {
                    val q = lastQueueIds.mapNotNull { map[it] }
                    if (q.any { it.id == targetSong.id }) q else listOf(targetSong)
                } else {
                    listOf(targetSong)
                }
                val index = queue.indexOfFirst { it.id == targetSong.id }.coerceAtLeast(0)
                prepareSongList(queue, startIndex = index, startPositionMs = lastPos)
            }
        }
    }

    private fun restoreAndPlayLast() {
        val app = context.applicationContext as? MyMusicApp ?: return
        scope.launch {
            val allSongs = app.repository.allSongs.value
            if (allSongs.isEmpty()) return@launch

            val lastSongId = app.repository.lastPlayedSongId.value
            val lastPos = app.repository.lastPlayedPosition.value
            val lastQueueIds = app.repository.lastPlayedQueueIds.value

            val targetSong = if (lastSongId > 0) allSongs.find { it.id == lastSongId } else allSongs.firstOrNull()
            if (targetSong != null) {
                val map = allSongs.associateBy { it.id }
                val queue = if (lastQueueIds.isNotEmpty()) {
                    val q = lastQueueIds.mapNotNull { map[it] }
                    if (q.any { it.id == targetSong.id }) q else listOf(targetSong)
                } else {
                    listOf(targetSong)
                }
                val index = queue.indexOfFirst { it.id == targetSong.id }.coerceAtLeast(0)
                playSongList(queue, startIndex = index, startPositionMs = lastPos)
            }
        }
    }

    private fun setupControllerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                super.onEvents(player, events)
                updatePlaybackState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                if (isPlaying) {
                    startPositionUpdates()
                } else {
                    stopPositionUpdates()
                }
                updatePlaybackState()
            }
        })
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = controller.currentPosition,
                        durationMs = controller.duration.coerceAtLeast(0L)
                    )
                }
                delay(500)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun updatePlaybackState() {
        val controller = mediaController ?: return
        val currentMediaItem = controller.currentMediaItem
        val currentSongId = currentMediaItem?.mediaId?.toLongOrNull()
        var currentSong = currentPlaylistSongs.find { it.id == currentSongId }

        // Fallback recovery if controller has item but in-memory playlist was wiped
        if (currentSong == null && currentMediaItem != null && currentSongId != null) {
            val app = context.applicationContext as? MyMusicApp
            currentSong = app?.repository?.allSongs?.value?.find { it.id == currentSongId }
                ?: Song(
                    id = currentSongId,
                    title = currentMediaItem.mediaMetadata.title?.toString() ?: "Unknown",
                    artist = currentMediaItem.mediaMetadata.artist?.toString() ?: "Unknown",
                    album = currentMediaItem.mediaMetadata.albumTitle?.toString() ?: "Unknown",
                    durationMs = controller.duration.coerceAtLeast(0L),
                    filePath = "",
                    contentUri = currentMediaItem.requestMetadata.mediaUri?.toString() ?: "",
                    albumArtUri = currentMediaItem.mediaMetadata.artworkUri?.toString()
                )
            if (currentPlaylistSongs.isEmpty()) {
                val all = app?.repository?.allSongs?.value ?: emptyList()
                val map = all.associateBy { it.id }
                val restored = mutableListOf<Song>()
                for (i in 0 until controller.mediaItemCount) {
                    val id = controller.getMediaItemAt(i).mediaId.toLongOrNull()
                    if (id != null && map.containsKey(id)) {
                        restored.add(map[id]!!)
                    }
                }
                currentPlaylistSongs = if (restored.isNotEmpty()) restored else listOf(currentSong)
            }
        }

        val repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }

        _playbackState.value = _playbackState.value.copy(
            currentSong = currentSong,
            isPlaying = controller.isPlaying,
            currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
            durationMs = controller.duration.coerceAtLeast(0L),
            isBuffering = controller.playbackState == Player.STATE_BUFFERING,
            shuffleModeEnabled = controller.shuffleModeEnabled,
            repeatMode = repeatMode,
            playbackSpeed = controller.playbackParameters.speed,
            queue = currentPlaylistSongs,
            currentQueueIndex = controller.currentMediaItemIndex
        )
    }

    fun prepareSongList(songs: List<Song>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        val controller = mediaController ?: return
        if (songs.isEmpty()) return

        currentPlaylistSongs = songs
        val mediaItems = songs.map { song ->
            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setArtworkUri(song.effectiveArtworkUri?.let { Uri.parse(it) })
                .build()

            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.contentUri)
                .setMediaMetadata(metadata)
                .build()
        }

        controller.setMediaItems(mediaItems, startIndex, startPositionMs)
        controller.prepare()
        updatePlaybackState()
    }

    fun playSongList(songs: List<Song>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        val controller = mediaController ?: return
        if (songs.isEmpty()) return

        startServiceExplicitly()

        currentPlaylistSongs = songs
        val mediaItems = songs.map { song ->
            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setArtworkUri(song.effectiveArtworkUri?.let { Uri.parse(it) })
                .build()

            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.contentUri)
                .setMediaMetadata(metadata)
                .build()
        }

        controller.setMediaItems(mediaItems, startIndex, startPositionMs)
        controller.prepare()
        controller.play()
        updatePlaybackState()

        val app = context.applicationContext as? MyMusicApp
        app?.let { myApp ->
            scope.launch {
                myApp.repository.savePlaybackSession(
                    songId = songs[startIndex].id,
                    positionMs = startPositionMs,
                    queueIds = songs.map { it.id }
                )
            }
        }
    }

    fun playPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.mediaItemCount > 0) {
                startServiceExplicitly()
                controller.play()
            } else {
                restoreAndPlayLast()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
    }

    fun skipToNext() {
        mediaController?.let {
            if (it.hasNextMediaItem()) {
                it.seekToNextMediaItem()
            }
        }
    }

    fun skipToPrevious() {
        mediaController?.let {
            if (it.currentPosition > 3000L) {
                it.seekTo(0L)
            } else if (it.hasPreviousMediaItem()) {
                it.seekToPreviousMediaItem()
            } else {
                it.seekTo(0L)
            }
        }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
        updatePlaybackState()
    }

    fun toggleRepeatMode() {
        val controller = mediaController ?: return
        val nextMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = nextMode
        updatePlaybackState()
    }

    fun setPlaybackSpeed(speed: Float) {
        val controller = mediaController ?: return
        controller.playbackParameters = PlaybackParameters(speed)
        val command = SessionCommand("ACTION_SET_SPEED", Bundle.EMPTY)
        val args = Bundle().apply { putFloat("speed", speed) }
        controller.sendCustomCommand(command, args)
        updatePlaybackState()
    }

    fun startSleepTimer(minutes: Int) {
        val controller = mediaController ?: return
        val command = SessionCommand("ACTION_START_SLEEP_TIMER", Bundle.EMPTY)
        val args = Bundle().apply { putInt("minutes", minutes) }
        controller.sendCustomCommand(command, args)
    }

    fun cancelSleepTimer() {
        val controller = mediaController ?: return
        val command = SessionCommand("ACTION_CANCEL_SLEEP_TIMER", Bundle.EMPTY)
        controller.sendCustomCommand(command, Bundle.EMPTY)
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        val controller = mediaController ?: return
        val command = SessionCommand("ACTION_SET_EQ_ENABLED", Bundle.EMPTY)
        val args = Bundle().apply { putBoolean("enabled", enabled) }
        controller.sendCustomCommand(command, args)
    }

    fun setEqualizerPreset(presetIndex: Short) {
        val controller = mediaController ?: return
        val command = SessionCommand("ACTION_SET_EQ_PRESET", Bundle.EMPTY)
        val args = Bundle().apply { putShort("preset", presetIndex) }
        controller.sendCustomCommand(command, args)
    }

    fun setEqualizerBandLevel(band: Short, level: Short) {
        val controller = mediaController ?: return
        val command = SessionCommand("ACTION_SET_EQ_BAND", Bundle.EMPTY)
        val args = Bundle().apply {
            putShort("band", band)
            putShort("level", level)
        }
        controller.sendCustomCommand(command, args)
    }

    fun setBassBoost(strength: Short) {
        val controller = mediaController ?: return
        val command = SessionCommand("ACTION_SET_BASS_BOOST", Bundle.EMPTY)
        val args = Bundle().apply { putShort("strength", strength) }
        controller.sendCustomCommand(command, args)
    }

    fun removeQueueItem(index: Int) {
        val controller = mediaController ?: return
        if (index in currentPlaylistSongs.indices) {
            controller.removeMediaItem(index)
            val updated = currentPlaylistSongs.toMutableList()
            updated.removeAt(index)
            currentPlaylistSongs = updated
            updatePlaybackState()
        }
    }

    fun moveQueueItem(from: Int, to: Int) {
        val controller = mediaController ?: return
        if (from in currentPlaylistSongs.indices && to in currentPlaylistSongs.indices) {
            controller.moveMediaItem(from, to)
            val updated = currentPlaylistSongs.toMutableList()
            val item = updated.removeAt(from)
            updated.add(to, item)
            currentPlaylistSongs = updated
            updatePlaybackState()
        }
    }

    fun release() {
        stopPositionUpdates()
        mediaController?.release()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
