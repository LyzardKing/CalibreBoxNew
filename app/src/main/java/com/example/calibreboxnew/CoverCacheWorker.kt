package com.example.calibreboxnew

// In app/src/main/java/com/example/calibreboxnew/CoverCacheWorker.kt


import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.calibreboxnew.db.DatabaseHelper
import com.example.calibreboxnew.dropbox.DropboxHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class CoverCacheWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            val currentLibrary = SettingsHelper.getCurrentLibrary(context)
            val calibreLibraryPath = currentLibrary?.dropboxPath
            if (calibreLibraryPath == null) {
                Log.e("CoverCacheWorker", "Calibre library path is not set. Cannot run worker.")
                return@withContext Result.failure()
            }
            val libraryId = currentLibrary.id

            // Ensure Dropbox client is initialized. This is crucial for background work.
            if (DropboxHelper.getClient() == null) {
                // Try to initialize from saved credential (with refresh token support)
                if (!DropboxHelper.initFromSavedCredential(context)) {
                    Log.e("CoverCacheWorker", "Dropbox client not initialized. Cannot fetch covers.")
                    return@withContext Result.failure()
                }
            }
            if (DropboxHelper.getClient() == null) {
                Log.e("CoverCacheWorker", "Dropbox client not initialized. Cannot fetch covers.")
                return@withContext Result.failure()
            }

            Log.d("CoverCacheWorker", "Starting background cover caching...")

            // Re-initialize DB to get the book list in the background thread
            DatabaseHelper.init(context, calibreLibraryPath, sharedLinkUrl = currentLibrary.sharedLinkUrl, libraryId = libraryId)
            val books = DatabaseHelper.getBooks()

            if (books.isEmpty()) {
                Log.w("CoverCacheWorker", "No books found in the database. Nothing to cache.")
                return@withContext Result.success()
            }

            var coversCached = 0
            var coversSkipped = 0

            books.forEach { book ->
                // Check if the book has a cover and if it's NOT already in the cache
                if (book.has_cover == true && CoverCacheHelper.getCover(context, libraryId, book.id) == null) {
                    try {
                        val coverPath =
                            "/${calibreLibraryPath.trim('/')}/${book.path}/cover.jpg".replace(
                                "//",
                                "/"
                            )
                        val outputStream = ByteArrayOutputStream()
                        DropboxHelper.downloadFile(context, coverPath, outputStream)
                        val imageBytes = outputStream.toByteArray()

                        if (imageBytes.isNotEmpty()) {
                            val downloadedBitmap =
                                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            if (downloadedBitmap != null) {
                                CoverCacheHelper.saveCover(context, libraryId, book.id, downloadedBitmap)
                                coversCached++
                            }
                        }
                    } catch (e: Exception) {
                        // Log error but continue with the next book
                        Log.e(
                            "CoverCacheWorker",
                            "Failed to cache cover for book '${book.title}' (ID: ${book.id})",
                            e
                        )
                    }
                } else {
                    coversSkipped++
                }
            }

            Log.d(
                "CoverCacheWorker",
                "Background caching finished. Cached: $coversCached, Skipped (already exist): $coversSkipped"
            )
            return@withContext Result.success()
        }
    }
}