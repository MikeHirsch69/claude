package com.vibecoded.radioplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vibecoded.radioplayer.R
import com.vibecoded.radioplayer.data.Folder
import com.vibecoded.radioplayer.data.ProxyType
import com.vibecoded.radioplayer.data.Station
import com.vibecoded.radioplayer.util.StationImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationListScreen(
    folders: List<Folder>,
    stations: List<Station>,
    onPlay: (Station, List<Station>) -> Unit,
    onEdit: (Station) -> Unit,
    onDelete: (Station) -> Unit,
    onMoveStation: (Station, Int) -> Unit,
    onAddStationClick: () -> Unit,
    onAddFolder: (String) -> Unit,
    onRenameFolder: (Folder, String) -> Unit,
    onDeleteFolder: (Folder) -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    var expandedFolders by remember { mutableStateOf(setOf<Long>()) }
    var showFabMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var folderBeingRenamed by remember { mutableStateOf<Folder?>(null) }

    val ungrouped = stations.filter { it.folderId == null }.sortedBy { it.sortOrder }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Radio Stations") },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                            DropdownMenuItem(text = { Text("Backup to file") }, onClick = {
                                showOverflowMenu = false
                                onExportClick()
                            })
                            DropdownMenuItem(text = { Text("Restore from file") }, onClick = {
                                showOverflowMenu = false
                                onImportClick()
                            })
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { showFabMenu = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
                DropdownMenu(expanded = showFabMenu, onDismissRequest = { showFabMenu = false }) {
                    DropdownMenuItem(text = { Text("Add Station") }, onClick = {
                        showFabMenu = false
                        onAddStationClick()
                    })
                    DropdownMenuItem(text = { Text("Add Folder") }, onClick = {
                        showFabMenu = false
                        showAddFolderDialog = true
                    })
                }
            }
        }
    ) { padding ->
        if (folders.isEmpty() && stations.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No stations yet. Tap + to add one.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(folders, key = { "folder_${it.id}" }) { folder ->
                    val folderStations = stations.filter { it.folderId == folder.id }.sortedBy { it.sortOrder }
                    val expanded = expandedFolders.contains(folder.id)
                    FolderHeader(
                        folder = folder,
                        expanded = expanded,
                        onToggle = {
                            expandedFolders = if (expanded) expandedFolders - folder.id else expandedFolders + folder.id
                        },
                        onRename = { folderBeingRenamed = folder },
                        onDelete = { onDeleteFolder(folder) }
                    )
                    if (expanded) {
                        folderStations.forEachIndexed { index, station ->
                            StationRow(
                                station = station,
                                onPlay = { onPlay(station, folderStations) },
                                onEdit = { onEdit(station) },
                                onDelete = { onDelete(station) },
                                onMoveUp = { onMoveStation(station, -1) },
                                onMoveDown = { onMoveStation(station, 1) },
                                canMoveUp = index > 0,
                                canMoveDown = index < folderStations.size - 1,
                                indented = true
                            )
                        }
                    }
                    Divider()
                }
                items(ungrouped, key = { "station_${it.id}" }) { station ->
                    val index = ungrouped.indexOf(station)
                    StationRow(
                        station = station,
                        onPlay = { onPlay(station, ungrouped) },
                        onEdit = { onEdit(station) },
                        onDelete = { onDelete(station) },
                        onMoveUp = { onMoveStation(station, -1) },
                        onMoveDown = { onMoveStation(station, 1) },
                        canMoveUp = index > 0,
                        canMoveDown = index < ungrouped.size - 1,
                        indented = false
                    )
                    Divider()
                }
            }
        }
    }

    if (showAddFolderDialog) {
        FolderNameDialog(
            title = "New Folder",
            initialName = "",
            onDismiss = { showAddFolderDialog = false },
            onConfirm = {
                onAddFolder(it)
                showAddFolderDialog = false
            }
        )
    }

    folderBeingRenamed?.let { folder ->
        FolderNameDialog(
            title = "Rename Folder",
            initialName = folder.name,
            onDismiss = { folderBeingRenamed = null },
            onConfirm = {
                onRenameFolder(folder, it)
                folderBeingRenamed = null
            }
        )
    }
}

@Composable
private fun FolderHeader(
    folder: Folder,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Folder, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(folder.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; onRename() })
                DropdownMenuItem(text = { Text("Delete Folder") }, onClick = { showMenu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun StationRow(
    station: Station,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    indented: Boolean
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (indented) 32.dp else 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = StationImage.modelFor(station),
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .weight(1f)
                .clickable { onPlay() }
        ) {
            Text(station.name, style = MaterialTheme.typography.titleMedium)
            if (station.proxyType != ProxyType.NONE) {
                Text("via proxy", style = MaterialTheme.typography.labelSmall)
            }
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
        }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { showMenu = false; onEdit() })
                if (canMoveUp) {
                    DropdownMenuItem(text = { Text("Move Up") }, onClick = { showMenu = false; onMoveUp() })
                }
                if (canMoveDown) {
                    DropdownMenuItem(text = { Text("Move Down") }, onClick = { showMenu = false; onMoveDown() })
                }
                DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun FolderNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Folder name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
