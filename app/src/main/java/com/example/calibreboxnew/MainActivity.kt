package com.example.calibreboxnew

import CacheCleanupWorker
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private var resumeCounter by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        scheduleCacheCleanup()
        setContent {
            CalibreBoxNewTheme {
                MainScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val newAuthToken = Auth.getOAuth2Token()
        if (newAuthToken != null) {
            DropboxHelper.saveAccessToken(this, newAuthToken)
            Log.d("MainActivity", "New Dropbox token received and saved.")
        }

        val existingToken = DropboxHelper.getAccessToken(this)
        if (existingToken != null && DropboxHelper.getClient() == null) {
            DropboxHelper.init(existingToken)
        }
        resumeCounter++
    }

    fun scheduleCacheCleanup() {
        val cleanupRequest = PeriodicWorkRequestBuilder<CacheCleanupWorker>(
            24, TimeUnit.HOURS // Run once a day
        ).build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "CacheCleanup",
            ExistingPeriodicWorkPolicy.KEEP, // Keep the existing schedule if already set
            cleanupRequest
        )
    }

    private fun loginToDropbox() {
        DropboxHelper.login(this)
    }

    private fun logout(context: Context, onLogoutComplete: () -> Unit) {
        DropboxHelper.logout(context)
        SettingsHelper.deleteCalibreLibraryPath(context)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                DatabaseHelper.clearDatabase(context)
                // Clear any cached cover images to ensure a fresh start on next login
                try {
                    CoverCacheHelper.clearCache(context)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to clear cover cache", e)
                }
            }
            onLogoutComplete()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

        var isLoggedIn by remember(resumeCounter) { mutableStateOf(DropboxHelper.getClient() != null) }
        var calibreLibraryPath by remember { mutableStateOf(SettingsHelper.getCalibreLibraryPath(context)) }
        val currentPath = calibreLibraryPath

        var isRefreshing by remember { mutableStateOf(false) }
        val allBooks by produceState<List<GetAllBookDetails>>(initialValue = emptyList(), currentPath, isRefreshing) {
            if (currentPath != null) {
                try {
                    if (isRefreshing) {
                        DatabaseHelper.reDownloadDatabase(context, currentPath)
                        isRefreshing = false // Reset refresh state
                    }
                    DatabaseHelper.init(context, currentPath)
                    value = DatabaseHelper.getBooks()
                } catch (e: Exception) {
                    Log.e("MainScreen", "Error loading books", e)
                    value = emptyList()
                    isRefreshing = false // Reset refresh state in case of error
                }
            }
        }

        var searchQuery by remember { mutableStateOf("") }
        var sortOrder by remember { mutableStateOf(SortOrder.RECENTLY_ADDED) }

        val sortedBooks = remember(sortOrder, allBooks) {
            when (sortOrder) {
                SortOrder.TITLE -> allBooks.sortedBy { it.title.normalizeForSearch() }
                SortOrder.AUTHOR -> allBooks.sortedWith(compareBy(nullsLast()) { it.authors?.normalizeForSearch() })
                SortOrder.RECENTLY_ADDED -> allBooks.sortedByDescending { it.id }
            }
        }

        val filteredBooks = remember(searchQuery, sortedBooks) {
            if (searchQuery.isBlank()) {
                sortedBooks
            } else {
                val normalizedQuery = searchQuery.normalizeForSearch()
                sortedBooks.filter { book ->
                    book.title.normalizeForSearch().contains(normalizedQuery) ||
                            book.authors?.normalizeForSearch()?.contains(normalizedQuery) == true
                }
            }
        }


        LaunchedEffect(allBooks.isNotEmpty()) {
            if (allBooks.isNotEmpty()) {
                val cacheWorkRequest = OneTimeWorkRequestBuilder<CoverCacheWorker>().build()
                WorkManager.getInstance(context).enqueue(cacheWorkRequest)
            }
        }

        // --- SIDEBAR DEFINITION ---
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = isLoggedIn, // Only allow swipe if logged in
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(16.dp)
                    ) {
                        // 1. Logo Section
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 16.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.LibraryBooks,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "CalibreBox",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 2. Separator
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // 3. Sort Button
                        var showSortMenu by remember { mutableStateOf(false) }

                        Box {
                            NavigationDrawerItem(
                                label = { Text("Sort Books") },
                                selected = false,
                                icon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
                                onClick = { showSortMenu = true }
                            )

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Title") },
                                    onClick = {
                                        sortOrder = SortOrder.TITLE
                                        showSortMenu = false
                                        scope.launch { drawerState.close() }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Author") },
                                    onClick = {
                                        sortOrder = SortOrder.AUTHOR
                                        showSortMenu = false
                                        scope.launch { drawerState.close() }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Recently Added") },
                                    onClick = {
                                        sortOrder = SortOrder.RECENTLY_ADDED
                                        showSortMenu = false
                                        scope.launch { drawerState.close() }
                                    }
                                )
                            }
                        }

                        var authorFilterExpanded by remember { mutableStateOf(false) }

                        Box {
                            NavigationDrawerItem(
                                label = { Text("Filter by Author") },
                                selected = false,
                                icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null) },
                                onClick = { authorFilterExpanded = true }
                            )

                            val authors = remember(allBooks) {
                                allBooks.mapNotNull { it.authors }.toSet().sorted()
                            }

                            DropdownMenu(
                                expanded = authorFilterExpanded,
                                onDismissRequest = { authorFilterExpanded = false },
                                modifier = Modifier.heightIn(max = 400.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All Authors") },
                                    onClick = {
                                        searchQuery = ""
                                        authorFilterExpanded = false
                                        scope.launch { drawerState.close() }
                                    }
                                )
                                authors.forEach { author ->
                                    DropdownMenuItem(
                                        text = { Text(author) },
                                        onClick = {
                                            searchQuery = author
                                            authorFilterExpanded = false
                                            scope.launch { drawerState.close() }
                                        }
                                    )
                                }
                            }
                        }

                        // Push remaining content to bottom
                        Spacer(modifier = Modifier.weight(1f))

                        // 4. Logout Button
                        NavigationDrawerItem(
                            label = { Text("Logout") },
                            selected = false,
                            icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                            onClick = {
                                logout(context) {
                                    isLoggedIn = false
                                    calibreLibraryPath = null
                                    resumeCounter++ // Trigger UI refresh
                                }
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                }
            }
        ) {
            // --- MAIN CONTENT AREA ---
            Scaffold(
                topBar = {
                    if (isLoggedIn) {
                        CenterAlignedTopAppBar(
                            title = { SearchBar(query = searchQuery, onQueryChange = { searchQuery = it }) },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                                }
                            }
                        )
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isLoggedIn) {
                        if (currentPath == null) {
                            FileBrowser(onFolderSelected = { path ->
                                SettingsHelper.saveCalibreLibraryPath(context, path)
                                calibreLibraryPath = path
                            })
                        } else {
                            if (allBooks.isEmpty() && !isRefreshing) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                BookGridScreen(
                                    books = filteredBooks,
                                    calibreLibraryPath = currentPath,
                                    isRefreshing = isRefreshing,
                                    onRefresh = { isRefreshing = true }
                                )
                            }
                        }
                    } else {
                        Button(onClick = { loginToDropbox() }) {
                            Text("Login to Dropbox")
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun BookGridScreen(
        books: List<GetAllBookDetails>,
        calibreLibraryPath: String,
        isRefreshing: Boolean,
        onRefresh: () -> Unit
    ) {
        var selectedBook by remember { mutableStateOf<GetAllBookDetails?>(null) }

        if (books.isEmpty() && !isRefreshing) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No books found matching your search.")
            }
            return
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(books, key = { it.id }) { book ->
                    BookCoverItem(
                        book = book,
                        calibreLibraryPath = calibreLibraryPath,
                        onBookClicked = { selectedBook = it }
                    )
                }
            }
        }

        selectedBook?.let { book ->
            BookDetailsDialog(book = book, onDismissRequest = { selectedBook = null })
        }
    }

    @Composable
    fun BookCoverItem(
        book: GetAllBookDetails,
        calibreLibraryPath: String,
        onBookClicked: (GetAllBookDetails) -> Unit
    ) {
        var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
        val context = LocalContext.current

        LaunchedEffect(book.id) {
            if (book.has_cover == true) {
                val cachedCover = CoverCacheHelper.getCover(context, book.id)
                if (cachedCover != null) {
                    imageBitmap = cachedCover.asImageBitmap()
                } else {
                    val coverPath = "/${calibreLibraryPath.trim('/')}/${book.path}/cover.jpg".replace("//", "/")
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
                        Log.e("BookCoverItem", "Failed to load cover", e)
                    }
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBookClicked(book) }
                .padding(vertical = 4.dp)
        ) {
            Card(
                modifier = Modifier
                    .width(120.dp)
                    .height(180.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (imageBitmap != null) {
                        Image(
                            bitmap = imageBitmap!!,
                            contentDescription = book.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(48.dp))
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
}