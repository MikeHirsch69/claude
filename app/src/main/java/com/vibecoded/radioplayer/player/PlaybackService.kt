package com.vibecoded.radioplayer.player

import android.net.Uri
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
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
import com.vibecoded.radioplayer.data.Station
import com.vibecoded.radioplayer.network.AlbumArtService
import com.vibecoded.radioplayer.util.IcyTitleParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    override fun onCreate() {
        super.onCreate()
        dataSourceFactory = ProxyAwareDataSourceFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
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
        })

        mediaSession = MediaLibrarySession.Builder(this, player, LibraryCallback())
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

    override fun onDestroy() {
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
                val stations = (application as RadioApp).database.stationDao().getAll()
                val items = stations.map { buildMediaItem(it) }
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
            }
            return future
        }

        // Called for both in-app playback requests and taps from the Android Auto browse tree.
        // Resolves a bare MediaItem (just an id/uri) into the full item, and points the
        // proxy-aware data source at the right station before playback starts.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val future = SettableFuture.create<MutableList<MediaItem>>()
            serviceScope.launch {
                val resolved = mediaItems.mapNotNull { item ->
                    val stationId = item.mediaId.removePrefix("station_").toLongOrNull()
                        ?: return@mapNotNull null
                    val station = (application as RadioApp).database.stationDao().getById(stationId)
                        ?: return@mapNotNull null
                    currentStation = station
                    dataSourceFactory.setActiveStation(station)
                    buildMediaItem(station)
                }.toMutableList()
                future.set(resolved)
            }
            return future
        }
    }
}
