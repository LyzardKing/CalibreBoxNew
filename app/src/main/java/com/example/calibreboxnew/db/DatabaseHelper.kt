package com.example.calibreboxnew.db

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.example.calibreboxnew.dropbox.DropboxHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DatabaseHelper {

    private var driver: AndroidSqliteDriver? = null
    private var database: CalibreMetadata? = null

    suspend fun init(
        context: Context, 
        calibreLibraryPath: String, 
        forceDownload: Boolean = false,
        sharedLinkUrl: String? = null,
        libraryId: String
    ) {
        withContext(Dispatchers.IO) {
            val dbFile = context.getDatabasePath("metadata_$libraryId.db")

            val cleanPath = "/${calibreLibraryPath.trim('/')}/metadata.db".replace("//", "/")
            Log.d("DatabaseHelper", "Preparing to ensure local DB; remote path: '$cleanPath'")

            var shouldDownload = forceDownload || !dbFile.exists() || dbFile.length() == 0L

            if (!shouldDownload) {
                try {
                    val remoteMeta = DropboxHelper.getFileMetadata(cleanPath)
                    if (remoteMeta != null) {
                        val remoteSize = remoteMeta.size
                        val remoteTime = remoteMeta.serverModified.time ?: 0L
                        val localSize = dbFile.length()
                        val localTime = if (dbFile.exists()) dbFile.lastModified() else 0L

                        // If sizes match, or local file is newer/equal to remote modification time, skip download
                        if (localSize == remoteSize || localTime >= remoteTime) {
                            Log.d("DatabaseHelper", "Local DB appears up-to-date (size/time match). Skipping download.")
                            shouldDownload = false
                        } else {
                            Log.d("DatabaseHelper", "Remote DB is newer or different size; will download.")
                            shouldDownload = true
                        }
                    } else {
                        Log.d("DatabaseHelper", "Remote metadata unavailable; will rely on local DB if present.")
                        shouldDownload = false
                    }
                } catch (e: Exception) {
                    Log.w("DatabaseHelper", "Failed to check remote metadata, falling back to local if present", e)
                    shouldDownload = false
                }
            }

            if (shouldDownload) {
                Log.d("DatabaseHelper", "Attempting to download metadata.db from Dropbox path: '$cleanPath'")
                try {
                    dbFile.outputStream().use { outputStream ->
                        if (sharedLinkUrl != null) {
                            Log.d("DatabaseHelper", "Using shared link: $sharedLinkUrl")
                            DropboxHelper.downloadFileFromSharedLink(context, sharedLinkUrl, "metadata.db", outputStream)
                        } else {
                            DropboxHelper.downloadFile(context, cleanPath, outputStream)
                        }
                    }
                    Log.d("DatabaseHelper", "Download complete. File size: ${dbFile.length()} bytes.")
                    if (dbFile.length() == 0L) {
                        Log.e("DatabaseHelper", "DOWNLOADED FILE IS EMPTY! The path '$cleanPath' is likely incorrect.")
                        throw Exception("Downloaded file is empty. The library path may be incorrect or you may not have access to this folder.")
                    }
                } catch (e: Exception) {
                    Log.e("DatabaseHelper", "Failed to download metadata.db from path '$cleanPath'", e)
                    
                    // Provide more specific error messages
                    val errorMessage = when {
                        e.message?.contains("not_found", ignoreCase = true) == true -> 
                            "Library not found at path '$calibreLibraryPath'. Please check the path is correct."
                        e.message?.contains("insufficient_permissions", ignoreCase = true) == true -> 
                            "You don't have permission to access this library. Please ask the owner to share it with you."
                        e.message?.contains("access_error", ignoreCase = true) == true -> 
                            "Unable to access shared library. Make sure you've been granted access."
                        sharedLinkUrl != null -> 
                            "Failed to access shared library. The link may be invalid or expired."
                        else -> 
                            "Failed to download library database. Please check your connection and library path."
                    }
                    
                    throw Exception(errorMessage, e)
                }
            } else {
                Log.d("DatabaseHelper", "Using existing local metadata.db (no download performed).")
            }

            val callback = object : SupportSQLiteOpenHelper.Callback(version = 1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    Log.d("DatabaseHelper", "onCreate called, but DB is pre-populated. Doing nothing.")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    Log.d("DatabaseHelper", "onUpgrade called, but we are not managing versions. Doing nothing.")
                }

                override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    Log.w("DatabaseHelper", "onDowngrade from v$oldVersion to v$newVersion. Allowing it by doing nothing.")
                }
            }

            val newDriver = AndroidSqliteDriver(
                schema = CalibreMetadata.Schema,
                context = context,
                name = "metadata_$libraryId.db",
                callback = callback
            )
            driver = newDriver
            database = CalibreMetadata(newDriver)
            Log.d("DatabaseHelper", "SQLDelight driver initialized successfully.")
        }
    }

    suspend fun reDownloadDatabase(context: Context, calibreLibraryPath: String, sharedLinkUrl: String? = null, libraryId: String) {
        Log.d("DatabaseHelper", "Re-downloading database.")
        clearDatabase(context, libraryId)
        // Re-initialize the database, which will trigger a new download
        init(context, calibreLibraryPath, sharedLinkUrl = sharedLinkUrl, libraryId = libraryId)
    }

    suspend fun clearDatabase(context: Context, libraryId: String? = null) {
        withContext(Dispatchers.IO) {
            // Close the existing database connection if it's open
            driver?.close()
            driver = null
            database = null

            // If libraryId is null, delete all library databases
            if (libraryId == null) {
                val dbDir = context.getDatabasePath("dummy").parentFile
                dbDir?.listFiles { file -> file.name.startsWith("metadata_") && file.name.endsWith(".db") }?.forEach { file ->
                    if (file.delete()) {
                        Log.d("DatabaseHelper", "Deleted database file: ${file.name}")
                    } else {
                        Log.e("DatabaseHelper", "Failed to delete database file: ${file.name}")
                    }
                }
            } else {
                // Delete the specific library database file
                val dbFile = context.getDatabasePath("metadata_$libraryId.db")
                if (dbFile.exists()) {
                    if (dbFile.delete()) {
                        Log.d("DatabaseHelper", "Existing database file deleted.")
                    } else {
                        Log.e("DatabaseHelper", "Failed to delete existing database file.")
                    }
                }
            }
        }
    }

    fun getBooks(): List<GetAllBookDetails> {
        val books = database?.bookQueries?.getAllBookDetails()?.executeAsList() ?: emptyList()
        Log.d("DatabaseHelper", "getBooks() found ${books.size} books using the detailed query.")
        return books
    }
}
