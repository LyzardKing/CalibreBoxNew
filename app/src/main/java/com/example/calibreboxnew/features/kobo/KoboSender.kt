package com.example.calibreboxnew.features.kobo

import android.util.Log
import com.example.calibreboxnew.dropbox.DropboxHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object KoboSender {

    /**
     * Send to Kobo by copying the file within Dropbox into the Kobo app folder.
     * Returns true on success. This requires the Dropbox client to be initialized.
     */
    suspend fun sendToKobo(
        dropboxPath: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (DropboxHelper.getClient() == null) {
                    Log.w("KoboSender", "Dropbox client not initialized; cannot copy to Kobo folder")
                    return@withContext false
                }

                val fileNameWithExt = dropboxPath.substringAfterLast('/')
                val destPath = "/Apps/Rakuten Kobo/$fileNameWithExt"

                val copied = DropboxHelper.copyFile(dropboxPath, destPath)
                if (copied) {
                    Log.d("KoboSender", "Copied $fileNameWithExt to Dropbox $destPath")
                    true
                } else {
                    Log.w("KoboSender", "Dropbox copy failed for $fileNameWithExt to $destPath")
                    false
                }
            } catch (e: Exception) {
                Log.e("KoboSender", "Failed to send to Kobo via Dropbox copy", e)
                false
            }
        }
    }
}
