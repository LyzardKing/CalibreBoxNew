package com.example.calibreboxnew.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.calibreboxnew.db.DatabaseHelper
import com.example.calibreboxnew.dropbox.DropboxHelper
import com.example.calibreboxnew.model.Library
import com.example.calibreboxnew.util.DropboxUrlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun AddLibraryDialog(
    onDismiss: () -> Unit,
    onLibraryAdded: (Library) -> Unit
) {
    var libraryInput by remember { mutableStateOf("") }
    var libraryName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isValidating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Library") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Enter a Dropbox shared URL or folder path:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Text(
                    "Shared links allow anonymous access without adding the folder to your Dropbox account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                OutlinedTextField(
                    value = libraryInput,
                    onValueChange = { 
                        libraryInput = it
                        errorMessage = null
                        // Auto-extract name from path
                        val extracted = DropboxUrlParser.extractPath(it)
                        if (extracted != null && libraryName.isEmpty()) {
                            libraryName = Library.getDisplayNameFromPath(extracted)
                        }
                    },
                    label = { Text("Shared URL or Path") },
                    placeholder = { Text("e.g., /Calibre Library") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null,
                    singleLine = false,
                    maxLines = 3
                )
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = libraryName,
                    onValueChange = { libraryName = it },
                    label = { Text("Library Name") },
                    placeholder = { Text("My Books") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                Text(
                    "Examples:\n• /My Books\n• https://www.dropbox.com/home/Library\n• https://www.dropbox.com/scl/fo/... (shared link)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            
            TextButton(
                onClick = {
                    val trimmedInput = libraryInput.trim()
                    val trimmedName = libraryName.trim()
                    
                    if (trimmedInput.isEmpty()) {
                        errorMessage = "Please enter a path or URL"
                        return@TextButton
                    }
                    
                    if (trimmedName.isEmpty()) {
                        errorMessage = "Please enter a library name"
                        return@TextButton
                    }
                    
                    // Try to extract or resolve path
                    var extractedPath = DropboxUrlParser.extractPath(trimmedInput)
                    var resolvedName = trimmedName
                    var sharedLink: String? = null
                    
                    if (extractedPath == null) {
                        errorMessage = "Invalid path format"
                        return@TextButton
                    }
                    
                    // Check if it's a shared link
                    if (DropboxUrlParser.isSharedLink(extractedPath)) {
                        sharedLink = extractedPath
                        // Try to get the name from metadata
                        resolvedName = if (trimmedName.isEmpty()) "Shared Library" else trimmedName
                        // Use a placeholder path - the actual access will be via shared link
                        extractedPath = "/" + resolvedName.replace(" ", "_")
                    }
                    
                    val finalPath = extractedPath
                    val finalSharedLink = sharedLink
                    val finalName = resolvedName
                    
                    // Start validation
                    isValidating = true
                    errorMessage = null
                    scope.launch {
                        try {
                            // Validate library exists and has metadata.db
                            withContext(Dispatchers.IO) {
                                DatabaseHelper.init(context, finalPath, sharedLinkUrl = finalSharedLink, libraryId = "temp_validation")
                            }
                            
                            // Library is valid
                            val library = Library(
                                id = UUID.randomUUID().toString(),
                                name = finalName,
                                dropboxPath = finalPath,
                                isDefault = false,
                                sharedLinkUrl = finalSharedLink
                            )
                            onLibraryAdded(library)
                        } catch (e: Exception) {
                            isValidating = false
                            errorMessage = when {
                                e.message?.contains("not_found") == true -> 
                                    "Library not found. Please check the path."
                                e.message?.contains("insufficient_permissions") == true || 
                                e.message?.contains("access_error") == true -> 
                                    "Permission denied. Make sure the library is shared with you."
                                else -> 
                                    "Failed to access library: ${e.message}"
                            }
                        }
                    }
                },
                enabled = !isValidating
            ) {
                if (isValidating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isValidating) "Validating..." else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
