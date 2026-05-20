package com.example.ui

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Bookmark
import com.example.data.BrowserDatabase
import com.example.data.BrowserRepository
import com.example.data.Download
import com.example.data.TabState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: BrowserRepository

    init {
        val database = BrowserDatabase.getDatabase(application)
        repository = BrowserRepository(database.bookmarkDao(), database.downloadDao())
    }

    val bookmarks: StateFlow<List<Bookmark>> = repository.allBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<List<Download>> = repository.allDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tabs
    private val _tabs = MutableStateFlow<List<TabState>>(listOf(TabState(title = "AnshSearch Home", url = "about:blank")))
    val tabs = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String>(_tabs.value.first().id)
    val activeTabId = _activeTabId.asStateFlow()

    // UI state
    private val _showTabsManager = MutableStateFlow(false)
    val showTabsManager = _showTabsManager.asStateFlow()

    private val _showBookmarksManager = MutableStateFlow(false)
    val showBookmarksManager = _showBookmarksManager.asStateFlow()

    private val _showDownloadsManager = MutableStateFlow(false)
    val showDownloadsManager = _showDownloadsManager.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val activeTab: StateFlow<TabState?> = combine(tabs, activeTabId) { tabs, activeId ->
        tabs.find { it.id == activeId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setActiveTab(id: String) {
        _activeTabId.value = id
        _showTabsManager.value = false
        // Update search query text for current tab
        val tab = _tabs.value.find { it.id == id }
        if (tab != null) {
            _searchQuery.value = if (tab.url == "about:blank") "" else tab.url
        }
    }

    fun addNewTab(url: String = "about:blank", isPrivate: Boolean = false) {
        val newTab = TabState(
            title = if (url == "about:blank") "New Tab" else "Web Page",
            url = url,
            isPrivate = isPrivate
        )
        val updatedList = _tabs.value + newTab
        _tabs.value = updatedList
        _activeTabId.value = newTab.id
        _searchQuery.value = if (url == "about:blank") "" else url
        _showTabsManager.value = false
    }

    fun closeTab(id: String) {
        val currentList = _tabs.value
        if (currentList.size <= 1) {
            // Keep at least one tab or reset it
            val newTab = TabState(title = "New Tab", url = "about:blank")
            _tabs.value = listOf(newTab)
            _activeTabId.value = newTab.id
            _searchQuery.value = ""
            return
        }

        val indexToClose = currentList.indexOfFirst { it.id == id }
        if (indexToClose != -1) {
            val updatedList = currentList.filter { it.id != id }
            _tabs.value = updatedList

            // If the closed tab was active, switch active tab
            if (_activeTabId.value == id) {
                val newActiveIndex = if (indexToClose >= updatedList.size) updatedList.size - 1 else indexToClose
                _activeTabId.value = updatedList[newActiveIndex].id
                val activeTab = updatedList[newActiveIndex]
                _searchQuery.value = if (activeTab.url == "about:blank") "" else activeTab.url
            }
        }
    }

    fun updateTabState(id: String, update: (TabState) -> TabState) {
        _tabs.value = _tabs.value.map {
            if (it.id == id) update(it) else it
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setShowTabsManager(show: Boolean) {
        _showTabsManager.value = show
    }

    fun setShowBookmarksManager(show: Boolean) {
        _showBookmarksManager.value = show
    }

    fun setShowDownloadsManager(show: Boolean) {
        _showDownloadsManager.value = show
    }

    // Bookmarks and Downloads database actions
    fun toggleBookmark(title: String, url: String, isCurrentlyBookmarked: Boolean) {
        viewModelScope.launch {
            if (isCurrentlyBookmarked) {
                repository.deleteBookmarkByUrl(url)
            } else {
                repository.insertBookmark(Bookmark(title = title, url = url))
            }
        }
    }

    fun addBookmarkDirectly(title: String, url: String) {
        viewModelScope.launch {
            repository.insertBookmark(Bookmark(title = title, url = url))
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmark)
        }
    }

    fun checkIfBookmarked(url: String): StateFlow<Boolean> = repository.isBookmarked(url)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Downloads
    fun startDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?, contentLength: Long) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>().applicationContext
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType) ?: "downloaded_file"
                
                // Set path in standard Downloads directory
                val targetFile = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )

                // 1. Write the database record first
                repository.insertDownload(
                    Download(
                        fileName = fileName,
                        url = url,
                        filePath = targetFile.absolutePath,
                        totalBytes = contentLength,
                        downloadedBytes = contentLength, // Complete mock layout
                        status = "COMPLETED",
                        mimeType = mimeType ?: "application/octet-stream"
                    )
                )

                // 2. Start the real Android DownloadManager request
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimeType)
                    addRequestHeader("User-Agent", userAgent)
                    setDescription("Downloading file via AnshSearch")
                    setTitle(fileName)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                }

                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadManager.enqueue(request)

                Toast.makeText(context, "Downloading: $fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun deleteDownload(download: Download) {
        viewModelScope.launch {
            repository.deleteDownload(download)
            try {
                val file = File(download.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
