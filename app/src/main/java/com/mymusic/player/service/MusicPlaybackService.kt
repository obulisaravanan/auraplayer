package com.mymusic.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mymusic.player.MyMusicApp
import com.mymusic.player.model.EqualizerBand
import com.mymusic.player.model.EqualizerState
import com.mymusic.player.model.PlayedThreshold
import com.mymusic.player.model.Song
import com.mymusic.player.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@UnstableApi
class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    lateinit var player: ExoPlayer

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressTrackingJob: Job? = null
    private var sleepTimerJob: Job? = null

    // Threshold tracking state for currently playing item
    private var currentTrackingSongId: Long = -1L
    private var currentSongThresholdSatisfied: Boolean = false
    private var sessionListenStartTime: Long = 0L

    // Battery-optimized throttled saving
    private var lastSavedPositionMs = 0L
    private var lastSaveTimeMs = 0L

    // Audio effects
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "music_playback_channel"
        const val NOTIFICATION_ID = 1001
        val sleepTimerSecondsRemaining = MutableStateFlow<Long?>(null)
        val currentEqualizerState = MutableStateFlow(EqualizerState())
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        initAudioEffects()

        val activityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(CustomSessionCallback())
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setNotificationId(NOTIFICATION_ID)
                .build()
        )

        setupPlayerListener()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls and current track"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveCurrentPlaybackPosition(force = true)
        // Keep service alive if playing in the background
        if (player.playWhenReady && player.mediaItemCount > 0) {
            return
        }
        // Keep session alive if there is an active queue so user can resume from lockscreen/Bluetooth
        if (player.mediaItemCount > 0) {
            return
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun initAudioEffects() {
        try {
            val audioSessionId = player.audioSessionId
            if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                equalizer = Equalizer(0, audioSessionId).apply {
                    enabled = false
                }
                bassBoost = BassBoost(0, audioSessionId).apply {
                    enabled = false
                }
                updateEqualizerStateFlow()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateEqualizerStateFlow() {
        val eq = equalizer ?: return
        try {
            val numBands = eq.numberOfBands.toInt()
            val minLevel = eq.bandLevelRange[0].toInt()
            val maxLevel = eq.bandLevelRange[1].toInt()

            val bands = (0 until numBands).map { i ->
                EqualizerBand(
                    index = i,
                    centerFreqHz = eq.getCenterFreq(i.toShort()) / 1000,
                    levelMillibels = eq.getBandLevel(i.toShort()).toInt(),
                    minLevel = minLevel,
                    maxLevel = maxLevel
                )
            }

            val presets = (0 until eq.numberOfPresets).map {
                eq.getPresetName(it.toShort())
            }

            currentEqualizerState.value = EqualizerState(
                enabled = eq.enabled,
                currentPreset = if (eq.currentPreset.toInt() >= 0 && eq.currentPreset < eq.numberOfPresets) {
                    eq.getPresetName(eq.currentPreset)
                } else "Custom",
                availablePresets = presets,
                bands = bands,
                bassBoost = bassBoost?.roundedStrength?.toInt() ?: 0
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            updateEqualizerStateFlow()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setEqualizerPreset(presetIndex: Short) {
        try {
            equalizer?.usePreset(presetIndex)
            updateEqualizerStateFlow()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setBandLevel(band: Short, level: Short) {
        try {
            equalizer?.setBandLevel(band, level)
            updateEqualizerStateFlow()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setBassBoostStrength(strength: Short) {
        try {
            bassBoost?.setStrength(strength)
            updateEqualizerStateFlow()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupPlayerListener() {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                saveCurrentPlaybackPosition(force = true)
                val newSongId = mediaItem?.mediaId?.toLongOrNull() ?: -1L
                if (newSongId != currentTrackingSongId) {
                    currentTrackingSongId = newSongId
                    currentSongThresholdSatisfied = false
                    sessionListenStartTime = System.currentTimeMillis()

                    // Auto-fetch album art & tags on first play in the background
                    if (newSongId > 0) {
                        val app = application as? MyMusicApp
                        app?.let { myApp ->
                            serviceScope.launch {
                                val song = myApp.repository.allSongs.value.find { it.id == newSongId }
                                if (song != null) {
                                    myApp.repository.fetchAndApplyOnlineMetadata(song)
                                }
                            }
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                    saveCurrentPlaybackPosition(force = true)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                if (playbackState == Player.STATE_ENDED) {
                    checkAndSatisfyThreshold(isFinished = true)
                    saveCurrentPlaybackPosition(force = true)
                }
            }
        })
    }

    private fun saveCurrentPlaybackPosition(force: Boolean = false) {
        val currentMediaItem = player.currentMediaItem ?: return
        val songId = currentMediaItem.mediaId.toLongOrNull() ?: return
        if (songId <= 0) return

        val now = System.currentTimeMillis()
        val pos = player.currentPosition
        if (force || (now - lastSaveTimeMs >= 15000L && kotlin.math.abs(pos - lastSavedPositionMs) >= 1000L)) {
            lastSaveTimeMs = now
            lastSavedPositionMs = pos
            val app = application as? MyMusicApp ?: return
            serviceScope.launch(Dispatchers.IO) {
                app.repository.updatePlaybackPosition(songId, pos)
            }
        }
    }

    private fun startProgressTracker() {
        progressTrackingJob?.cancel()
        progressTrackingJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                checkAndSatisfyThreshold(isFinished = false)
                saveCurrentPlaybackPosition(force = false)
            }
        }
    }

    private fun stopProgressTracker() {
        progressTrackingJob?.cancel()
        progressTrackingJob = null
    }

    private fun checkAndSatisfyThreshold(isFinished: Boolean) {
        if (currentSongThresholdSatisfied) return
        val currentMediaItem = player.currentMediaItem ?: return
        val songId = currentMediaItem.mediaId.toLongOrNull() ?: return
        if (songId <= 0) return

        val app = application as? MyMusicApp ?: return
        val threshold = app.repository.playedThreshold.value
        val position = player.currentPosition
        val duration = player.duration

        if (threshold.hasSatisfied(position, duration, isFinished)) {
            currentSongThresholdSatisfied = true
            val song = app.repository.allSongs.value.find { it.id == songId }
            if (song != null) {
                val listenedMs = (System.currentTimeMillis() - sessionListenStartTime).coerceAtLeast(1000L)
                serviceScope.launch {
                    app.repository.onPlaybackThresholdSatisfied(song, listenedMs)
                }
            }
        }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            sleepTimerSecondsRemaining.value = null
            return
        }

        sleepTimerJob = serviceScope.launch {
            var remaining = minutes * 60L
            while (remaining > 0 && isActive) {
                sleepTimerSecondsRemaining.value = remaining
                delay(1000)
                remaining--
            }
            sleepTimerSecondsRemaining.value = null
            player.pause()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerSecondsRemaining.value = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        saveCurrentPlaybackPosition(force = true)
        stopProgressTracker()
        sleepTimerJob?.cancel()
        equalizer?.release()
        bassBoost?.release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private inner class CustomSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val sessionCommands = connectionResult.availableSessionCommands.buildUpon()
                .add(SessionCommand("ACTION_START_SLEEP_TIMER", Bundle.EMPTY))
                .add(SessionCommand("ACTION_CANCEL_SLEEP_TIMER", Bundle.EMPTY))
                .add(SessionCommand("ACTION_SET_SPEED", Bundle.EMPTY))
                .add(SessionCommand("ACTION_SET_EQ_ENABLED", Bundle.EMPTY))
                .add(SessionCommand("ACTION_SET_EQ_PRESET", Bundle.EMPTY))
                .add(SessionCommand("ACTION_SET_EQ_BAND", Bundle.EMPTY))
                .add(SessionCommand("ACTION_SET_BASS_BOOST", Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                connectionResult.availablePlayerCommands
            )
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val app = application as? MyMusicApp
            if (app != null) {
                val lastSongId = app.repository.lastPlayedSongId.value
                val lastPos = app.repository.lastPlayedPosition.value
                val allSongs = app.repository.allSongs.value
                val targetSong = allSongs.find { it.id == lastSongId }
                if (targetSong != null) {
                    val mediaItem = MediaItem.Builder()
                        .setMediaId(targetSong.id.toString())
                        .setUri(targetSong.contentUri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(targetSong.title)
                                .setArtist(targetSong.artist)
                                .setAlbumTitle(targetSong.album)
                                .setArtworkUri(targetSong.effectiveArtworkUri?.let { Uri.parse(it) })
                                .build()
                        )
                        .build()
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(
                            listOf(mediaItem),
                            0,
                            lastPos
                        )
                    )
                }
            }
            return Futures.immediateFailedFuture(UnsupportedOperationException())
        }

        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            @Suppress("DEPRECATION")
            val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        if (!player.isPlaying) {
                            player.play()
                            return true
                        }
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        if (player.isPlaying) {
                            player.pause()
                            return true
                        }
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (player.hasNextMediaItem()) {
                            player.seekToNextMediaItem()
                            return true
                        }
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        if (player.currentPosition > 3000L) {
                            player.seekTo(0L)
                        } else if (player.hasPreviousMediaItem()) {
                            player.seekToPreviousMediaItem()
                        } else {
                            player.seekTo(0L)
                        }
                        return true
                    }
                }
            }
            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                "ACTION_START_SLEEP_TIMER" -> {
                    val minutes = args.getInt("minutes", 0)
                    startSleepTimer(minutes)
                }
                "ACTION_CANCEL_SLEEP_TIMER" -> {
                    cancelSleepTimer()
                }
                "ACTION_SET_SPEED" -> {
                    val speed = args.getFloat("speed", 1.0f)
                    player.playbackParameters = PlaybackParameters(speed)
                }
                "ACTION_SET_EQ_ENABLED" -> {
                    val enabled = args.getBoolean("enabled", false)
                    setEqualizerEnabled(enabled)
                }
                "ACTION_SET_EQ_PRESET" -> {
                    val preset = args.getShort("preset", 0)
                    setEqualizerPreset(preset)
                }
                "ACTION_SET_EQ_BAND" -> {
                    val band = args.getShort("band", 0)
                    val level = args.getShort("level", 0)
                    setBandLevel(band, level)
                }
                "ACTION_SET_BASS_BOOST" -> {
                    val strength = args.getShort("strength", 0)
                    setBassBoostStrength(strength)
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }
}
