package com.example.calibreboxnew.util

import android.net.Uri
import android.util.Log

/**
 * Utility for parsing Dropbox shared URLs and extracting folder paths.
 */
object DropboxUrlParser {
    
    private const val TAG = "DropboxUrlParser"
    
    /**
     * Extracts the Dropbox folder path from a shared URL or returns the input if it's already a path.
     * 
     * Supports formats:
     * - Direct paths: "/Calibre Library" or "Calibre Library"
     * - New shared links: https://www.dropbox.com/scl/fo/xyz?rlkey=abc&dl=0
     * - Old shared links: https://www.dropbox.com/sh/xyz/abc
     * - Direct links: https://www.dropbox.com/home/Calibre%20Library
     * 
     * @param input The shared URL or direct path
     * @return The Dropbox folder path (e.g., "/Calibre Library") or null if invalid
     */
    fun extractPath(input: String): String? {
        val trimmed = input.trim()
        
        // Already a path (starts with /)
        if (trimmed.startsWith("/")) {
            return trimmed
        }
        
        // Not a URL - treat as relative path
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "/$trimmed"
        }
        
        try {
            val uri = Uri.parse(trimmed)
            val host = uri.host ?: return null
            
            // Check if it's a Dropbox URL
            if (!host.contains("dropbox.com")) {
                Log.w(TAG, "Not a Dropbox URL: $host")
                return null
            }
            
            val path = uri.path ?: return null
            
            // Handle /home/ URLs: https://www.dropbox.com/home/Calibre%20Library
            if (path.startsWith("/home/")) {
                return path.removePrefix("/home")
            }
            
            // Handle /scl/fo/ (new shared folder links) and /scl/fi/ (files)
            // These don't contain the folder name in the URL, so we can't extract it locally
            // They need to be resolved via the Dropbox API
            if (path.startsWith("/scl/")) {
                Log.d(TAG, "Shared link detected - will be resolved via API")
                // Return the full URL as a marker that this needs API resolution
                return trimmed
            }
            
            // Handle /sh/ (old shared folder links)
            // These also need API resolution
            if (path.startsWith("/sh/")) {
                Log.d(TAG, "Old shared link detected - will be resolved via API")
                return trimmed
            }
            
            // For any other Dropbox URL, try to use the path as-is
            return if (path.isNotEmpty() && path != "/") path else null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing URL: $input", e)
            return null
        }
    }
    
    /**
     * Checks if the input looks like a Dropbox shared link that requires API resolution.
     */
    fun isSharedLink(input: String): Boolean {
        val trimmed = input.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return false
        }
        
        try {
            val uri = Uri.parse(trimmed)
            val host = uri.host ?: return false
            val path = uri.path ?: return false
            
            return host.contains("dropbox.com") && 
                   (path.startsWith("/scl/") || path.startsWith("/sh/"))
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Checks if the path is a URL (either direct path or shared link)
     */
    fun isUrl(input: String): Boolean {
        val trimmed = input.trim()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }
    
    /**
     * Validates that a string is a valid Dropbox path format.
     */
    fun isValidPath(path: String): Boolean {
        val trimmed = path.trim()
        // Must start with / and not be just /
        return trimmed.startsWith("/") && trimmed.length > 1
    }
}
