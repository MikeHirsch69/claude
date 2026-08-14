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
import androidx.media3.common.PlaybackException
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

                var playerTitle by remember { mutableStateOf("") }
                var playerArtist by remember { mutableStateOf("") }
                var playerArtwork by remember { mutableStateOf<String?>(null) }
                var isPlaying by remember { mutableStateOf(false) }
                var hasActiveMedia by remember { mutableStateOf(false) }
                var playbackState by remember { mutableStateOf(Player.STATE_IDLE) }
                var statusMessage by remember { mutableStateOf<String?>(null) }

                val stations by viewModel.stations.collectAsState()
                val folders by viewModel.folders.collectAsState()
                val controller = mediaController

                val exportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json")
                ) { uri -> if (uri != null) viewModel.exportBackup(uri) }

                val importLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent()
                ) { uri -> if (uri != null) viewModel.importBackup(uri) }

                // Mirror the playback service's state (title/artist/art/playing/buffering)
                // into the UI - shared by the Now Playing screen and the mini-player bar.
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

                            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                                hasActiveMedia = mediaItem != null
                            }

                            override fun onPlaybackStateChanged(state: Int) {
                                // If we were buffering and dropped straight to IDLE with no
                                // error attached, the service's 30s connect watchdog gave up.
                                if (playbackState == Player.STATE_BUFFERING &&
                                    state == Player.STATE_IDLE &&
                                    controller.playerError == null
                                ) {
                                    statusMessage = "Couldn't connect. The stream timed out after 30 seconds."
                                } else if (state == Player.STATE_READY) {
                                    statusMessage = null
                                }
                                playbackState = state
                            }

                            override fun onPlayerError(error: PlaybackException) {
                                statusMessage = error.message ?: "Playback error."
                            }
                        }
                        controller.addListener(listener)
                        playerTitle = controller.mediaMetadata.title?.toString() ?: ""
                        playerArtist = controller.mediaMetadata.artist?.toString() ?: ""
                        playerArtwork = controller.mediaMetadata.artworkUri?.toString()
                        isPlaying = controller.isPlaying
                        hasActiveMedia = controller.currentMediaItem != null
                        playbackState = controller.playbackState
                        onDispose { controller.removeListener(listener) }
                    } else {
                        onDispose { }
                    }
                }

                fun play(station: Station) {
                    statusMessage = null
                    val mediaItem = MediaItem.Builder()
                        .setMediaId("station_${station.id}")
                        .setUri(station.streamUrl)
                        .build()
                    // The service expands this single placeholder item into the full
                    // sibling queue (its folder, or the ungrouped list) so Next/Previous
                    // work against a real playlist - see PlaybackService.onSetMediaItems.
                    controller?.setMediaItem(mediaItem)
                    controller?.prepare()
                    controller?.play()
                    screen = Screen.PLAYER
                }

                val isBuffering = playbackState == Player.STATE_BUFFERING

                when (screen) {
                    Screen.PLAYER -> {
                        NowPlayingScreen(
                            title = playerTitle,
                            artist = playerArtist,
                            artworkUri = playerArtwork,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            statusMessage = statusMessage,
                            onBack = { screen = Screen.LIST },
                            onPlayPause = {
                                if (controller?.isPlaying == true) controller.pause() else controller?.play()
                            },
                            onNext = { controller?.seekToNext() },
                            onPrevious = { controller?.seekToPrevious() },
                            onRetry = {
                                statusMessage = null
                                controller?.prepare()
                                controller?.play()
                            }
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
                                onPlay = { station -> play(station) },
                                onEdit = { editingStation = it },
                                onDelete = { viewModel.deleteStation(it) },
                                onMoveStation = { station, direction -> viewModel.moveStation(station, direction) },
                                onAddStationClick = { showAddDialog = true },
                                onAddFolder = { viewModel.addFolder(it) },
                                onRenameFolder = { folder, newName -> viewModel.renameFolder(folder, newName) },
                                onDeleteFolder = { viewModel.deleteFolder(it) },
                                onReorderFolders = { viewModel.reorderFolders(it) },
                                onExportClick = { exportLauncher.launch("radio-player-backup.json") },
                                onImportClick = { importLauncher.launch("application/json") },
                                showMiniPlayer = hasActiveMedia,
                                miniTitle = playerTitle,
                                miniArtist = playerArtist,
                                miniArtwork = playerArtwork,
                                miniIsPlaying = isPlaying,
                                miniIsBuffering = isBuffering,
                                onMiniPlayerClick = { screen = Screen.PLAYER },
                                onMiniPlayPause = {
                                    if (controller?.isPlaying == true) controller.pause() else controller?.play()
                                }
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
