package com.vibecoded.radioplayer.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

object ImageStorage {
    /** Copies the user-picked image into app-private storage so it survives even if the
     * original picker Uri permission is revoked, and returns the absolute file path. */
    fun saveStationLogo(context: Context, sourceUri: Uri): String? {
        return try {
            val dir = File(context.filesDir, "logos").apply { mkdirs() }
            val dest = File(dir, "${UUID.randomUUID()}.png")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun deleteLogo(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            File(path).delete()
        } catch (_: Exception) {
        }
    }
}
