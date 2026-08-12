package com.vibecoded.radioplayer.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class AlbumArtService(private val client: OkHttpClient = OkHttpClient()) {

    suspend fun lookupCoverUrl(artist: String?, title: String): String? = withContext(Dispatchers.IO) {
        deezerLookup(artist, title) ?: musicBrainzLookup(artist, title)
    }

    private fun deezerLookup(artist: String?, title: String): String? {
        return try {
            val query = if (!artist.isNullOrBlank()) "$artist $title" else title
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.deezer.com/search?q=$encoded&limit=1"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return null
                if (data.length() == 0) return null
                val track = data.getJSONObject(0)
                val album = track.optJSONObject("album") ?: return null
                val big = album.optString("cover_big")
                if (big.isNotBlank()) return big
                val medium = album.optString("cover_medium")
                medium.ifBlank { null }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun musicBrainzLookup(artist: String?, title: String): String? {
        return try {
            val queryParts = mutableListOf("recording:\"$title\"")
            if (!artist.isNullOrBlank()) queryParts.add("artist:\"$artist\"")
            val query = URLEncoder.encode(queryParts.joinToString(" AND "), "UTF-8")
            val searchUrl = "https://musicbrainz.org/ws/2/recording/?query=$query&fmt=json&limit=1"
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "VibeCodedRadioPlayer/1.0 (contact@example.com)")
                .build()

            val releaseId = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val recordings = json.optJSONArray("recordings") ?: return null
                if (recordings.length() == 0) return null
                val recording = recordings.getJSONObject(0)
                val releases = recording.optJSONArray("releases") ?: return null
                if (releases.length() == 0) return null
                val id = releases.getJSONObject(0).optString("id")
                if (id.isBlank()) null else id
            } ?: return null

            val coverUrl = "https://coverartarchive.org/release/$releaseId/front-500"
            val headRequest = Request.Builder().url(coverUrl).head().build()
            client.newCall(headRequest).execute().use { headResponse ->
                if (headResponse.isSuccessful) coverUrl else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
