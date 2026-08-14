package com.vibecoded.radioplayer.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.vibecoded.radioplayer.RadioApp
import com.vibecoded.radioplayer.data.Folder
import com.vibecoded.radioplayer.data.Station
import com.vibecoded.radioplayer.network.AlbumArtService
import com.vibecoded.radioplayer.ui.MainActivity
import com.vibecoded.radioplayer.util.IcyTitleParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@UnstableApi
class PlaybackService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var dataSourceFactory: ProxyAwareDataSourceFactory
    private val albumArtService = AlbumArtService()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var currentStation: Station? = null
    private var artworkJob: Job? = null

    // Gives up on a stream that never leaves the "buffering/connecting" state.
    private var bufferingTimeoutJob: Job? = null
    private val CONNECT_TIMEOUT_MS = 30_000L

    // Cache of the stations belonging to whatever queue (a folder, or the ungrouped list)
    // is currently loaded into the player, keyed by station id. Lets onMediaItemTransition
    // (fired on manual switches AND on automatic skip-to-next/previous - from the app, the
    // notification, or Android Auto) update the active station instantly, no DB hit needed.
    private var queueStationsById: Map<Long, Station> = emptyMap()

    override fun onCreate() {
        super.onCreate()
        dataSourceFactory = ProxyAwareDataSourceFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus= */ true
            )
            .build()

        player.addListener(object : Player.Listener {
            override fun onMetadata(metadata: Metadata) {
                for (i in 0 until metadata.length()) {
                    val entry = metadata.get(i)
                    if (entry is IcyInfo) {
                        val title = entry.title
                        if (!title.isNullOrBlank()) handleIcyTitle(title)
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId?.removePrefix("station_")?.toLongOrNull() ?: return
                val station = queueStationsById[id] ?: return
                currentStation = station
                dataSourceFactory.setActiveStation(station)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        // (Re)start the 30s connect watchdog. If we're still stuck buffering
                        // when it fires, give up so the stream doesn't hang forever - the
                        // app, the notification, and Android Auto all observe this the same
                        // way (state drops to IDLE with no PlaybackException attached).
                        bufferingTimeoutJob?.cancel()
                        bufferingTimeoutJob = serviceScope.launch {
                            delay(CONNECT_TIMEOUT_MS)
                            if (player.playbackState == Player.STATE_BUFFERING) {
                                player.stop()
                            }
                        }
                    }
                    else -> bufferingTimeoutJob?.cancel()
                }
            }
        })

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            // Tapping the media notification (status bar / lock screen) reopens the app.
            .setSessionActivity(openAppIntent)
            .build()
    }

    /** Called whenever the stream's ICY "now playing" text changes. Updates the notification
     * text immediately, then asynchronously fetches cover art and updates it again. */
    private fun handleIcyTitle(rawTitle: String) {
        artworkJob?.cancel()
        val parsed = IcyTitleParser.parse(rawTitle)
        val station = currentStation ?: return

        updateNowPlayingMetadata(station, parsed.artist, parsed.title, artworkUri = null)

        artworkJob = serviceScope.launch {
            val coverUrl = albumArtService.lookupCoverUrl(parsed.artist, parsed.title)
            if (coverUrl != null) {
                updateNowPlayingMetadata(station, parsed.artist, parsed.title, artworkUri = Uri.parse(coverUrl))
            }
        }
    }

    private fun updateNowPlayingMetadata(station: Station, artist: String?, title: String, artworkUri: Uri?) {
        val current = player.currentMediaItem ?: return
        val newMetadata = current.mediaMetadata.buildUpon()
            .setTitle(title)
            .setArtist(artist ?: station.name)
            .setArtworkUri(artworkUri ?: stationLogoUri(station))
            .build()
        val updatedItem = current.buildUpon().setMediaMetadata(newMetadata).build()
        player.replaceMediaItem(player.currentMediaItemIndex, updatedItem)
    }

    private fun buildMediaItem(station: Station): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(station.name)
            .setArtist("Live Radio")
            .setArtworkUri(stationLogoUri(station))
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build()
        return MediaItem.Builder()
            .setMediaId("station_${station.id}")
            .setUri(station.streamUrl)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun buildFolderItem(folder: Folder): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(folder.name)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .build()
        return MediaItem.Builder()
            .setMediaId("folder_${folder.id}")
            .setMediaMetadata(metadata)
            .build()
    }

    private fun stationLogoUri(station: Station): Uri? {
        val path = station.logoPath
        if (!path.isNullOrBlank()) {
            val file = File(path)
            if (file.exists()) {
                return try {
                    FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                } catch (e: Exception) {
                    null
                }
            }
        }
        val url = station.logoUrl
        if (!url.isNullOrBlank()) {
            return try {
                Uri.parse(url)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = mediaSession

    // Swiping the app away from Recents stops playback entirely instead of leaving it
    // running silently in the background until force-stopped from Settings.
    override fun onTaskRemoved(rootIntent: Intent?) {
        player.stop()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        bufferingTimeoutJob?.cancel()
        artworkJob?.cancel()
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId("root")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setTitle("My Stations")
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        // Root shows folders (browsable) alongside ungrouped stations (playable), same
        // grouping the phone app uses - so Android Auto keeps the folder logic instead of
        // flattening everything into one list.
        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                val db = (application as RadioApp).database
                val items: List<MediaItem> = when {
                    parentId == "root" -> {
                        val folders = db.folderDao().getAll()
                        val allStations = db.stationDao().getAll()
                        val folderItems = folders.map { buildFolderItem(it) }
                        val ungroupedItems = allStations.filter { it.folderId == null }
                            .sortedBy { it.sortOrder }
                            .map { buildMediaItem(it) }
                        folderItems + ungroupedItems
                    }
                    parentId.startsWith("folder_") -> {
                        val folderId = parentId.removePrefix("folder_").toLongOrNull()
                        db.stationDao().getAll()
                            .filter { it.folderId == folderId }
                            .sortedBy { it.sortOrder }
                            .map { buildMediaItem(it) }
                    }
                    else -> emptyList()
                }
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
            }
            return future
        }

        // Resolves a bare/placeholder MediaItem (just a station's mediaId) into the FULL
        // sibling queue - the station's folder, or the ungrouped list - regardless of
        // whether the request came from the in-app player, the notification, or an
        // Android Auto browse tap. This is what makes skip-next/previous (and Auto's skip
        // button) work against a real queue everywhere, using one consistent grouping.
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                val db = (application as RadioApp).database
                val requested = mediaItems.firstOrNull()
                val requestedId = requested?.mediaId?.removePrefix("station_")?.toLongOrNull()

                if (mediaItems.size == 1 && requestedId != null) {
                    val requestedStation = db.stationDao().getById(requestedId)
                    if (requestedStation != null) {
                        val siblings = db.stationDao().getAll()
                            .filter { it.folderId == requestedStation.folderId }
                            .sortedBy { it.sortOrder }
                        val index = siblings.indexOfFirst { it.id == requestedStation.id }.coerceAtLeast(0)
                        queueStationsById = siblings.associateBy { it.id }
                        currentStation = requestedStation
                        dataSourceFactory.setActiveStation(requestedStation)
                        future.set(
                            MediaSession.MediaItemsWithStartPosition(
                                siblings.map { buildMediaItem(it) }, index, startPositionMs
                            )
                        )
                        return@launch
                    }
                }

                // Fallback for any caller that sets an explicit multi-item playlist directly.
                val resolved = mediaItems.mapNotNull { item ->
                    val id = item.mediaId.removePrefix("station_").toLongOrNull() ?: return@mapNotNull null
                    db.stationDao().getById(id)
                }
                queueStationsById = resolved.associateBy { it.id }
                future.set(
                    MediaSession.MediaItemsWithStartPosition(
                        resolved.map { buildMediaItem(it) }, startIndex, startPositionMs
                    )
                )
            }
            return future
        }

        // Kept for controllers that append to the queue (addMediaItem/s) rather than
        // replacing it via setMediaItem(s).
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val future = SettableFuture.create<MutableList<MediaItem>>()
            serviceScope.launch {
                val db = (application as RadioApp).database
                val resolved = mediaItems.mapNotNull { item ->
                    val stationId = item.mediaId.removePrefix("station_").toLongOrNull()
                        ?: return@mapNotNull null
                    val station = db.stationDao().getById(stationId) ?: return@mapNotNull null
                    currentStation = station
                    dataSourceFactory.setActiveStation(station)
                    queueStationsById = queueStationsById + (station.id to station)
                    buildMediaItem(station)
                }.toMutableList()
                future.set(resolved)
            }
            return future
        }
    }
}
