package com.example.calibreboxnew.dropbox

import android.content.Context
import android.util.Log
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.android.Auth
import com.dropbox.core.oauth.DbxCredential
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
    private var credential: DbxCredential? = null

    private const val PREFS_NAME = "DropboxPrefs"
    private const val KEY_CREDENTIAL = "dropbox_credential"
    // Keep old key for migration purposes
    private const val KEY_ACCESS_TOKEN_LEGACY = "dropbox_access_token"

    private val requestConfig = DbxRequestConfig("calibre-box")

    fun getClient(): DbxClientV2? {
        return client
    }

    /**
     * Initialize the Dropbox client with a DbxCredential containing refresh token.
     * This allows the SDK to automatically refresh the access token when it expires.
     */
    fun init(dbxCredential: DbxCredential) {
        if (client == null) {
            Log.d("DropboxHelper", "Initializing DbxClientV2 with credential (refresh token enabled).")
            credential = dbxCredential
            client = DbxClientV2(requestConfig, dbxCredential)
        }
    }

    /**
     * Initialize the Dropbox client from saved credential in SharedPreferences.
     * Returns true if initialization was successful, false otherwise.
     */
    fun initFromSavedCredential(context: Context): Boolean {
        val savedCredential = getCredential(context)
        return if (savedCredential != null) {
            init(savedCredential)
            true
        } else {
            false
        }
    }

    fun login(context: Context) {
        Auth.startOAuth2PKCE(
            context,
            BuildConfig.DROPBOX_APP_KEY,
            requestConfig,
            emptyList()
        )
    }

    fun logout(context: Context) {
        clearCredential(context)
    }

    /**
     * Get the stored DbxCredential from SharedPreferences.
     * Returns null if no credential is stored.
     */
    fun getCredential(context: Context): DbxCredential? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val credentialJson = prefs.getString(KEY_CREDENTIAL, null)
        
        if (credentialJson != null) {
            return try {
                DbxCredential.Reader.readFully(credentialJson)
            } catch (e: Exception) {
                Log.e("DropboxHelper", "Failed to parse stored credential", e)
                null
            }
        }
        
        Log.d("DropboxHelper", "No credential found in SharedPreferences")
        return null
    }

    /**
     * Save the DbxCredential to SharedPreferences.
     * The credential contains the refresh token which allows automatic token refresh.
     */
    fun saveCredential(context: Context, dbxCredential: DbxCredential) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val credentialJson = DbxCredential.Writer.writeToString(dbxCredential)
        prefs.edit { 
            putString(KEY_CREDENTIAL, credentialJson)
            // Remove legacy access token if it exists
            remove(KEY_ACCESS_TOKEN_LEGACY)
        }
        Log.d("DropboxHelper", "Saved credential to SharedPreferences (refresh token enabled).")
    }

    /**
     * Clear the stored credential and reset the client.
     */
    fun clearCredential(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { 
            remove(KEY_CREDENTIAL)
            remove(KEY_ACCESS_TOKEN_LEGACY)
        }
        client = null
        credential = null
        Log.d("DropboxHelper", "Cleared credential and client.")
    }

    /**
     * Check if a valid credential is stored.
     */
    fun hasStoredCredential(context: Context): Boolean {
        return getCredential(context) != null
    }

    suspend fun listFolder(path: String): ListFolderResult? {
        return withContext(Dispatchers.IO) {
            client?.files()?.listFolder(path)
        }
    }

    suspend fun downloadFile(context: Context, path: String, outputStream: OutputStream): FileMetadata? {
        return withContext(Dispatchers.IO) {
            try {
                client?.files()?.download(path)?.download(outputStream)
            } catch (e: Exception) {
                Log.w("DropboxHelper", "downloadFile failed for $path", e)
                // With refresh tokens, most auth errors should be handled automatically.
                // Only clear credential if it's a permanent auth failure.
                if (e.message?.contains("invalid_grant", ignoreCase = true) == true) {
                    Log.e("DropboxHelper", "Refresh token is invalid, clearing credential")
                    clearCredential(context)
                }
                null
            }
        }
    }

    suspend fun getFileMetadata(path: String): FileMetadata? {
        return withContext(Dispatchers.IO) {
            try {
                val meta = client?.files()?.getMetadata(path)
                if (meta is FileMetadata) meta else null
            } catch (e: Exception) {
                Log.w("DropboxHelper", "Failed to get metadata for '$path'", e)
                null
            }
        }
    }
}