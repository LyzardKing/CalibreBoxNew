package com.example.calibreboxnew.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.calibreboxnew.SettingsHelper
import com.example.calibreboxnew.model.Library
import com.example.calibreboxnew.utils.DropboxUrlParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryManagementSheet(
    onDismiss: () -> Unit,
    onLibrariesChanged: () -> Unit
) {
    val context = LocalContext.current
    var libraries by remember { mutableStateOf(SettingsHelper.getLibraries(context)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingLibrary by remember { mutableStateOf<Library?>(null) }
    var deletingLibrary by remember { mutableStateOf<Library?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.8f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Manage Libraries",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Library")
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            if (libraries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Book,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No libraries configured",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(libraries) { library ->
                        LibraryItem(
                            library = library,
                            onEdit = { editingLibrary = library },
                            onDelete = { deletingLibrary = library }
                        )
                    }
                }
            }
        }
    }
    
    // Add Library Dialog
    if (showAddDialog) {
        AddLibraryDialog(
            onDismiss = { showAddDialog = false },
            onLibraryAdded = { library ->
                SettingsHelper.addLibrary(context, library)
                libraries = SettingsHelper.getLibraries(context)
                showAddDialog = false
                onLibrariesChanged()
            }
        )
    }
    
    // Edit Library Dialog
    editingLibrary?.let { library ->
        EditLibraryDialog(
            library = library,
            onDismiss = { editingLibrary = null },
            onLibraryUpdated = { updated ->
                SettingsHelper.updateLibrary(context, updated)
                libraries = SettingsHelper.getLibraries(context)
                editingLibrary = null
                onLibrariesChanged()
            }
        )
    }
    
    // Delete Confirmation Dialog
    deletingLibrary?.let { library ->
        AlertDialog(
            onDismissRequest = { deletingLibrary = null },
            title = { Text("Delete Library?") },
            text = { Text("Are you sure you want to remove \"${library.name}\"? This won't delete any files.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        SettingsHelper.removeLibrary(context, library.id)
                        libraries = SettingsHelper.getLibraries(context)
                        deletingLibrary = null
                        onLibrariesChanged()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingLibrary = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LibraryItem(
    library: Library,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        library.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (library.isDefault) {
                        Spacer(Modifier.width(8.dp))
                        AssistChip(
                            onClick = { },
                            label = { Text("Default", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    library.dropboxPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(
                    onClick = onDelete,
                    enabled = !library.isDefault // Can't delete default library
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = if (library.isDefault) 
                            MaterialTheme.colorScheme.outline 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun EditLibraryDialog(
    library: Library,
    onDismiss: () -> Unit,
    onLibraryUpdated: (Library) -> Unit
) {
    var libraryName by remember { mutableStateOf(library.name) }
    var libraryPath by remember { mutableStateOf(library.dropboxPath) }
    var isDefault by remember { mutableStateOf(library.isDefault) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Library") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = libraryName,
                    onValueChange = { 
                        libraryName = it
                        errorMessage = null
                    },
                    label = { Text("Library Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = libraryPath,
                    onValueChange = { 
                        libraryPath = it
                        errorMessage = null
                    },
                    label = { Text("Dropbox Path") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isDefault,
                        onCheckedChange = { isDefault = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Set as default library")
                }
                
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedName = libraryName.trim()
                    val trimmedPath = libraryPath.trim()
                    
                    if (trimmedName.isEmpty()) {
                        errorMessage = "Please enter a library name"
                        return@TextButton
                    }
                    
                    if (trimmedPath.isEmpty() || !DropboxUrlParser.isValidPath(trimmedPath)) {
                        errorMessage = "Please enter a valid path starting with /"
                        return@TextButton
                    }
                    
                    val updatedLibrary = library.copy(
                        name = trimmedName,
                        dropboxPath = trimmedPath,
                        isDefault = isDefault
                    )
                    
                    onLibraryUpdated(updatedLibrary)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
