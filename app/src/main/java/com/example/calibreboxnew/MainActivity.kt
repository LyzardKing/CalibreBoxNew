package com.example.calibreboxnew

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isEmpty
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.calibreboxnew.db.Books
import com.example.calibreboxnew.db.DatabaseHelper
import com.example.calibreboxnew.dropbox.DropboxHelper
import com.example.calibreboxnew.ui.FileBrowser
import com.example.calibreboxnew.ui.theme.CalibreBoxNewTheme
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.dropbox.core.android.Auth
import java.io.ByteArrayOutputStream

// Replace with your Dropbox App Key
private const val APP_KEY = "z9ga59p8xyvp2xz"

class MainActivity : ComponentActivity() {

    private var resumeCounter by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalibreBoxNewTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // --- THIS IS THE CRITICAL FIX FOR LOGIN FLOW ---

        // 1. Check if we are returning from the Dropbox login activity
        val newAuthToken = Auth.getOAuth2Token()
        if (newAuthToken != null) {
            // If we have a new token, save it immediately
            DropboxHelper.saveAccessToken(this, newAuthToken)
            Log.d("MainActivity", "New Dropbox token received and saved.")
        }

        // 2. Try to load any existing token (either old or the new one we just saved)
        val existingToken = DropboxHelper.getAccessToken(this)

        // 3. Initialize the client if we have a token and the client isn't already running
        if (existingToken != null && DropboxHelper.getClient() == null) {
            Log.d("MainActivity", "Initializing Dropbox client from stored token.")
            DropboxHelper.init(existingToken)
        }

        // This triggers a recomposition to update the UI state
        resumeCounter++
    }

    private fun loginToDropbox() {
        DropboxHelper.login(this, APP_KEY)
    }

    @Composable
    fun MainScreen(modifier: Modifier = Modifier) {
        var isLoggedIn by remember(resumeCounter) { mutableStateOf(DropboxHelper.getClient() != null) }
        val context = LocalContext.current

        var calibreLibraryPath by remember {
            mutableStateOf(SettingsHelper.getCalibreLibraryPath(context))
        }

        val bookState by produceState<List<Books>>(initialValue = emptyList(), calibreLibraryPath) {
            if (calibreLibraryPath != null) {
                try {
                    DatabaseHelper.init(context, calibreLibraryPath!!)
                    value = DatabaseHelper.getBooks()
                } catch (e: Exception) {
                    // Handle exceptions, e.g., log them or show an error message
                    e.printStackTrace()
                    value = emptyList() // Ensure state is updated on error
                }
            }
        }

        LaunchedEffect(bookState.isNotEmpty()) {
            if (bookState.isNotEmpty()) {
                Log.d("MainScreen", "Book list loaded. Enqueueing background cover caching worker.")
                val cacheWorkRequest = OneTimeWorkRequestBuilder<CoverCacheWorker>().build()
                WorkManager.getInstance(context).enqueue(cacheWorkRequest)
            }
        }

        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoggedIn) {
                if (calibreLibraryPath == null) {
                    FileBrowser(onFolderSelected = { path ->
                        SettingsHelper.saveCalibreLibraryPath(context, path)
                        calibreLibraryPath = path
                    })
                } else {
                    // Now, we just check the state produced above.
                    if (bookState.isEmpty()) {
                        // You could show a more specific loading indicator here if you add a status to the state
                        Text("Loading books or library is empty...")
                    } else {
                        BookGridScreen(books = bookState, calibreLibraryPath = calibreLibraryPath!!)
                    }
                }
            } else {
                Button(onClick = { loginToDropbox() }) {
                    Text("Login to Dropbox")
                }
            }
        }
    }

    @Composable
    fun BookGridScreen(books: List<Books>, calibreLibraryPath: String) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 128.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(books) { book ->
                BookCoverItem(book = book, calibreLibraryPath = calibreLibraryPath)
            }
        }
    }

    @Composable
    fun BookCoverItem(book: Books, calibreLibraryPath: String) {
        var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
        var context = LocalContext.current

        LaunchedEffect(book.id) {
            if (book.has_cover == 1L) {
                // --- CACHING LOGIC ---
                // 1. Try to load from cache first
                val cachedCover = CoverCacheHelper.getCover(context, book.id)
                if (cachedCover != null) {
                    imageBitmap = cachedCover.asImageBitmap()
                } else {
                    // 2. If not in cache, download from Dropbox
                    val coverPath = "/${calibreLibraryPath.trim('/')}/${book.path}/cover.jpg".replace("//", "/")
                    Log.d("BookCoverItem", "Cache miss for book ID ${book.id}. Fetching from: $coverPath")

                    try {
                        val outputStream = ByteArrayOutputStream()
                        DropboxHelper.downloadFile(coverPath, outputStream)
                        val imageBytes = outputStream.toByteArray()

                        if (imageBytes.isNotEmpty()) {
                            val downloadedBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            if (downloadedBitmap != null) {
                                // 3. Save the downloaded cover to cache for next time
                                CoverCacheHelper.saveCover(context, book.id, downloadedBitmap)
                                imageBitmap = downloadedBitmap.asImageBitmap()
                            }
                        }
                    } catch(e: Exception) {
                        Log.e("BookCoverItem", "Failed to load cover for '${book.title}'", e)
                    }
                }
                // --- END OF CACHING LOGIC ---
            }
        }

        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.aspectRatio(0.7f),
                contentAlignment = Alignment.Center
            ) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap!!,
                        contentDescription = book.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.Book, contentDescription = book.title)
                }
            }
            Text(text = book.title, maxLines = 2)
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun LoginScreenPreview() {
        CalibreBoxNewTheme {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(onClick = {}) {
                    Text("Login to Dropbox")
                }
            }
        }
    }
}