package com.example.calibreboxnew

import android.content.Context
import com.example.calibreboxnew.data.repositories.LibraryRepository
import com.example.calibreboxnew.model.Library

/**
 * Settings helper that delegates library management to LibraryRepository.
 * Kept for backward compatibility during migration.
 */
object SettingsHelper {

    @Deprecated("Use getDefaultLibrary(context)?.dropboxPath", ReplaceWith("LibraryRepository(context).getDefaultLibrary()?.dropboxPath"))
    fun getCalibreLibraryPath(context: Context): String? {
        return LibraryRepository(context).getDefaultLibrary()?.dropboxPath
    }

    // ========== Delegate to LibraryRepository ==========

    fun getLibraries(context: Context): List<Library> {
        return LibraryRepository(context).getLibraries()
    }

    fun addLibrary(context: Context, library: Library): Boolean {
        return LibraryRepository(context).addLibrary(library)
    }

    fun removeLibrary(context: Context, libraryId: String): Boolean {
        return LibraryRepository(context).removeLibrary(libraryId)
    }

    fun updateLibrary(context: Context, library: Library): Boolean {
        return LibraryRepository(context).updateLibrary(library)
    }

    fun setCurrentLibraryId(context: Context, libraryId: String) {
        LibraryRepository(context).setCurrentLibraryId(libraryId)
    }

    fun getCurrentLibrary(context: Context): Library? {
        return LibraryRepository(context).getCurrentLibrary()
    }
}
