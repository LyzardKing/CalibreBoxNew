package com.example.calibreboxnew.dropbox

import android.content.Context
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.android.Auth
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.ListFolderResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

object DropboxHelper {

    private var client: DbxClientV2? = null

    fun getClient(): DbxClientV2? {
        return client
    }

    fun init(accessToken: String) {
        val requestConfig = DbxRequestConfig("calibre-box")
        client = DbxClientV2(requestConfig, accessToken)
    }

    fun login(context: Context, appKey: String) {
        Auth.startOAuth2PKCE(
            context,
            appKey,
            DbxRequestConfig("calibre-box"),
            listOf("files.content.read", "files.metadata.read")
        )
    }

    fun getAccessToken(): String? {
        return Auth.getDbxCredential()?.accessToken
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