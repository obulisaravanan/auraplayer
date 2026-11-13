package com.mymusic.player

import android.app.Application
import com.mymusic.player.data.local.AppDatabase
import com.mymusic.player.data.preferences.UserPreferencesRepository
import com.mymusic.player.data.repository.MusicRepository
import com.mymusic.player.service.MusicControllerConnection

class MyMusicApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferencesRepository: UserPreferencesRepository
        private set

    lateinit var repository: MusicRepository
        private set

    lateinit var musicControllerConnection: MusicControllerConnection
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        preferencesRepository = UserPreferencesRepository(this)
        repository = MusicRepository(this, database, preferencesRepository)
        musicControllerConnection = MusicControllerConnection(this)
    }
}
