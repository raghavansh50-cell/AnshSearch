package com.example.data

import kotlinx.coroutines.flow.Flow

class BrowserRepository(
    private val bookmarkDao: BookmarkDao,
    private val downloadDao: DownloadDao
) {
    val allBookmarks: Flow<List<Bookmark>> = bookmarkDao.getAllBookmarks()
    val allDownloads: Flow<List<Download>> = downloadDao.getAllDownloads()

    suspend fun insertBookmark(bookmark: Bookmark) = bookmarkDao.insertBookmark(bookmark)
    suspend fun deleteBookmark(bookmark: Bookmark) = bookmarkDao.deleteBookmark(bookmark)
    suspend fun deleteBookmarkByUrl(url: String) = bookmarkDao.deleteBookmarkByUrl(url)
    fun isBookmarked(url: String): Flow<Boolean> = bookmarkDao.isBookmarked(url)

    suspend fun insertDownload(download: Download): Int {
        return downloadDao.insertDownload(download).toInt()
    }
    suspend fun updateDownloadProgress(id: Int, downloadedBytes: Long, status: String) =
        downloadDao.updateDownloadProgress(id, downloadedBytes, status)
    suspend fun updateDownloadStatus(id: Int, status: String) =
        downloadDao.updateDownloadStatus(id, status)
    suspend fun deleteDownload(download: Download) = downloadDao.deleteDownload(download)
}
