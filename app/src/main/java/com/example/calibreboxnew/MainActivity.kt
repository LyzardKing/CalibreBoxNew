package com.example.calibreboxnew

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.dropbox.core.android.Auth
import com.example.calibreboxnew.db.DatabaseHelper
import com.example.calibreboxnew.db.GetAllBookDetails
import com.example.calibreboxnew.dropbox.DropboxHelper
import com.example.calibreboxnew.ui.BookDetailsDialog
import com.example.calibreboxnew.ui.FileBrowser
import com.example.calibreboxnew.ui.SearchBar
import com.example.calibreboxnew.ui.theme.CalibreBoxNewTheme
import com.example.calibreboxnew.utils.normalizeForSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {

    private var resumeCounter by mutableIntStateOf(0)

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
        // 1. Check if we are returning from the Dropbox login activity
        val newAuthToken = Auth.getOAuth2Token()
        if (newAuthToken != null) {
            DropboxHelper.saveAccessToken(this, newAuthToken)
            Log.d("MainActivity", "New Dropbox token received and saved.")
        }

        // 2. Try to load any existing token
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
        DropboxHelper.login(this)
    }

    @Composable
    fun MainScreen(modifier: Modifier = Modifier) {
        var isLoggedIn by remember(resumeCounter) { mutableStateOf(DropboxHelper.getClient() != null) }
        val context = LocalContext.current

        var calibreLibraryPath by remember {
            mutableStateOf(SettingsHelper.getCalibreLibraryPath(context))
        }

        // Create a local, immutable copy of the state variable
        val currentPath = calibreLibraryPath

        val allBooks by produceState<List<GetAllBookDetails>>(initialValue = emptyList(), currentPath) {
            // Use the local copy for the null check and subsequent operations
            if (currentPath != null) {
                try {
                    DatabaseHelper.init(context, currentPath)
                    value = DatabaseHelper.getBooks()
                } catch (e: Exception) {
                    Log.e("MainScreen", "Error loading books", e)
                    value = emptyList()
                }
            }
        }

        // --- NEW: State for search query ---
        var searchQuery by remember { mutableStateOf("") }

        // --- NEW: Derived state for filtered books ---
        val filteredBooks = remember(searchQuery, allBooks) {
            if (searchQuery.isBlank()) {
                allBooks
            } else {
                val normalizedQuery = searchQuery.normalizeForSearch()
                allBooks.filter { book ->
                    // Search in title and authors
                    val matchesTitle = book.title.normalizeForSearch().contains(normalizedQuery)
                    val matchesAuthors = book.authors?.normalizeForSearch()?.contains(normalizedQuery) == true
                    matchesTitle || matchesAuthors
                }
            }
        }

        LaunchedEffect(allBooks.isNotEmpty()) {
            if (allBooks.isNotEmpty()) {
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
                // Also use the local copy here for consistency
                if (currentPath == null) {
                    FileBrowser(onFolderSelected = { path ->
                        SettingsHelper.saveCalibreLibraryPath(context, path)
                        calibreLibraryPath = path
                    })
                } else {
                    // --- NEW: Add the SearchBar ---
                    SearchBar(query = searchQuery, onQueryChange = { searchQuery = it })

                    if (allBooks.isEmpty()) {
                        Text("Loading books...")
                    } else {
                        // --- Use the filtered list ---
                        BookGridScreen(books = filteredBooks, calibreLibraryPath = currentPath)
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
    fun BookGridScreen(books: List<GetAllBookDetails>, calibreLibraryPath: String) {
        var selectedBook by remember { mutableStateOf<GetAllBookDetails?>(null) }

        // --- NEW: Add a message for no search results ---
        if (books.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No books found matching your search.")
            }
            return
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(books, key = { it.id }) { book -> // Use a key for better performance
                BookCoverItem(
                    book = book,
                    calibreLibraryPath = calibreLibraryPath,
                    onBookClicked = {
                        selectedBook = it
                    }
                )
            }
        }

        selectedBook?.let { book ->
            BookDetailsDialog(
                book = book,
                onDismissRequest = {
                    selectedBook = null
                }
            )
        }
    }

    @Composable
    fun BookCoverItem(
        book: GetAllBookDetails,
        calibreLibraryPath: String,
        onBookClicked: (GetAllBookDetails) -> Unit // The click handler parameter
    ) {
        var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
        val context = LocalContext.current

        LaunchedEffect(book.id) {
            if (book.has_cover == 1L) {
                val cachedCover = CoverCacheHelper.getCover(context, book.id)
                if (cachedCover != null) {
                    imageBitmap = cachedCover.asImageBitmap()
                } else {
                    val coverPath = "/${calibreLibraryPath.trim('/')}/${book.path}/cover.jpg".replace("//", "/")
                    Log.d("BookCoverItem", "Cache miss for book ID ${book.id}. Fetching from: $coverPath")
                    try {
                        val outputStream = ByteArrayOutputStream()
                        DropboxHelper.downloadFile(coverPath, outputStream)
                        val imageBytes = outputStream.toByteArray()
                        if (imageBytes.isNotEmpty()) {
                            val downloadedBitmap = withContext(Dispatchers.IO) {
                                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            }
                            if (downloadedBitmap != null) {
                                CoverCacheHelper.saveCover(context, book.id, downloadedBitmap)
                                imageBitmap = downloadedBitmap.asImageBitmap()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("BookCoverItem", "Failed to load cover for '${book.title}'", e)
                    }
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBookClicked(book) } // Make the whole item clickable
                .padding(vertical = 4.dp)
        ) {
            Card(
                modifier = Modifier
                    .width(120.dp)
                    .height(180.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                        // Placeholder for books without a cover or while loading
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = "No cover available",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
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
