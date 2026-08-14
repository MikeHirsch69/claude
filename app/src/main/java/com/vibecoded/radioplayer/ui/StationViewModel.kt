package com.vibecoded.radioplayer.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vibecoded.radioplayer.RadioApp
import com.vibecoded.radioplayer.backup.BackupManager
import com.vibecoded.radioplayer.data.Folder
import com.vibecoded.radioplayer.data.FolderRepository
import com.vibecoded.radioplayer.data.ProxyType
import com.vibecoded.radioplayer.data.Station
import com.vibecoded.radioplayer.data.StationRepository
import com.vibecoded.radioplayer.util.ImageStorage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StationViewModel(application: Application) : AndroidViewModel(application) {
    private val database = (application as RadioApp).database
    private val stationRepository = StationRepository(database.stationDao())
    private val folderRepository = FolderRepository(database.folderDao())

    val stations: StateFlow<List<Station>> = stationRepository.observeStations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<Folder>> = folderRepository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addStation(
        name: String,
        streamUrl: String,
        logoUri: Uri?,
        logoUrl: String?,
        proxyType: ProxyType,
        proxyHost: String?,
        proxyPort: Int?,
        folderId: Long?
    ) {
        viewModelScope.launch {
            var logoPath: String? = null
            var finalLogoUrl: String? = null
            if (logoUri != null) {
                logoPath = ImageStorage.saveStationLogo(getApplication(), logoUri)
            } else if (!logoUrl.isNullOrBlank()) {
                finalLogoUrl = logoUrl
            }
            val siblingCount = stations.value.count { it.folderId == folderId }
            stationRepository.add(
                Station(
                    name = name,
                    streamUrl = streamUrl,
                    logoPath = logoPath,
                    logoUrl = finalLogoUrl,
                    proxyType = proxyType,
                    proxyHost = proxyHost,
                    proxyPort = proxyPort,
                    folderId = folderId,
                    sortOrder = siblingCount
                )
            )
        }
    }

    fun updateStation(
        station: Station,
        name: String,
        streamUrl: String,
        newLogoUri: Uri?,
        newLogoUrl: String?,
        proxyType: ProxyType,
        proxyHost: String?,
        proxyPort: Int?,
        folderId: Long?
    ) {
        viewModelScope.launch {
            var logoPath = station.logoPath
            var logoUrl = station.logoUrl
            if (newLogoUri != null) {
                ImageStorage.deleteLogo(station.logoPath)
                logoPath = ImageStorage.saveStationLogo(getApplication(), newLogoUri)
                logoUrl = null
            } else if (!newLogoUrl.isNullOrBlank()) {
                ImageStorage.deleteLogo(station.logoPath)
                logoPath = null
                logoUrl = newLogoUrl
            }
            stationRepository.update(
                station.copy(
                    name = name,
                    streamUrl = streamUrl,
                    logoPath = logoPath,
                    logoUrl = logoUrl,
                    proxyType = proxyType,
                    proxyHost = proxyHost,
                    proxyPort = proxyPort,
                    folderId = folderId
                )
            )
        }
    }

    fun deleteStation(station: Station) {
        viewModelScope.launch {
            ImageStorage.deleteLogo(station.logoPath)
            stationRepository.delete(station)
        }
    }

    /** direction: -1 to move up, +1 to move down, among siblings in the same folder. */
    fun moveStation(station: Station, direction: Int) {
        viewModelScope.launch {
            val siblings = stations.value
                .filter { it.folderId == station.folderId }
                .sortedBy { it.sortOrder }
            val index = siblings.indexOfFirst { it.id == station.id }
            val swapIndex = index + direction
            if (index < 0 || swapIndex < 0 || swapIndex >= siblings.size) return@launch
            val a = siblings[index]
            val b = siblings[swapIndex]
            stationRepository.update(a.copy(sortOrder = b.sortOrder))
            stationRepository.update(b.copy(sortOrder = a.sortOrder))
        }
    }

    fun addFolder(name: String) {
        viewModelScope.launch {
            folderRepository.add(Folder(name = name, sortOrder = folders.value.size))
        }
    }

    fun renameFolder(folder: Folder, newName: String) {
        viewModelScope.launch {
            folderRepository.update(folder.copy(name = newName))
        }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch {
            // Move its stations back to "ungrouped" instead of deleting them.
            stations.value.filter { it.folderId == folder.id }.forEach { station ->
                stationRepository.update(station.copy(folderId = null))
            }
            folderRepository.delete(folder)
        }
    }

    /** Persists a full new folder ordering (from drag-and-drop, or a Move Up/Down tap). */
    fun reorderFolders(newOrder: List<Folder>) {
        viewModelScope.launch {
            newOrder.forEachIndexed { index, folder ->
                if (folder.sortOrder != index) {
                    folderRepository.update(folder.copy(sortOrder = index))
                }
            }
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            BackupManager.export(getApplication(), uri, stations.value, folders.value)
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val result = BackupManager.readBackup(getApplication(), uri) ?: return@launch

            val folderIdByName = mutableMapOf<String, Long>()
            folders.value.forEach { folderIdByName[it.name] = it.id }
            result.folderNames.forEach { name ->
                if (!folderIdByName.containsKey(name)) {
                    val newId = folderRepository.add(Folder(name = name, sortOrder = folderIdByName.size))
                    folderIdByName[name] = newId
                }
            }

            result.stations.forEach { imported ->
                val folderId = imported.folderName?.let { name ->
                    folderIdByName[name] ?: run {
                        val newId = folderRepository.add(Folder(name = name, sortOrder = folderIdByName.size))
                        folderIdByName[name] = newId
                        newId
                    }
                }
                val logoPath = imported.logoBase64?.let { BackupManager.decodeLogoToFile(getApplication(), it) }
                stationRepository.add(
                    Station(
                        name = imported.name,
                        streamUrl = imported.streamUrl,
                        logoPath = logoPath,
                        logoUrl = if (logoPath == null) imported.logoUrl else null,
                        proxyType = imported.proxyType,
                        proxyHost = imported.proxyHost,
                        proxyPort = imported.proxyPort,
                        folderId = folderId,
                        sortOrder = stations.value.count { it.folderId == folderId }
                    )
                )
            }
        }
    }
}
