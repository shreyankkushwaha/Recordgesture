package com.example.data.repository

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.db.RecordingEntity
import com.example.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class RecordingsRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private val cryptoManager: CryptoManager = CryptoManager()
) {
    private val dao = database.recordingDao()

    val allRecordings: Flow<List<RecordingEntity>> = dao.getAll()

    suspend fun saveRecording(
        title: String,
        filePath: String,
        uriString: String,
        durationMs: Long,
        fileSize: Long,
        autoEncrypt: Boolean
    ): RecordingEntity = withContext(Dispatchers.IO) {
        val initialEntity = RecordingEntity(
            title = title,
            filePath = filePath,
            uriString = uriString,
            durationMs = durationMs,
            timestamp = System.currentTimeMillis(),
            fileSize = fileSize,
            isEncrypted = false,
            isBackedUp = false
        )
        val id = dao.insert(initialEntity)
        var savedEntity = initialEntity.copy(id = id)

        if (autoEncrypt) {
            val encrypted = encryptRecording(savedEntity)
            if (encrypted != null) {
                savedEntity = encrypted
            }
        }
        savedEntity
    }

    suspend fun encryptRecording(entity: RecordingEntity): RecordingEntity? = withContext(Dispatchers.IO) {
        if (entity.isEncrypted) return@withContext entity
        val originalFile = File(entity.filePath)
        if (!originalFile.exists()) return@withContext null

        val encFile = File(originalFile.parentFile, "${originalFile.nameWithoutExtension}.enc")
        val success = cryptoManager.encryptFile(originalFile, encFile)
        if (success) {
            originalFile.delete()
            val updated = entity.copy(
                filePath = encFile.absolutePath,
                fileSize = encFile.length(),
                isEncrypted = true
            )
            dao.update(updated)
            updated
        } else {
            null
        }
    }

    suspend fun decryptRecording(entity: RecordingEntity): RecordingEntity? = withContext(Dispatchers.IO) {
        if (!entity.isEncrypted) return@withContext entity
        val encFile = File(entity.filePath)
        if (!encFile.exists()) return@withContext null

        val decFile = File(encFile.parentFile, "${encFile.nameWithoutExtension}.mp4")
        val success = cryptoManager.decryptFile(encFile, decFile)
        if (success) {
            encFile.delete()
            val updated = entity.copy(
                filePath = decFile.absolutePath,
                fileSize = decFile.length(),
                isEncrypted = false
            )
            dao.update(updated)
            updated
        } else {
            null
        }
    }

    suspend fun updateBackupStatus(id: Long, backupUrl: String) = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext
        dao.update(entity.copy(isBackedUp = true, backupUrl = backupUrl))
    }

    suspend fun deleteRecording(entity: RecordingEntity) = withContext(Dispatchers.IO) {
        val file = File(entity.filePath)
        if (file.exists()) {
            file.delete()
        }
        dao.delete(entity)
    }
}
