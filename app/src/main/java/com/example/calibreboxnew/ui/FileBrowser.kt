package com.example.calibreboxnew.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dropbox.core.v2.files.FolderMetadata
import com.dropbox.core.v2.files.Metadata
import com.example.calibreboxnew.dropbox.DropboxHelper
import kotlinx.coroutines.launch

@Composable
fun FileBrowser(
    modifier: Modifier = Modifier,
    onFolderSelected: (String) -> Unit
) {
    var currentPath by remember { mutableStateOf("") }
    var files by remember { mutableStateOf<List<Metadata>>(emptyList()) }
    var hasCalibreDb by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentPath) {
        scope.launch {
            val result = DropboxHelper.listFolder(currentPath)
            files = result?.entries ?: emptyList()
            hasCalibreDb = files.any { it.name.equals("metadata.db", ignoreCase = true) }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentPath.isNotEmpty()) {
                IconButton(onClick = {
                    currentPath = currentPath.substringBeforeLast("/", "")
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Text(text = currentPath.ifEmpty { "/" }, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.weight(1f))
            if (hasCalibreDb) {
                Button(onClick = { onFolderSelected(currentPath) }) {
                    Icon(Icons.Default.Check, contentDescription = "Select Folder")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Select")
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(files) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (it is FolderMetadata) {
                                currentPath = it.pathDisplay ?: ""
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (it is FolderMetadata) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = it.name)
                }
            }
        }
    }
}
