import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File
import java.util.concurrent.TimeUnit

class CacheCleanupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val booksFolder = File(applicationContext.cacheDir, "books")

        if (booksFolder.exists() && booksFolder.isDirectory) {
            val currentTime = System.currentTimeMillis()
            // Define expiration: e.g., 2 days (48 hours)
            // We assume that if the book is not opened in two days we can remove it.
            val expirationThreshold = TimeUnit.DAYS.toMillis(2)

            booksFolder.listFiles()?.forEach { file ->
                val fileAge = currentTime - file.lastModified()
                if (fileAge > expirationThreshold) {
                    file.delete()
                }
            }
        }
        return Result.success()
    }
}