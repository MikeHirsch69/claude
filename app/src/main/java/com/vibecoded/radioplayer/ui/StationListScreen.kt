package com.vibecoded.radioplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
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
    onPlay: (Station) -> Unit,
    onEdit: (Station) -> Unit,
    onDelete: (Station) -> Unit,
    onMoveStation: (Station, Int) -> Unit,
    onAddStationClick: () -> Unit,
    onAddFolder: (String) -> Unit,
    onRenameFolder: (Folder, String) -> Unit,
    onDeleteFolder: (Folder) -> Unit,
    onReorderFolders: (List<Folder>) -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    showMiniPlayer: Boolean,
    miniTitle: String,
    miniArtist: String,
    miniArtwork: String?,
    miniIsPlaying: Boolean,
    miniIsBuffering: Boolean,
    onMiniPlayerClick: () -> Unit,
    onMiniPlayPause: () -> Unit
) {
    var expandedFolders by remember { mutableStateOf(setOf<Long>()) }
    var showFabMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var folderBeingRenamed by remember { mutableStateOf<Folder?>(null) }

    // Local working order for drag-and-drop; resynced from the persisted list whenever it
    // changes (e.g. after we ourselves persist a reorder, or another edit happens).
    var folderOrder by remember(folders) { mutableStateOf(folders) }
    var draggingFolderId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val listState = rememberLazyListState()

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
        },
        bottomBar = {
            if (showMiniPlayer) {
                MiniPlayerBar(
                    title = miniTitle,
                    artist = miniArtist,
                    artworkUri = miniArtwork,
                    isPlaying = miniIsPlaying,
                    isBuffering = miniIsBuffering,
                    onClick = onMiniPlayerClick,
                    onPlayPause = onMiniPlayPause
                )
            }
        }
    ) { padding ->
        if (folders.isEmpty() && stations.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No stations yet. Tap + to add one.")
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding)) {
                itemsIndexed(folderOrder, key = { _, folder -> "folder_${folder.id}" }) { index, folder ->
                    val folderStations = stations.filter { it.folderId == folder.id }.sortedBy { it.sortOrder }
                    val expanded = expandedFolders.contains(folder.id)
                    val isDragging = draggingFolderId == folder.id

                    Column(
                        modifier = Modifier
                            .offset { IntOffset(0, if (isDragging) dragOffsetY.roundToInt() else 0) }
                            .zIndex(if (isDragging) 1f else 0f)
                    ) {
                        FolderHeader(
                            folder = folder,
                            expanded = expanded,
                            onToggle = {
                                expandedFolders = if (expanded) expandedFolders - folder.id else expandedFolders + folder.id
                            },
                            onRename = { folderBeingRenamed = folder },
                            onDelete = { onDeleteFolder(folder) },
                            canMoveUp = index > 0,
                            canMoveDown = index < folderOrder.size - 1,
                            onMoveUp = {
                                val newList = folderOrder.toMutableList().apply { add(index - 1, removeAt(index)) }
                                folderOrder = newList
                                onReorderFolders(newList)
                            },
                            onMoveDown = {
                                val newList = folderOrder.toMutableList().apply { add(index + 1, removeAt(index)) }
                                folderOrder = newList
                                onReorderFolders(newList)
                            },
                            dragModifier = Modifier.pointerInput(folder.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        // Collapse while dragging so the item height stays
                                        // predictable and the drag feels smooth.
                                        expandedFolders = expandedFolders - folder.id
                                        draggingFolderId = folder.id
                                        dragOffsetY = 0f
                                    },
                                    onDragEnd = {
                                        draggingFolderId = null
                                        dragOffsetY = 0f
                                        onReorderFolders(folderOrder)
                                    },
                                    onDragCancel = {
                                        draggingFolderId = null
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y
                                        val currentIndex = folderOrder.indexOfFirst { it.id == folder.id }
                                        val itemSize = listState.layoutInfo.visibleItemsInfo
                                            .find { it.key == "folder_${folder.id}" }?.size ?: 0
                                        if (itemSize > 0) {
                                            val moveBy = (dragOffsetY / itemSize).toInt()
                                            if (moveBy != 0 && currentIndex >= 0) {
                                                val targetIndex = (currentIndex + moveBy)
                                                    .coerceIn(0, folderOrder.size - 1)
                                                if (targetIndex != currentIndex) {
                                                    folderOrder = folderOrder.toMutableList().apply {
                                                        add(targetIndex, removeAt(currentIndex))
                                                    }
                                                    dragOffsetY -= moveBy * itemSize
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        )
                        if (expanded) {
                            folderStations.forEachIndexed { stationIndex, station ->
                                StationRow(
                                    station = station,
                                    onPlay = { onPlay(station) },
                                    onEdit = { onEdit(station) },
                                    onDelete = { onDelete(station) },
                                    onMoveUp = { onMoveStation(station, -1) },
                                    onMoveDown = { onMoveStation(station, 1) },
                                    canMoveUp = stationIndex > 0,
                                    canMoveDown = stationIndex < folderStations.size - 1,
                                    indented = true
                                )
                            }
                        }
                        Divider()
                    }
                }
                items(ungrouped, key = { "station_${it.id}" }) { station ->
                    val index = ungrouped.indexOf(station)
                    StationRow(
                        station = station,
                        onPlay = { onPlay(station) },
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
    onDelete: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    dragModifier: Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Long-press-and-drag handle: move a folder up/down in the overview by holding it.
        Icon(
            Icons.Default.DragHandle,
            contentDescription = "Drag to reorder",
            modifier = dragModifier
        )
        Spacer(Modifier.width(8.dp))
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
                if (canMoveUp) {
                    DropdownMenuItem(text = { Text("Move Up") }, onClick = { showMenu = false; onMoveUp() })
                }
                if (canMoveDown) {
                    DropdownMenuItem(text = { Text("Move Down") }, onClick = { showMenu = false; onMoveDown() })
                }
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
