package com.example.calibreboxnew

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream

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
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Log.d("CoverCacheHelper", "Saved cover for book ID $bookId to cache.")
        } catch (e: Exception) {
            Log.e("CoverCacheHelper", "Error saving cover for book ID $bookId", e)
        }
    }

    fun getCover(context: Context, bookId: Long): Bitmap? {
        val file = getCoverFile(context, bookId)
        if (file.exists() && file.length() > 0) {
            return try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                Log.d("CoverCacheHelper", "Loaded cover for book ID $bookId from cache.")
                bitmap
            } catch (e: Exception) {
                Log.e("CoverCacheHelper", "Error decoding cached cover for book ID $bookId", e)
                null
            }
        }
        return null // Not in cache
    }
}
