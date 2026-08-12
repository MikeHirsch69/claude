package com.vibecoded.radioplayer.util

import com.vibecoded.radioplayer.R
import com.vibecoded.radioplayer.data.Station
import java.io.File

object StationImage {
    /** Returns a value Coil's AsyncImage can load directly: a File for local logos (more
     * reliable than a raw path string), a URL String for remote logos, or a placeholder. */
    fun modelFor(station: Station): Any {
        val path = station.logoPath
        if (!path.isNullOrBlank()) {
            val file = File(path)
            if (file.exists()) return file
        }
        val url = station.logoUrl
        if (!url.isNullOrBlank()) return url
        return R.drawable.ic_radio_placeholder
    }
}
