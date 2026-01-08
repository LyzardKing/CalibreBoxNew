package com.example.calibreboxnew.data.repositories

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.example.calibreboxnew.model.Library
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Repository for managing Calibre libraries.
 * Handles CRUD operations and persistence using SharedPreferences.
 */
class LibraryRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val PREFS_NAME = "CalibreBoxPrefs"
        private const val KEY_CALIBRE_PATH = "calibre_library_path" // Legacy single library
        private const val KEY_LIBRARIES = "libraries"
        private const val KEY_CURRENT_LIBRARY_ID = "current_library_id"
    }

    init {
        migrateIfNeeded()
    }

    /**
     * Migrates legacy single library to multi-library format if needed.
     */
    private fun migrateIfNeeded() {
        val libraries = prefs.getString(KEY_LIBRARIES, null)
        
        // Already migrated
        if (libraries != null) return
        
        // Check for legacy path
        val legacyPath = prefs.getString(KEY_CALIBRE_PATH, null)
        if (legacyPath != null) {
            Log.d("LibraryRepository", "Migrating legacy library path to multi-library format")
            val library = Library(
                id = UUID.randomUUID().toString(),
                name = Library.getDisplayNameFromPath(legacyPath),
                dropboxPath = legacyPath,
                isDefault = true
            )
            saveLibraries(listOf(library))
            setCurrentLibraryId(library.id)
        }
    }

    /**
     * Gets all configured libraries.
     */
    fun getLibraries(): List<Library> {
        val librariesJson = prefs.getString(KEY_LIBRARIES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<Library>>(librariesJson)
        } catch (e: Exception) {
            Log.e("LibraryRepository", "Error parsing libraries", e)
            emptyList()
        }
    }

    /**
     * Saves the list of libraries.
     */
    private fun saveLibraries(libraries: List<Library>) {
        val librariesJson = json.encodeToString(libraries)
        prefs.edit { putString(KEY_LIBRARIES, librariesJson) }
        Log.d("LibraryRepository", "Saved ${libraries.size} libraries")
    }

    /**
     * Adds a new library to the list.
     */
    fun addLibrary(library: Library): Boolean {
        val libraries = getLibraries().toMutableList()
        
        // Check if path already exists
        if (libraries.any { it.dropboxPath == library.dropboxPath }) {
            Log.w("LibraryRepository", "Library with path ${library.dropboxPath} already exists")
            return false
        }
        
        // If this is the first library, make it default
        val libraryToAdd = if (libraries.isEmpty()) {
            library.copy(isDefault = true)
        } else {
            library
        }
        
        libraries.add(libraryToAdd)
        saveLibraries(libraries)
        
        // Set as current if first library
        if (libraries.size == 1) {
            setCurrentLibraryId(libraryToAdd.id)
        }
        
        return true
    }

    /**
     * Removes a library by ID.
     */
    fun removeLibrary(libraryId: String): Boolean {
        val libraries = getLibraries().toMutableList()
        val removed = libraries.removeIf { it.id == libraryId }
        
        if (removed) {
            // If removed library was default, make first library default
            if (libraries.isNotEmpty() && libraries.none { it.isDefault }) {
                libraries[0] = libraries[0].copy(isDefault = true)
            }
            
            saveLibraries(libraries)
            
            // If removed library was current, switch to default
            if (getCurrentLibraryId() == libraryId) {
                getDefaultLibrary()?.let { setCurrentLibraryId(it.id) }
            }
            
            Log.d("LibraryRepository", "Removed library $libraryId")
        }
        
        return removed
    }

    /**
     * Updates a library.
     */
    fun updateLibrary(library: Library): Boolean {
        val libraries = getLibraries().toMutableList()
        val index = libraries.indexOfFirst { it.id == library.id }
        
        if (index != -1) {
            libraries[index] = library
            saveLibraries(libraries)
            Log.d("LibraryRepository", "Updated library ${library.id}")
            return true
        }
        
        return false
    }

    /**
     * Gets the default library (marked as primary).
     */
    fun getDefaultLibrary(): Library? {
        return getLibraries().firstOrNull { it.isDefault }
            ?: getLibraries().firstOrNull()
    }

    /**
     * Gets a library by ID.
     */
    fun getLibraryById(id: String): Library? {
        return getLibraries().firstOrNull { it.id == id }
    }

    /**
     * Sets the current library ID (the one being viewed).
     */
    fun setCurrentLibraryId(libraryId: String) {
        prefs.edit { putString(KEY_CURRENT_LIBRARY_ID, libraryId) }
        Log.d("LibraryRepository", "Set current library to $libraryId")
    }

    /**
     * Gets the current library ID (the one being viewed).
     */
    fun getCurrentLibraryId(): String? {
        return prefs.getString(KEY_CURRENT_LIBRARY_ID, null)
    }

    /**
     * Gets the currently selected library, or default if none selected.
     */
    fun getCurrentLibrary(): Library? {
        val currentId = getCurrentLibraryId()
        return if (currentId != null) {
            getLibraryById(currentId) ?: getDefaultLibrary()
        } else {
            getDefaultLibrary()
        }
    }
}
