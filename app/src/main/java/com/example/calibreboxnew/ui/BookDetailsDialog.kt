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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Backup
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
import com.example.calibreboxnew.model.Library
import com.example.calibreboxnew.dropbox.DropboxFileProvider
import com.example.calibreboxnew.SettingsHelper
import com.example.calibreboxnew.db.GetAllBookDetails
import com.example.calibreboxnew.dropbox.DropboxHelper
import com.example.calibreboxnew.KoboSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookDetailsDialog(
    book: GetAllBookDetails,
    library: Library,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sendingToKoboFormat by remember { mutableStateOf<String?>(null) }
    val libraryPath = library.dropboxPath

    Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
                modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
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

                // List each format on its own line: <format> <downloadBtn> <shareBtn>
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    book.formatsAndFiles.split("|||SEP|||").forEach { fileInfo ->
                        val parts = fileInfo.split(':', limit = 2)
                        if (parts.size == 2) {
                            val format = parts[0]
                            val fileName = parts[1]

                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                // format label
                                Text(
                                    text = format,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )

                                // Download AssistChip - opens immediately, downloads on-demand
                                AssistChip(
                                    onClick = {
                                        val fullPath = if (library.sharedLinkUrl != null) {
                                            // For shared links, use relative path
                                            "${book.path}/$fileName.${format.lowercase()}"
                                        } else {
                                            "$libraryPath/${book.path}/$fileName.${format.lowercase()}".replace("//", "/")
                                        }
                                        val cleanFileName = fileName.substringAfterLast('/')
                                        openFileWithIntent(
                                            context,
                                            fullPath,
                                            cleanFileName,
                                            format,
                                            library.sharedLinkUrl
                                        )
                                    },
                                    modifier = Modifier.size(36.dp),
                                    label = {},
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = null,
                                            Modifier.size(18.dp)
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                // Share AssistChip - opens immediately, downloads on-demand
                                AssistChip(
                                    onClick = {
                                        val fullPath = if (library.sharedLinkUrl != null) {
                                            // For shared links, use relative path
                                            "${book.path}/$fileName.${format.lowercase()}"
                                        } else {
                                            "$libraryPath/${book.path}/$fileName.${format.lowercase()}".replace("//", "/")
                                        }
                                        val cleanFileName = fileName.substringAfterLast('/')
                                        sendFile(
                                            context,
                                            fullPath,
                                            cleanFileName,
                                            format,
                                            library.sharedLinkUrl
                                        )
                                    },
                                    modifier = Modifier.size(36.dp),
                                    label = {},
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                // Send to Device (Kobo) AssistChip — copies inside Dropbox to /Apps/Rakuten Kobo
                                AssistChip(
                                    onClick = {
                                        if (sendingToKoboFormat == null) {
                                            if (DropboxHelper.getClient() == null) {
                                                Toast.makeText(context, "Please login to Dropbox first", Toast.LENGTH_LONG).show()
                                            } else {
                                                scope.launch {
                                                    sendingToKoboFormat = format
                                                    val fullPath =
                                                        "$libraryPath/${book.path}/$fileName.${format.lowercase()}".replace("//", "/")
                                                    val success = KoboSender.sendToKobo(
                                                        context,
                                                        fullPath
                                                    )
                                                    withContext(Dispatchers.Main) {
                                                        if (success) Toast.makeText(context, "Sent to Kobo via Dropbox", Toast.LENGTH_SHORT).show()
                                                        else Toast.makeText(context, "Failed to send to Kobo", Toast.LENGTH_LONG).show()
                                                    }
                                                    sendingToKoboFormat = null
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(36.dp),
                                    label = {},
                                    leadingIcon = {
                                        if (sendingToKoboFormat == format) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Backup,
                                                contentDescription = null,
                                                Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    enabled = sendingToKoboFormat == null
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom Action
                Button(
                        onClick = onDismissRequest,
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = PaddingValues(horizontal = 24.dp)
                ) { Text("Close") }
            }
        }
    }
}

private fun openFileWithIntent(
        context: Context,
        dropboxPath: String,
        fileName: String,
        extension: String,
        sharedLinkUrl: String?
) {
    try {
        // Better MimeType detection using Android standard MimeTypeMap
        val mimeType =
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                        ?: "application/octet-stream"

        // Create a content URI using the DropboxFileProvider
        // The actual download will happen when the user selects an app and it tries to read the file
        val contentUri = DropboxFileProvider.createUri(
            authority = context.packageName,
            dropboxPath = dropboxPath,
            fileName = fileName,
            format = extension,
            mimeType = mimeType,
            sharedLinkUrl = sharedLinkUrl
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("", contentUri)
        }

        val chooser = Intent.createChooser(intent, "Read with...")
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    } catch (e: Exception) {
        Log.e("BookDetails", "Error opening file: ${e.message}")
        Toast.makeText(context, "Could not open file: ${e.localizedMessage}", Toast.LENGTH_LONG)
                .show()
    }
}

private fun sendFile(
        context: Context,
        dropboxPath: String,
        fileName: String,
        format: String,
        sharedLinkUrl: String?
) {
    try {
        // Try to detect a proper MIME type; fallback to a generic type to maximize available targets
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(format.lowercase()) ?: "*/*"

        // Create a content URI using the DropboxFileProvider
        // The actual download will happen when the user selects an app and it tries to read the file
        val contentUri = DropboxFileProvider.createUri(
            authority = context.packageName,
            dropboxPath = dropboxPath,
            fileName = fileName,
            format = format,
            mimeType = mimeType,
            sharedLinkUrl = sharedLinkUrl
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            clipData = ClipData.newRawUri("", contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Share file")
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    } catch (e: Exception) {
        Log.e("BookDetails", "Error sending file: ${e.message}")
        Toast.makeText(context, "Could not send file: ${e.localizedMessage}", Toast.LENGTH_LONG)
                .show()
    }
}
