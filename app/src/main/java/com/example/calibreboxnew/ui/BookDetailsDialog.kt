package com.example.calibreboxnew.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.calibreboxnew.db.GetAllBookDetails
import com.example.calibreboxnew.SettingsHelper
import com.example.calibreboxnew.dropbox.DropboxHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookDetailsDialog(
    book: GetAllBookDetails,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Track which format is currently downloading to show progress
    var downloadingFormat by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(vertical = 24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Title and Authors
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                book.authors?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // Description Section
                book.comment?.let { comment ->
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val cleanComment = comment.replace(Regex("<.*?>"), "").trim()
                    Text(
                        text = cleanComment.ifEmpty { "No description available." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // File Formats Section
                Text(
                    text = "Available Formats",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                // FlowRow handles wrapping if there are many formats
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    book.formatsAndFiles.split("|||SEP|||").forEach { fileInfo ->
                        val parts = fileInfo.split(':', limit = 2)
                        if (parts.size == 2) {
                            val format = parts[0]
                            val fileName = parts[1]
                            val isThisDownloading = downloadingFormat == format

                            AssistChip(
                                onClick = {
                                    if (downloadingFormat == null) {
                                        scope.launch {
                                            downloadingFormat = format
                                            val fullPath = "${SettingsHelper.getCalibreLibraryPath(context)}/${book.path}/$fileName.${format.lowercase()}"
                                                .replace("//", "/")
                                            openFileWithIntent(context, fullPath, format)
                                            downloadingFormat = null
                                        }
                                    }
                                },
                                label = { Text(format) },
                                leadingIcon = {
                                    if (isThisDownloading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(Icons.Default.Download, contentDescription = null, Modifier.size(18.dp))
                                    }
                                },
                                enabled = downloadingFormat == null
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom Action
                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 24.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

private suspend fun openFileWithIntent(context: Context, dropboxPath: String, format: String) {
    try {
        val booksDir = File(context.cacheDir, "books")
        if (!booksDir.exists()) {
            booksDir.mkdirs()
        }

        val tempFile = File(booksDir, "temp_${System.currentTimeMillis()}.${format.lowercase()}")

        withContext(Dispatchers.IO) {
            tempFile.outputStream().use { stream ->
                DropboxHelper.downloadFile(dropboxPath, stream)
            }
        }

        if (!tempFile.exists() || tempFile.length() == 0L) throw Exception("Download failed")

        // Better MimeType detection using Android standard MimeTypeMap
        val extension = MimeTypeMap.getFileExtensionFromUrl(tempFile.absolutePath)
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )

        withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("", contentUri)
            }

            val chooser = Intent.createChooser(intent, "Read with...")
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(chooser)
        }
    } catch (e: Exception) {
        Log.e("BookDetails", "Error opening file: ${e.message}")
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Could not open file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}