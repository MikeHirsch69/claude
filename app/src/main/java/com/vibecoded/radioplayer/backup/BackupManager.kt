package com.vibecoded.radioplayer.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.vibecoded.radioplayer.data.Folder
import com.vibecoded.radioplayer.data.ProxyType
import com.vibecoded.radioplayer.data.Station
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object BackupManager {

    data class ImportedStation(
        val name: String,
        val streamUrl: String,
        val folderName: String?,
        val logoUrl: String?,
        val logoBase64: String?,
        val proxyType: ProxyType,
        val proxyHost: String?,
        val proxyPort: Int?
    )

    data class ImportResult(
        val folderNames: List<String>,
        val stations: List<ImportedStation>
    )

    /** Writes all stations + folders to the given file Uri as JSON. Local logo images are
     * embedded as base64 so the backup is fully portable; remote logo URLs are stored as-is. */
    fun export(context: Context, uri: Uri, stations: List<Station>, folders: List<Folder>) {
        val folderNamesById = folders.associateBy({ it.id }, { it.name })

        val foldersArray = JSONArray()
        folders.forEach { foldersArray.put(it.name) }

        val stationsArray = JSONArray()
        stations.forEach { station ->
            val obj = JSONObject()
            obj.put("name", station.name)
            obj.put("streamUrl", station.streamUrl)
            obj.put("folder", station.folderId?.let { folderNamesById[it] } ?: JSONObject.NULL)
            obj.put("logoUrl", station.logoUrl ?: JSONObject.NULL)

            val logoBase64 = station.logoPath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    try {
                        Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }
            obj.put("logoBase64", logoBase64 ?: JSONObject.NULL)
            obj.put("proxyType", station.proxyType.name)
            obj.put("proxyHost", station.proxyHost ?: JSONObject.NULL)
            obj.put("proxyPort", station.proxyPort ?: JSONObject.NULL)
            stationsArray.put(obj)
        }

        val root = JSONObject()
        root.put("app", "RadioPlayer")
        root.put("version", 1)
        root.put("folders", foldersArray)
        root.put("stations", stationsArray)

        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(root.toString(2).toByteArray())
        }
    }

    /** Reads a previously exported JSON file back into plain data, without touching the
     * database yet — the caller decides how to merge it in. */
    fun readBackup(context: Context, uri: Uri): ImportResult? {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: return null

        val root = JSONObject(text)
        val foldersArray = root.optJSONArray("folders") ?: JSONArray()
        val folderNames = (0 until foldersArray.length()).map { foldersArray.getString(it) }

        val stationsArray = root.optJSONArray("stations") ?: JSONArray()
        val stations = (0 until stationsArray.length()).map { i ->
            val obj = stationsArray.getJSONObject(i)
            ImportedStation(
                name = obj.optString("name"),
                streamUrl = obj.optString("streamUrl"),
                folderName = obj.optString("folder").takeIf { it.isNotBlank() && it != "null" },
                logoUrl = obj.optString("logoUrl").takeIf { it.isNotBlank() && it != "null" },
                logoBase64 = obj.optString("logoBase64").takeIf { it.isNotBlank() && it != "null" },
                proxyType = try {
                    ProxyType.valueOf(obj.optString("proxyType", "NONE"))
                } catch (e: Exception) {
                    ProxyType.NONE
                },
                proxyHost = obj.optString("proxyHost").takeIf { it.isNotBlank() && it != "null" },
                proxyPort = if (obj.isNull("proxyPort")) null else obj.optInt("proxyPort")
            )
        }
        return ImportResult(folderNames, stations)
    }

    /** Decodes a base64 logo from a backup file into a new local image file, returning its path. */
    fun decodeLogoToFile(context: Context, base64: String): String? {
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val dir = File(context.filesDir, "logos").apply { mkdirs() }
            val dest = File(dir, "${UUID.randomUUID()}.png")
            dest.writeBytes(bytes)
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
