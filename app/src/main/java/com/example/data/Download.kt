package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class Download(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val url: String,
    val filePath: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: String, // "DOWNLOADING", "COMPLETED", "FAILED"
    val mimeType: String,
    val timestamp: Long = System.currentTimeMillis()
)
