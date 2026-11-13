package com.mymusic.player.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mymusic.player.ui.components.MiniPlayer
import com.mymusic.player.ui.screens.duplicates.DuplicatesSheet
import com.mymusic.player.ui.screens.home.HomeScreen
import com.mymusic.player.ui.screens.permission.PermissionScreen
import com.mymusic.player.ui.screens.player.FullPlayerSheet
import com.mymusic.player.ui.screens.player.QueueSheet
import com.mymusic.player.ui.screens.playlists.PlaylistsScreen
import com.mymusic.player.ui.screens.settings.EqualizerSheet
import com.mymusic.player.ui.screens.settings.SettingsScreen
import com.mymusic.player.ui.screens.songs.SongsScreen
import com.mymusic.player.ui.screens.unplayed.UnplayedScreen
import com.mymusic.player.ui.theme.MyMusicTheme
import com.mymusic.player.ui.theme.UnplayedGold

enum class NavDestination(
    val title: String,
    val icon: ImageVector
) {
    HOME("Home", Icons.Default.Home),
    SONGS("Songs", Icons.Default.LibraryMusic),
    UNPLAYED("Unplayed", Icons.Default.RadioButtonUnchecked),
    PLAYLISTS("Playlists", Icons.Default.QueueMusic),
    SETTINGS("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()

            MyMusicTheme(themeMode = themeMode) {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    val stats by viewModel.libraryStats.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    var currentDestination by remember { mutableStateOf(NavDestination.HOME) }
    val isFullPlayerVisible by viewModel.isFullPlayerVisible.collectAsState()
    val isQueueSheetVisible by viewModel.isQueueSheetVisible.collectAsState()
    val isEqualizerSheetVisible by viewModel.isEqualizerSheetVisible.collectAsState()
    val isDuplicatesSheetVisible by viewModel.isDuplicatesSheetVisible.collectAsState()

    // Permission check
    val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[requiredPermission] == true
        hasPermission = audioGranted
        if (audioGranted) {
            viewModel.rescanMusic()
        }
    }

    // Toast/Snackbar notifications
    LaunchedEffect(userMessage) {
        userMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearUserMessage()
        }
    }

    // Ensure notification permission is requested on Android 13+
    LaunchedEffect(hasPermission) {
        if (hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }

    if (!hasPermission) {
        PermissionScreen(
            onRequestPermission = {
                val permissionsToRequest = mutableListOf(requiredPermission)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permissionLauncher.launch(permissionsToRequest.toTypedArray())
            }
        )
        return
    }

    Scaffold(
        bottomBar = {
            Column {
                // MiniPlayer directly above navigation bar
                if (playbackState.currentSong != null) {
                    MiniPlayer(
                        playbackState = playbackState,
                        onPlayPause = { viewModel.playPause() },
                        onSkipNext = { viewModel.skipToNext() },
                        onClick = { viewModel.isFullPlayerVisible.value = true }
                    )
                }

                // Bottom Navigation Bar
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavDestination.values().forEach { destination ->
                        val isSelected = currentDestination == destination

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentDestination = destination },
                            icon = {
                                if (destination == NavDestination.UNPLAYED && stats.unplayedCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge(containerColor = UnplayedGold) {
                                                Text(
                                                    text = "${stats.unplayedCount}",
                                                    color = MaterialTheme.colorScheme.background
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(imageVector = destination.icon, contentDescription = destination.title)
                                    }
                                } else {
                                    Icon(imageVector = destination.icon, contentDescription = destination.title)
                                }
                            },
                            label = { Text(destination.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            when (currentDestination) {
                NavDestination.HOME -> HomeScreen(
                    viewModel = viewModel,
                    onNavigateToUnplayed = { currentDestination = NavDestination.UNPLAYED }
                )
                NavDestination.SONGS -> SongsScreen(viewModel = viewModel)
                NavDestination.UNPLAYED -> UnplayedScreen(viewModel = viewModel)
                NavDestination.PLAYLISTS -> PlaylistsScreen(viewModel = viewModel)
                NavDestination.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
        }
    }

    // Modal Full Player Sheet
    if (isFullPlayerVisible) {
        FullPlayerSheet(
            viewModel = viewModel,
            onDismiss = { viewModel.isFullPlayerVisible.value = false }
        )
    }

    // Modal Queue Sheet
    if (isQueueSheetVisible) {
        QueueSheet(
            viewModel = viewModel,
            onDismiss = { viewModel.isQueueSheetVisible.value = false }
        )
    }

    // Modal Equalizer Sheet
    if (isEqualizerSheetVisible) {
        EqualizerSheet(
            viewModel = viewModel,
            onDismiss = { viewModel.isEqualizerSheetVisible.value = false }
        )
    }

    // Modal Duplicate Songs Finder Sheet
    if (isDuplicatesSheetVisible) {
        DuplicatesSheet(
            viewModel = viewModel,
            onDismiss = { viewModel.isDuplicatesSheetVisible.value = false }
        )
    }
}
