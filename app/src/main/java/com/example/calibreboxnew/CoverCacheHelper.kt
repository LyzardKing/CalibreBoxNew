package com.example.calibreboxnew

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CoverCacheHelper {

    private fun getCacheDir(context: Context): File {
        // Create a specific subdirectory for covers to keep things organized
        val cacheDir = File(context.cacheDir, "covers")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            Log.d("CoverCacheHelper", "Created cover cache directory at: ${cacheDir.absolutePath}")
        }
        return cacheDir
    }

    private fun getCoverFile(context: Context, bookId: Long): File {
        // Use the book's unique ID for the filename
        return File(getCacheDir(context), "$bookId.jpg")
    }

    fun saveCover(context: Context, bookId: Long, bitmap: Bitmap) {
        val file = getCoverFile(context, bookId)
        try {
            // Calculate the height to maintain aspect ratio
            // val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
            // val thumbnailHeight = (THUMBNAIL_WIDTH * aspectRatio).toInt()

            // Create a scaled-down version of the bitmap
            // val thumbnailBitmap = bitmap.scale(THUMBNAIL_WIDTH, thumbnailHeight)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                // thumbnailBitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, out)
            }
            Log.d("CoverCacheHelper", "Saved cover for book ID $bookId to cache.")
        } catch (e: Exception) {
            Log.e("CoverCacheHelper", "Error saving cover for book ID $bookId", e)
        }
    }

    suspend fun getCover(context: Context, bookId: Long): Bitmap? {
        val file = getCoverFile(context, bookId)
        return decodeBitmapFromFile(file, "full-res cover for book ID $bookId")
    }

    private suspend fun decodeBitmapFromFile(file: File, logIdentifier: String): Bitmap? =
        withContext(Dispatchers.IO) {
        if (file.exists() && file.length() > 0) {
            try {
                // This call is now safely on the IO dispatcher
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                Log.d("CoverCacheHelper", "Loaded $logIdentifier from cache.")
                bitmap
            } catch (e: Exception) {
                Log.e("CoverCacheHelper", "Error decoding cached $logIdentifier", e)
                null
            }
        } else {
            null // Not in cache
        }
    }
}
