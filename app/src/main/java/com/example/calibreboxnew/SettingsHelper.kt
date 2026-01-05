package com.example.calibreboxnew

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.example.calibreboxnew.model.Library
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

object SettingsHelper {

    private const val PREFS_NAME = "CalibreBoxPrefs"
    private const val KEY_CALIBRE_PATH = "calibre_library_path" // Legacy single library
    private const val KEY_LIBRARIES = "libraries" // New multi-library list
    private const val KEY_CURRENT_LIBRARY_ID = "current_library_id"

    private val json = Json { ignoreUnknownKeys = true }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ========== Legacy Methods (for backward compatibility) ==========

    @Deprecated("Use getLibraries() instead", ReplaceWith("getDefaultLibrary(context)?.dropboxPath"))
    fun getCalibreLibraryPath(context: Context): String? {
        // First check if we have migrated to multi-library
        val libraries = getLibraries(context)
        if (libraries.isNotEmpty()) {
            return getDefaultLibrary(context)?.dropboxPath
        }
        
        // Otherwise, return legacy path
        val path = getPrefs(context).getString(KEY_CALIBRE_PATH, null)
        Log.d("SettingsHelper", "Loaded legacy Calibre library path: $path")
        return path
    }

    @Deprecated("Use addLibrary() instead", ReplaceWith("addLibrary(context, Library(...))"))
    fun saveCalibreLibraryPath(context: Context, path: String) {
        Log.d("SettingsHelper", "Saving Calibre library path: $path")
        getPrefs(context).edit { putString(KEY_CALIBRE_PATH, path) }
    }

    @Deprecated("Use removeLibrary() instead")
    fun deleteCalibreLibraryPath(context: Context) {
        getPrefs(context).edit { remove(KEY_CALIBRE_PATH) }
        Log.d("SettingsHelper", "Deleted Calibre library path")
    }

    // ========== Multi-Library Methods ==========

    /**
     * Migrates legacy single library to multi-library format if needed.
     */
    private fun migrateIfNeeded(context: Context) {
        val prefs = getPrefs(context)
        val libraries = prefs.getString(KEY_LIBRARIES, null)
        
        // Already migrated
        if (libraries != null) return
        
        // Check for legacy path
        val legacyPath = prefs.getString(KEY_CALIBRE_PATH, null)
        if (legacyPath != null) {
            Log.d("SettingsHelper", "Migrating legacy library path to multi-library format")
            val library = Library(
                id = UUID.randomUUID().toString(),
                name = Library.getDisplayNameFromPath(legacyPath),
                dropboxPath = legacyPath,
                isDefault = true
            )
            saveLibraries(context, listOf(library))
            setCurrentLibraryId(context, library.id)
            // Keep legacy path for now for backward compatibility
        }
    }

    /**
     * Gets all configured libraries.
     */
    fun getLibraries(context: Context): List<Library> {
        migrateIfNeeded(context)
        
        val librariesJson = getPrefs(context).getString(KEY_LIBRARIES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<Library>>(librariesJson)
        } catch (e: Exception) {
            Log.e("SettingsHelper", "Error parsing libraries", e)
            emptyList()
        }
    }

    /**
     * Saves the list of libraries.
     */
    private fun saveLibraries(context: Context, libraries: List<Library>) {
        val librariesJson = json.encodeToString(libraries)
        getPrefs(context).edit { putString(KEY_LIBRARIES, librariesJson) }
        Log.d("SettingsHelper", "Saved ${libraries.size} libraries")
    }

    /**
     * Adds a new library to the list.
     */
    fun addLibrary(context: Context, library: Library): Boolean {
        val libraries = getLibraries(context).toMutableList()
        
        // Check if path already exists
        if (libraries.any { it.dropboxPath == library.dropboxPath }) {
            Log.w("SettingsHelper", "Library with path ${library.dropboxPath} already exists")
            return false
        }
        
        // If this is the first library, make it default
        val libraryToAdd = if (libraries.isEmpty()) {
            library.copy(isDefault = true)
        } else {
            library
        }
        
        libraries.add(libraryToAdd)
        saveLibraries(context, libraries)
        
        // Set as current if first library
        if (libraries.size == 1) {
            setCurrentLibraryId(context, libraryToAdd.id)
        }
        
        return true
    }

    /**
     * Removes a library by ID.
     */
    fun removeLibrary(context: Context, libraryId: String): Boolean {
        val libraries = getLibraries(context).toMutableList()
        val removed = libraries.removeIf { it.id == libraryId }
        
        if (removed) {
            // If removed library was default, make first library default
            if (libraries.isNotEmpty() && libraries.none { it.isDefault }) {
                libraries[0] = libraries[0].copy(isDefault = true)
            }
            
            saveLibraries(context, libraries)
            
            // If removed library was current, switch to default
            if (getCurrentLibraryId(context) == libraryId) {
                getDefaultLibrary(context)?.let { setCurrentLibraryId(context, it.id) }
            }
            
            Log.d("SettingsHelper", "Removed library $libraryId")
        }
        
        return removed
    }

    /**
     * Updates a library.
     */
    fun updateLibrary(context: Context, library: Library): Boolean {
        val libraries = getLibraries(context).toMutableList()
        val index = libraries.indexOfFirst { it.id == library.id }
        
        if (index != -1) {
            libraries[index] = library
            saveLibraries(context, libraries)
            Log.d("SettingsHelper", "Updated library ${library.id}")
            return true
        }
        
        return false
    }

    /**
     * Gets the default library (marked as primary).
     */
    fun getDefaultLibrary(context: Context): Library? {
        return getLibraries(context).firstOrNull { it.isDefault }
            ?: getLibraries(context).firstOrNull()
    }

    /**
     * Gets a library by ID.
     */
    fun getLibraryById(context: Context, id: String): Library? {
        return getLibraries(context).firstOrNull { it.id == id }
    }

    /**
     * Sets the current library ID (the one being viewed).
     */
    fun setCurrentLibraryId(context: Context, libraryId: String) {
        getPrefs(context).edit { putString(KEY_CURRENT_LIBRARY_ID, libraryId) }
        Log.d("SettingsHelper", "Set current library to $libraryId")
    }

    /**
     * Gets the current library ID (the one being viewed).
     */
    fun getCurrentLibraryId(context: Context): String? {
        return getPrefs(context).getString(KEY_CURRENT_LIBRARY_ID, null)
    }

    /**
     * Gets the currently selected library, or default if none selected.
     */
    fun getCurrentLibrary(context: Context): Library? {
        val currentId = getCurrentLibraryId(context)
        return if (currentId != null) {
            getLibraryById(context, currentId) ?: getDefaultLibrary(context)
        } else {
            getDefaultLibrary(context)
        }
    }
}