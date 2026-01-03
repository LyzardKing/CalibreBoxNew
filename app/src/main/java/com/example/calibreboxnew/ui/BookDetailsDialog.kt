package com.example.calibreboxnew.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.calibreboxnew.db.GetAllBookDetails
import com.example.calibreboxnew.SettingsHelper
import com.example.calibreboxnew.dropbox.DropboxHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

@Composable
fun BookDetailsDialog(
    book: GetAllBookDetails,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope() // <-- Define the scope here

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()) // Make content scrollable if it's too long
            ) {
                // Book Title
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Authors
                book.authors?.let { authors ->
                    Text(
                        text = "by $authors",
                        style = MaterialTheme.typography.titleMedium,
                        fontStyle = FontStyle.Italic
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Comment/Description
                book.comment?.let { comment ->
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // A simple way to clean up HTML tags from Calibre comments
                    val cleanComment = comment.replace(Regex("<.*?>"), "")
                    Text(
                        text = cleanComment,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                book.formatsAndFiles.let { formatsAndFiles ->
                    Text(
                        text = "Files",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Split the string into individual file entries, e.g., ["EPUB:file.epub", "MOBI:file.mobi"]
                        // Book names may contain commas, so use a custom delimiter
                        formatsAndFiles.split("|||SEP|||").forEach { fileInfo ->
                            // Split each entry into format and filename, e.g., ["EPUB", "file.epub"]
                            val parts = fileInfo.split(':', limit = 2)
                            if (parts.size == 2) {
                                val format = parts[0]
                                val fileName = parts[1] // <-- This is the REAL filename

                                // FIX: Construct the path with the correct filename
                                val fullPath = "${SettingsHelper.getCalibreLibraryPath(context)}/${book.path}/${fileName}.${format.lowercase()}".replace("//", "/")

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(onClick = {
                                        scope.launch {
                                            openFileWithIntent(context, fullPath, format)
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Download $format"
                                        )
                                    }
                                    Text(
                                        text = format,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Dismiss Button
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

/**
 * Creates an ACTION_VIEW intent to open a file from a URL.
 * Android will show a chooser for apps that can handle the file's MIME type.
 * This is the "Open with..." or "Import" behavior.
 */
private suspend fun openFileWithIntent(context: Context, dropboxPath: String, format: String) {
    // 1. Map the MimeType
//    val mimeType = when (format.lowercase()) {
//        "epub" -> "application/epub+zip"
//        "pdf"  -> "application/pdf"
//        "mobi" -> "application/x-mobipocket-ebook"
//        else   -> "application/octet-stream"
//    }

    try {
        // Prepare the Temp File in Cache
        val tempFile = File(context.cacheDir, "temp_book.${format.lowercase()}")

        // Download and AUTO-CLOSE the stream using .use
        withContext(Dispatchers.IO) {
            // .use ensures the stream is closed even if the download fails
            tempFile.outputStream().use { stream ->
                DropboxHelper.downloadFile(dropboxPath, stream)
            }
        }

        // Verify file was actually written
        if (!tempFile.exists() || tempFile.length() == 0L) {
            throw Exception("File is empty or was not created.")
        }

        // Detect the mimetype automagically
        val mimeType = Files.probeContentType(tempFile.toPath())


        // Get Secure URI
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )

        // Launch Intent on Main Thread
        withContext(Dispatchers.Main) {
            val targetIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // This helps modern apps recognize the permission
                clipData = ClipData.newRawUri("", contentUri)
            }

            val chooser = Intent.createChooser(targetIntent, "Open with...")
            // Grant permission to the chooser wrapper too
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            context.startActivity(chooser)
        }

    } catch (e: Exception) {
        Log.e("DropboxError", "Path attempted: $dropboxPath")
        Log.e("DropboxError", "Error detail: ${e.message}")
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}