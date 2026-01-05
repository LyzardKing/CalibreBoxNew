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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import com.example.calibreboxnew.model.Library
import com.example.calibreboxnew.ui.AddLibraryDialog
import com.example.calibreboxnew.ui.BookDetailsDialog
import com.example.calibreboxnew.ui.FileBrowser
import com.example.calibreboxnew.ui.LibraryManagementSheet
import com.example.calibreboxnew.ui.SearchBar
import com.example.calibreboxnew.ui.theme.CalibreBoxNewTheme
import com.example.calibreboxnew.utils.normalizeForSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
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
        // Check for newly obtained credential after OAuth flow
        val newCredential = Auth.getDbxCredential()
        if (newCredential != null) {
            DropboxHelper.saveCredential(this, newCredential)
            DropboxHelper.init(newCredential)
            Log.d("MainActivity", "New Dropbox credential received and saved (refresh token enabled).")
        } else if (DropboxHelper.getClient() == null) {
            // Try to initialize from saved credential
            DropboxHelper.initFromSavedCredential(this)
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
        // Clear all libraries
        SettingsHelper.getLibraries(context).forEach { library ->
            SettingsHelper.removeLibrary(context, library.id)
        }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                DatabaseHelper.clearDatabase(context, libraryId = null) // Clear all
                // Clear all library caches
                try {
                    CoverCacheHelper.clearCache(context, libraryId = null) // Clear all
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
        
        // Multi-library support
        var libraries by remember { mutableStateOf(SettingsHelper.getLibraries(context)) }
        var currentLibrary by remember { mutableStateOf(SettingsHelper.getCurrentLibrary(context)) }
        
        // Derive path directly from currentLibrary
        val calibreLibraryPath = currentLibrary?.dropboxPath
        val currentPath = calibreLibraryPath
        
        // Dialog states
        var showAddLibraryDialog by remember { mutableStateOf(false) }
        var showLibraryManagement by remember { mutableStateOf(false) }

        var isRefreshing by remember { mutableStateOf(false) }
        var libraryError by remember { mutableStateOf<String?>(null) }
        var allBooks by remember { mutableStateOf<List<GetAllBookDetails>>(emptyList()) }
        var refreshTrigger by remember { mutableStateOf(0) }
        
        // Load books when library changes or refresh is triggered
        LaunchedEffect(currentLibrary?.id, currentPath, refreshTrigger) {
            // Clear books immediately when library changes
            allBooks = emptyList()
            
            if (currentPath != null) {
                try {
                    libraryError = null
                    if (isRefreshing) {
                        DatabaseHelper.reDownloadDatabase(context, currentPath, currentLibrary?.sharedLinkUrl, libraryId = currentLibrary?.id ?: "default")
                        isRefreshing = false
                    }
                    DatabaseHelper.init(context, currentPath, sharedLinkUrl = currentLibrary?.sharedLinkUrl, libraryId = currentLibrary?.id ?: "default")
                    allBooks = DatabaseHelper.getBooks()
                } catch (e: Exception) {
                    Log.e("MainScreen", "Error loading books", e)
                    libraryError = e.message ?: "Failed to load library"
                    allBooks = emptyList()
                    isRefreshing = false
                }
            } else {
                allBooks = emptyList()
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

                        // 3. Library Selection
                        if (libraries.isNotEmpty()) {
                            Text(
                                "Libraries",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            
                            libraries.forEach { library ->
                                val isSelected = library.id == currentLibrary?.id
                                NavigationDrawerItem(
                                    label = { 
                                        Text(
                                            library.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        ) 
                                    },
                                    selected = isSelected,
                                    icon = { 
                                        Icon(
                                            Icons.Default.Book, 
                                            contentDescription = null
                                        ) 
                                    },
                                    badge = if (library.isDefault) {
                                        { Text("Default", style = MaterialTheme.typography.labelSmall) }
                                    } else null,
                                    onClick = {
                                        currentLibrary = library
                                        SettingsHelper.setCurrentLibraryId(context, library.id)
                                        scope.launch { drawerState.close() }
                                    }
                                )
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }

                        // 4. Sort Button
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

                        // Add Library Button
                        FilledTonalButton(
                            onClick = { showAddLibraryDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Book,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Add Library")
                        }
                        
                        // Manage Libraries Button
                        if (libraries.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { 
                                    showLibraryManagement = true
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Manage Libraries")
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Logout Button
                        NavigationDrawerItem(
                            label = { Text("Logout") },
                            selected = false,
                            icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                            onClick = {
                                logout(context) {
                                    isLoggedIn = false
                                    libraries = emptyList()
                                    currentLibrary = null
                                    resumeCounter++ // Trigger UI refresh
                                }
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                }
            }
        ) {
            // Add Library Dialog
            if (showAddLibraryDialog) {
                AddLibraryDialog(
                    onDismiss = { showAddLibraryDialog = false },
                    onLibraryAdded = { library ->
                        val success = SettingsHelper.addLibrary(context, library)
                        if (success) {
                            libraries = SettingsHelper.getLibraries(context)
                            currentLibrary = library
                            SettingsHelper.setCurrentLibraryId(context, library.id)
                        }
                        showAddLibraryDialog = false
                    }
                )
            }
            
            // Library Management Sheet
            if (showLibraryManagement) {
                LibraryManagementSheet(
                    onDismiss = { showLibraryManagement = false },
                    onLibrariesChanged = {
                        libraries = SettingsHelper.getLibraries(context)
                        currentLibrary = SettingsHelper.getCurrentLibrary(context)
                    }
                )
            }
            
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
                },
                snackbarHost = {
                    // Show error message if library fails to load
                    libraryError?.let { error ->
                        Snackbar(
                            modifier = Modifier.padding(16.dp),
                            action = {
                                TextButton(onClick = { libraryError = null }) {
                                    Text("Dismiss")
                                }
                            }
                        ) {
                            Text(error)
                        }
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
                                // Create a default library from the selected path
                                val newLibrary = Library(
                                    id = java.util.UUID.randomUUID().toString(),
                                    name = "My Library",
                                    dropboxPath = path,
                                    isDefault = true
                                )
                                SettingsHelper.addLibrary(context, newLibrary)
                                SettingsHelper.setCurrentLibraryId(context, newLibrary.id)
                                // Refresh the library list
                                libraries = SettingsHelper.getLibraries(context)
                                currentLibrary = SettingsHelper.getCurrentLibrary(context)
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
                                    currentLibrary = currentLibrary,
                                    isRefreshing = isRefreshing,
                                    onRefresh = { 
                                        isRefreshing = true
                                        refreshTrigger++
                                    }
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
        currentLibrary: Library?,
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

        val gridState = rememberLazyGridState()
        
        // Scroll to top when library changes
        LaunchedEffect(currentLibrary?.id) {
            gridState.scrollToItem(0)
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(books, key = { "${currentLibrary?.id ?: "default"}_${it.id}" }) { book ->
                    BookCoverItem(
                        book = book,
                        calibreLibraryPath = calibreLibraryPath,
                        sharedLinkUrl = currentLibrary?.sharedLinkUrl,
                        onBookClicked = { selectedBook = it }
                    )
                }
            }
        }

        selectedBook?.let { book ->
            currentLibrary?.let { library ->
                BookDetailsDialog(
                    book = book,
                    library = library,
                    onDismissRequest = { selectedBook = null }
                )
            }
        }
    }

    @Composable
    fun BookCoverItem(
        book: GetAllBookDetails,
        calibreLibraryPath: String,
        sharedLinkUrl: String?,
        onBookClicked: (GetAllBookDetails) -> Unit
    ) {
        var imageBitmap by remember(calibreLibraryPath, sharedLinkUrl, book.id) { mutableStateOf<ImageBitmap?>(null) }
        val context = LocalContext.current

        LaunchedEffect(calibreLibraryPath, sharedLinkUrl, book.id) {
            if (book.has_cover == true) {
                withContext(Dispatchers.IO) {
                    try {
                        val libraryId = calibreLibraryPath.hashCode().toString()
                        
                        // Check if we're still active before proceeding
                        if (!isActive) return@withContext
                        
                        val cachedCover = CoverCacheHelper.getCover(context, libraryId, book.id)
                        if (cachedCover != null) {
                            if (!isActive) return@withContext
                            withContext(Dispatchers.Main) {
                                imageBitmap = cachedCover.asImageBitmap()
                            }
                        } else {
                            if (!isActive) return@withContext
                            
                            val coverPath = if (sharedLinkUrl != null) {
                                "${book.path}/cover.jpg"
                            } else {
                                "/${calibreLibraryPath.trim('/')}/${book.path}/cover.jpg".replace("//", "/")
                            }
                            val outputStream = ByteArrayOutputStream()
                            if (sharedLinkUrl != null) {
                                DropboxHelper.downloadFileFromSharedLink(context, sharedLinkUrl, coverPath, outputStream)
                            } else {
                                DropboxHelper.downloadFile(context, coverPath, outputStream)
                            }
                            
                            if (!isActive) return@withContext
                            
                            val imageBytes = outputStream.toByteArray()
                            if (imageBytes.isNotEmpty()) {
                                val downloadedBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                if (downloadedBitmap != null) {
                                    CoverCacheHelper.saveCover(context, libraryId, book.id, downloadedBitmap)
                                    if (!isActive) return@withContext
                                    withContext(Dispatchers.Main) {
                                        imageBitmap = downloadedBitmap.asImageBitmap()
                                    }
                                }
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // Expected when library changes - just cancel silently
                        Log.d("BookCoverItem", "Cover load cancelled for book ${book.id}")
                        throw e // Re-throw to properly cancel the coroutine
                    } catch (e: Exception) {
                        Log.e("BookCoverItem", "Failed to load cover for book ${book.id}", e)
                    }
                }
            } else {
                imageBitmap = null
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