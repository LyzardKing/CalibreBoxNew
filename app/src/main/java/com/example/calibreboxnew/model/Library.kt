package com.example.calibreboxnew.model

import kotlinx.serialization.Serializable

/**
 * Represents a Calibre library with its Dropbox path.
 * 
 * @property id Unique identifier for the library
 * @property name Display name of the library
 * @property dropboxPath Path to the library folder in Dropbox (e.g., "/Calibre Library")
 * @property isDefault Whether this is the default/primary library
 * @property sharedLinkUrl Optional shared link URL for accessing the library without authentication
 */
@Serializable
data class Library(
    val id: String,
    val name: String,
    val dropboxPath: String,
    val isDefault: Boolean = false,
    val sharedLinkUrl: String? = null
) {
    companion object {
        /**
         * Extracts a display name from a Dropbox path.
         * E.g., "/My Books/Calibre" -> "Calibre"
         */
        fun getDisplayNameFromPath(path: String): String {
            return path.trimEnd('/').substringAfterLast('/').ifEmpty { "Library" }
        }
    }
}
