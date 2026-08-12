package com.vibecoded.radioplayer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stations")
data class Station(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val streamUrl: String,
    val logoPath: String? = null, // absolute path to a locally saved logo image (from gallery/backup)
    val logoUrl: String? = null,  // remote image URL, used only if logoPath is null
    val proxyType: ProxyType = ProxyType.NONE,
    val proxyHost: String? = null,
    val proxyPort: Int? = null,
    val folderId: Long? = null,
    val sortOrder: Int = 0
)
