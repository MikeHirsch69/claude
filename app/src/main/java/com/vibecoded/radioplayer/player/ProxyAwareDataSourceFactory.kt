package com.vibecoded.radioplayer.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.vibecoded.radioplayer.data.Station
import com.vibecoded.radioplayer.network.ProxyBuilder
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Media3 calls createDataSource() once per stream load. We keep a mutable "active station"
 * reference and build the OkHttp client (with or without a proxy) fresh each time, so each
 * station can use its own proxy setting without needing multiple ExoPlayer instances.
 */
@UnstableApi
class ProxyAwareDataSourceFactory : DataSource.Factory {

    @Volatile private var currentStation: Station? = null

    fun setActiveStation(station: Station?) {
        currentStation = station
    }

    override fun createDataSource(): DataSource {
        val station = currentStation
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)

        if (station != null) {
            clientBuilder.proxy(ProxyBuilder.build(station))
        }

        return OkHttpDataSource.Factory(clientBuilder.build())
            .setUserAgent("VibeCodedRadioPlayer/1.0")
            .createDataSource()
    }
}
