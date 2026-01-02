package com.example.calibreboxnew.dropbox

import android.content.Context
import android.util.Log
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.android.Auth
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.ListFolderResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import androidx.core.content.edit
import com.example.calibreboxnew.BuildConfig

object DropboxHelper {

    private var client: DbxClientV2? = null

    private const val PREFS_NAME = "DropboxPrefs"
    private const val KEY_ACCESS_TOKEN = "dropbox_access_token"

    fun getClient(): DbxClientV2? {
        return client
    }

    // Function to get a temporary download link
    // This needs to be a suspend function because it's a network call
    suspend fun getLink(path: String): String? {
        // Ensure the client is not null before using it
        val currentClient = client ?: run {
            Log.e("DropboxHelper", "getLink failed: Dropbox client is not initialized.")
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                // Use the Dropbox SDK to create a temporary link for the file
                val result = currentClient.files().getTemporaryLink(path)
                Log.d("DropboxHelper", "Successfully created temporary link for: $path")
                // Return the generated link URL
                result.link
            } catch (e: Exception) {
                Log.e("DropboxHelper", "Error getting temporary link for path: $path", e)
                // Return null if there was an error (e.g., file not found)
                null
            }
        }
    }

    fun init(accessToken: String) {
        if (client == null) {
            Log.d("DropboxHelper", "Initializing DbxClientV2 with a token.")
            val requestConfig = DbxRequestConfig("calibre-box")
            client = DbxClientV2(requestConfig, accessToken)
        }
    }

    fun login(context: Context) {
        Auth.startOAuth2PKCE(
            context,
            BuildConfig.DROPBOX_APP_KEY,
            DbxRequestConfig("calibre-box"),
            listOf("files.content.read", "files.metadata.read")
        )
    }

    fun getAccessToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        Log.d("DropboxHelper", "Loaded access token from SharedPreferences: ${token != null}")
        return token
    }

    fun saveAccessToken(context: Context, accessToken: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_ACCESS_TOKEN, accessToken) }
        Log.d("DropboxHelper", "Saved access token to SharedPreferences.")
    }

    fun clearAccessToken(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { remove(KEY_ACCESS_TOKEN) }
        client = null // Also clear the in-memory client
        Log.d("DropboxHelper", "Cleared access token and client.")
    }

    suspend fun listFolder(path: String): ListFolderResult? {
        return withContext(Dispatchers.IO) {
            client?.files()?.listFolder(path)
        }
    }

    suspend fun downloadFile(path: String, outputStream: OutputStream): FileMetadata? {
        return withContext(Dispatchers.IO) {
            client?.files()?.download(path)?.download(outputStream)
        }
    }
}