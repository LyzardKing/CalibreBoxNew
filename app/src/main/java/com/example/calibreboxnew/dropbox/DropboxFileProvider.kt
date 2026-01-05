package com.example.calibreboxnew.dropbox

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import com.example.calibreboxnew.dropbox.DropboxHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException

/**
 * ContentProvider that downloads files from Dropbox on-demand.
 * This allows showing the Intent chooser immediately while deferring
 * the actual download until the user selects an app.
 */
class DropboxFileProvider : ContentProvider() {

    companion object {
        private const val TAG = "DropboxFileProvider"
        private const val AUTHORITY_SUFFIX = ".dropboxprovider"
        
        /**
         * Creates a content URI for a Dropbox file without downloading it.
         */
        fun createUri(
            authority: String,
            dropboxPath: String,
            fileName: String,
            format: String,
            mimeType: String,
            sharedLinkUrl: String? = null
        ): Uri {
            return Uri.parse("content://$authority$AUTHORITY_SUFFIX")
                .buildUpon()
                .appendQueryParameter("path", dropboxPath)
                .appendQueryParameter("fileName", fileName)
                .appendQueryParameter("format", format)
                .appendQueryParameter("mimeType", mimeType)
                .apply {
                    if (sharedLinkUrl != null) {
                        appendQueryParameter("sharedLinkUrl", sharedLinkUrl)
                    }
                }
                .build()
        }
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        Log.d(TAG, "openFile called for URI: $uri")
        
        val context = context ?: throw IllegalStateException("Context is null")
        
        // Extract parameters from URI
        val dropboxPath = uri.getQueryParameter("path")
            ?: throw FileNotFoundException("Missing path parameter")
        val fileName = uri.getQueryParameter("fileName")
            ?: throw FileNotFoundException("Missing fileName parameter")
        val format = uri.getQueryParameter("format")
            ?: throw FileNotFoundException("Missing format parameter")
        val sharedLinkUrl = uri.getQueryParameter("sharedLinkUrl")

        try {
            // Create cache directory
            val booksDir = File(context.cacheDir, "books")
            if (!booksDir.exists()) {
                booksDir.mkdirs()
            }

            val tempFile = File(booksDir, "temp_${fileName}.${format.lowercase()}")

            // Download the file if it doesn't exist or is empty
            if (!tempFile.exists() || tempFile.length() == 0L) {
                Log.d(TAG, "Downloading file from Dropbox: $dropboxPath")
                if (sharedLinkUrl != null) {
                    Log.d(TAG, "Using shared link: $sharedLinkUrl")
                }
                tempFile.outputStream().use { stream ->
                    runBlocking(Dispatchers.IO) {
                        if (sharedLinkUrl != null) {
                            DropboxHelper.downloadFileFromSharedLink(context, sharedLinkUrl, dropboxPath, stream)
                        } else {
                            DropboxHelper.downloadFile(context, dropboxPath, stream)
                        }
                    }
                }
                Log.d(TAG, "Download complete: ${tempFile.length()} bytes")
            } else {
                Log.d(TAG, "Using cached file: ${tempFile.length()} bytes")
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                throw FileNotFoundException("Download failed or file is empty")
            }

            // Return a file descriptor that the target app can read from
            return ParcelFileDescriptor.open(
                tempFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error opening file", e)
            throw FileNotFoundException("Failed to download file: ${e.message}")
        }
    }

    override fun getType(uri: Uri): String {
        // Return the MIME type passed in the URI, or detect from format
        val mimeType = uri.getQueryParameter("mimeType")
        if (mimeType != null) {
            return mimeType
        }
        
        val format = uri.getQueryParameter("format")
        if (format != null) {
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(format.lowercase())
                ?: "application/octet-stream"
        }
        
        return "application/octet-stream"
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        // Extract file info from URI
        val fileName = uri.getQueryParameter("fileName") ?: return null
        val format = uri.getQueryParameter("format") ?: return null
        
        // Build display name with extension
        val displayName = "$fileName.${format.lowercase()}"
        
        // Create cursor with file metadata for sharing apps
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns)
        
        val values = arrayOfNulls<Any>(columns.size)
        for (i in columns.indices) {
            when (columns[i]) {
                OpenableColumns.DISPLAY_NAME -> values[i] = displayName
                OpenableColumns.SIZE -> {
                    // Try to get cached file size, or estimate if not cached
                    val context = context
                    if (context != null) {
                        try {
                            val booksDir = File(context.cacheDir, "books")
                            val tempFile = File(booksDir, "temp_${fileName}.${format.lowercase()}")
                            values[i] = if (tempFile.exists()) tempFile.length() else null
                        } catch (e: Exception) {
                            values[i] = null
                        }
                    } else {
                        values[i] = null
                    }
                }
            }
        }
        cursor.addRow(values)
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("Insert not supported")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("Delete not supported")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("Update not supported")
    }
}
