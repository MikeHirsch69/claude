package com.vibecoded.radioplayer.ui

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.vibecoded.radioplayer.data.Station
import com.vibecoded.radioplayer.player.PlaybackService

@UnstableApi
class MainActivity : ComponentActivity() {

    private val viewModel: StationViewModel by viewModels()
    private var mediaController by mutableStateOf<MediaController?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private enum class Screen { LIST, PLAYER }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener(
            { mediaController = controllerFuture?.get() },
            MoreExecutors.directExecutor()
        )

        setContent {
            RadioPlayerTheme {
                var screen by remember { mutableStateOf(Screen.LIST) }
                var editingStation by remember { mutableStateOf<Station?>(null) }
                var showAddDialog by remember { mutableStateOf(false) }

                var currentPlaylist by remember { mutableStateOf(listOf<Station>()) }
                var currentIndex by remember { mutableStateOf(0) }

                var playerTitle by remember { mutableStateOf("") }
                var playerArtist by remember { mutableStateOf("") }
                var playerArtwork by remember { mutableStateOf<String?>(null) }
                var isPlaying by remember { mutableStateOf(false) }

                val stations by viewModel.stations.collectAsState()
                val folders by viewModel.folders.collectAsState()
                val controller = mediaController

                val exportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json")
                ) { uri -> if (uri != null) viewModel.exportBackup(uri) }

                val importLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent()
                ) { uri -> if (uri != null) viewModel.importBackup(uri) }

                // Mirror the playback service's state (title/artist/art/playing) into the UI.
                DisposableEffect(controller) {
                    if (controller != null) {
                        val listener = object : Player.Listener {
                            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                                playerTitle = mediaMetadata.title?.toString() ?: ""
                                playerArtist = mediaMetadata.artist?.toString() ?: ""
                                playerArtwork = mediaMetadata.artworkUri?.toString()
                            }

                            override fun onIsPlayingChanged(playing: Boolean) {
                                isPlaying = playing
                            }
                        }
                        controller.addListener(listener)
                        playerTitle = controller.mediaMetadata.title?.toString() ?: ""
                        playerArtist = controller.mediaMetadata.artist?.toString() ?: ""
                        playerArtwork = controller.mediaMetadata.artworkUri?.toString()
                        isPlaying = controller.isPlaying
                        onDispose { controller.removeListener(listener) }
                    } else {
                        onDispose { }
                    }
                }

                fun playAt(index: Int, playlist: List<Station>) {
                    if (playlist.isEmpty()) return
                    val safeIndex = ((index % playlist.size) + playlist.size) % playlist.size
                    currentPlaylist = playlist
                    currentIndex = safeIndex
                    val station = playlist[safeIndex]
                    val mediaItem = MediaItem.Builder()
                        .setMediaId("station_${station.id}")
                        .setUri(station.streamUrl)
                        .build()
                    controller?.setMediaItem(mediaItem)
                    controller?.prepare()
                    controller?.play()
                    screen = Screen.PLAYER
                }

                when (screen) {
                    Screen.PLAYER -> {
                        NowPlayingScreen(
                            title = playerTitle.ifBlank { currentPlaylist.getOrNull(currentIndex)?.name ?: "" },
                            artist = playerArtist,
                            artworkUri = playerArtwork,
                            isPlaying = isPlaying,
                            onBack = { screen = Screen.LIST },
                            onPlayPause = {
                                if (controller?.isPlaying == true) controller.pause() else controller?.play()
                            },
                            onNext = { playAt(currentIndex + 1, currentPlaylist) },
                            onPrevious = { playAt(currentIndex - 1, currentPlaylist) }
                        )
                    }

                    Screen.LIST -> {
                        if (showAddDialog || editingStation != null) {
                            AddEditStationScreen(
                                existing = editingStation,
                                folders = folders,
                                onDismiss = {
                                    showAddDialog = false
                                    editingStation = null
                                },
                                onSave = { stationName, streamUrl, logoUri, logoUrl, proxyType, proxyHost, proxyPort, folderId ->
                                    val current = editingStation
                                    if (current == null) {
                                        viewModel.addStation(
                                            stationName, streamUrl, logoUri, logoUrl,
                                            proxyType, proxyHost, proxyPort, folderId
                                        )
                                    } else {
                                        viewModel.updateStation(
                                            current, stationName, streamUrl, logoUri, logoUrl,
                                            proxyType, proxyHost, proxyPort, folderId
                                        )
                                    }
                                    showAddDialog = false
                                    editingStation = null
                                }
                            )
                        } else {
                            StationListScreen(
                                folders = folders,
                                stations = stations,
                                onPlay = { station, playlist -> playAt(playlist.indexOf(station), playlist) },
                                onEdit = { editingStation = it },
                                onDelete = { viewModel.deleteStation(it) },
                                onMoveStation = { station, direction -> viewModel.moveStation(station, direction) },
                                onAddStationClick = { showAddDialog = true },
                                onAddFolder = { viewModel.addFolder(it) },
                                onRenameFolder = { folder, newName -> viewModel.renameFolder(folder, newName) },
                                onDeleteFolder = { viewModel.deleteFolder(it) },
                                onExportClick = { exportLauncher.launch("radio-player-backup.json") },
                                onImportClick = { importLauncher.launch("application/json") }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}
