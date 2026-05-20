package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<Download>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: Download): Long

    @Query("UPDATE downloads SET downloadedBytes = :downloadedBytes, status = :status WHERE id = :id")
    suspend fun updateDownloadProgress(id: Int, downloadedBytes: Long, status: String)

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateDownloadStatus(id: Int, status: String)

    @Delete
    suspend fun deleteDownload(download: Download)
}
