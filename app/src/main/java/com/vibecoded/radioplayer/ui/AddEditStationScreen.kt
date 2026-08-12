package com.vibecoded.radioplayer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditStationScreen(
    existing: Station?,
    folders: List<Folder>,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        url: String,
        logoUri: Uri?,
        logoUrl: String?,
        proxyType: ProxyType,
        proxyHost: String?,
        proxyPort: Int?,
        folderId: Long?
    ) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var url by remember { mutableStateOf(existing?.streamUrl ?: "") }
    var pickedLogoUri by remember { mutableStateOf<Uri?>(null) }
    var logoUrlText by remember { mutableStateOf(existing?.logoUrl ?: "") }
    var proxyType by remember { mutableStateOf(existing?.proxyType ?: ProxyType.NONE) }
    var proxyHost by remember { mutableStateOf(existing?.proxyHost ?: "") }
    var proxyPort by remember { mutableStateOf(existing?.proxyPort?.toString() ?: "") }
    var selectedFolderId by remember { mutableStateOf(existing?.folderId) }
    var proxyMenuExpanded by remember { mutableStateOf(false) }
    var folderMenuExpanded by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pickedLogoUri = uri
        }
    }

    // What to actually show in the preview circle right now, in priority order.
    val previewModel: Any = pickedLogoUri
        ?: logoUrlText.trim().ifBlank { null }
        ?: existing?.logoPath?.let { File(it) }
        ?: existing?.logoUrl
        ?: R.drawable.ic_radio_placeholder

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add Station" else "Edit Station") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = previewModel,
                        contentDescription = null,
                        modifier = Modifier.size(96.dp).clip(CircleShape)
                    )
                }
            }
            item {
                Button(
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pick Logo From Gallery")
                }
            }
            item {
                OutlinedTextField(
                    value = logoUrlText,
                    onValueChange = {
                        logoUrlText = it
                        pickedLogoUri = null // typing a URL takes priority over a previous gallery pick
                    },
                    label = { Text("...or paste an image URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Station name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Stream URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        readOnly = true,
                        value = folders.firstOrNull { it.id == selectedFolderId }?.name ?: "No folder",
                        onValueChange = {},
                        label = { Text("Folder") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Transparent overlay so the tap always registers, even though the
                    // field underneath is a read-only text field.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { folderMenuExpanded = true }
                    )
                    DropdownMenu(expanded = folderMenuExpanded, onDismissRequest = { folderMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("No folder") }, onClick = {
                            selectedFolderId = null
                            folderMenuExpanded = false
                        })
                        folders.forEach { folder ->
                            DropdownMenuItem(text = { Text(folder.name) }, onClick = {
                                selectedFolderId = folder.id
                                folderMenuExpanded = false
                            })
                        }
                    }
                }
            }
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        readOnly = true,
                        value = proxyType.name,
                        onValueChange = {},
                        label = { Text("Proxy (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { proxyMenuExpanded = true }
                    )
                    DropdownMenu(expanded = proxyMenuExpanded, onDismissRequest = { proxyMenuExpanded = false }) {
                        ProxyType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    proxyType = type
                                    proxyMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            if (proxyType != ProxyType.NONE) {
                item {
                    OutlinedTextField(
                        value = proxyHost,
                        onValueChange = { proxyHost = it },
                        label = { Text("Proxy host") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = proxyPort,
                        onValueChange = { input -> proxyPort = input.filter { it.isDigit() } },
                        label = { Text("Proxy port") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        onSave(
                            name.trim(),
                            url.trim(),
                            pickedLogoUri,
                            logoUrlText.trim().ifBlank { null },
                            proxyType,
                            proxyHost.trim().ifBlank { null },
                            proxyPort.trim().toIntOrNull(),
                            selectedFolderId
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = name.isNotBlank() && url.isNotBlank()
                ) {
                    Text("Save")
                }
            }
        }
    }
}
