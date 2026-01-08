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

    suspend fun listFolder(path: String): ListFolderResult? {
        return withContext(Dispatchers.IO) {
            client?.files()?.listFolder(path)
        }
    }

    /**
     * Copy a file within Dropbox from sourcePath to destPath.
     * Returns true on success.
     */
    suspend fun copyFile(sourcePath: String, destPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val result = client?.files()?.copyV2(sourcePath, destPath)
                result != null
            } catch (e: Exception) {
                Log.w("DropboxHelper", "copyFile failed from $sourcePath to $destPath", e)
                false
            }
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
                meta as? FileMetadata
            } catch (e: Exception) {
                Log.w("DropboxHelper", "Failed to get metadata for '$path'", e)
                null
            }
        }
    }

    /**
     * Download a file using a shared link URL.
     * This allows accessing shared files without the user adding them to their Dropbox.
     * 
     * @param sharedLink The shared link URL
     * @param path Path within the shared folder (e.g., "metadata.db" or "book/cover.jpg")
     * @param outputStream Stream to write the downloaded file to
     * @return Metadata if successful, null otherwise
     */
    suspend fun downloadFileFromSharedLink(
        context: Context,
        sharedLink: String,
        path: String,
        outputStream: OutputStream
    ): com.dropbox.core.v2.sharing.SharedLinkMetadata? {
        return withContext(Dispatchers.IO) {
            try {
                // For shared links, the path should NOT have a leading slash
                val cleanPath = path.trimStart('/')
                Log.d("DropboxHelper", "Downloading from shared link: $sharedLink")
                Log.d("DropboxHelper", "  Requested path: $path")
                Log.d("DropboxHelper", "  Clean path: $cleanPath")
                
                // Use the public API with builder pattern
                val downloader = client?.sharing()
                    ?.getSharedLinkFileBuilder(sharedLink)
                    ?.withPath("/$cleanPath")
                    ?.start()
                
                if (downloader == null) {
                    Log.e("DropboxHelper", "Failed to create downloader - client may not be initialized")
                    return@withContext null
                }
                    
                downloader.download(outputStream)
                Log.d("DropboxHelper", "Download completed successfully")
                
                // Return metadata  
                client?.sharing()
                    ?.getSharedLinkMetadataBuilder(sharedLink)
                    ?.withPath("/$cleanPath")
                    ?.start()
            } catch (e: Exception) {
                Log.e("DropboxHelper", "downloadFileFromSharedLink failed for $path in $sharedLink", e)
                Log.e("DropboxHelper", "  Error type: ${e.javaClass.simpleName}")
                Log.e("DropboxHelper", "  Error message: ${e.message}")
                if (e.message?.contains("invalid_grant", ignoreCase = true) == true) {
                    Log.e("DropboxHelper", "Refresh token is invalid, clearing credential")
                    clearCredential(context)
                }
                null
            }
        }
    }

}