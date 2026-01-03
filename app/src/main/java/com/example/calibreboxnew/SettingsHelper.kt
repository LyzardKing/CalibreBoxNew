package com.example.calibreboxnew

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit

object SettingsHelper {

    private const val PREFS_NAME = "CalibreBoxPrefs"
    private const val KEY_CALIBRE_PATH = "calibre_library_path"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveCalibreLibraryPath(context: Context, path: String) {
        Log.d("SettingsHelper", "Saving Calibre library path: $path")
        getPrefs(context).edit { putString(KEY_CALIBRE_PATH, path) }
    }

    fun getCalibreLibraryPath(context: Context): String? {
        val path = getPrefs(context).getString(KEY_CALIBRE_PATH, null)
        Log.d("SettingsHelper", "Loaded Calibre library path: $path")
        return path
    }

    fun deleteCalibreLibraryPath(context: Context) {
        getPrefs(context).edit { remove(KEY_CALIBRE_PATH) }
        Log.d("SettingsHelper", "Deleted Calibre library path")
    }
}