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

    suspend fun init(context: Context, calibreLibraryPath: String) {
        withContext(Dispatchers.IO) {
            val dbFile = context.getDatabasePath("metadata.db")

            val cleanPath = "/${calibreLibraryPath.trim('/')}/metadata.db".replace("//", "/")
            Log.d("DatabaseHelper", "Attempting to download from robust Dropbox path: '$cleanPath'")

            try {
                dbFile.outputStream().use { outputStream ->
                    DropboxHelper.downloadFile(cleanPath, outputStream)
                }
                Log.d("DatabaseHelper", "Download complete. File size: ${dbFile.length()} bytes.")
                if (dbFile.length() == 0L) {
                    Log.e("DatabaseHelper", "DOWNLOADED FILE IS EMPTY! The path '$cleanPath' is likely incorrect.")
                    return@withContext
                }
            } catch (e: Exception) {
                Log.e("DatabaseHelper", "Failed to download metadata.db from path '$cleanPath'", e)
                return@withContext
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
                name = "metadata.db",
                callback = callback
            )
            driver = newDriver
            database = CalibreMetadata(newDriver)
            Log.d("DatabaseHelper", "SQLDelight driver initialized successfully.")
        }
    }

    suspend fun reDownloadDatabase(context: Context, calibreLibraryPath: String) {
        withContext(Dispatchers.IO) {
            Log.d("DatabaseHelper", "Re-downloading database.")
            // Close the existing database connection if it's open
            driver?.close()
            driver = null
            database = null

            // Delete the old database file
            val dbFile = context.getDatabasePath("metadata.db")
            if (dbFile.exists()) {
                if (dbFile.delete()) {
                    Log.d("DatabaseHelper", "Existing database file deleted.")
                } else {
                    Log.e("DatabaseHelper", "Failed to delete existing database file.")
                }
            }
        }
        // Re-initialize the database, which will trigger a new download
        init(context, calibreLibraryPath)
    }

    fun getBooks(): List<GetAllBookDetails> {
        val books = database?.bookQueries?.getAllBookDetails()?.executeAsList() ?: emptyList()
        Log.d("DatabaseHelper", "getBooks() found ${books.size} books using the detailed query.")
        return books
    }
}
