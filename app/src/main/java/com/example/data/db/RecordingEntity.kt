package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val uriString: String,
    val durationMs: Long,
    val timestamp: Long,
    val fileSize: Long,
    val isEncrypted: Boolean = false,
    val isBackedUp: Boolean = false,
    val backupUrl: String = ""
)
