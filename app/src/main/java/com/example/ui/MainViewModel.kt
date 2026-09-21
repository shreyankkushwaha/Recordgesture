package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.QuickRecordApplication
import com.example.backup.BackupResult
import com.example.data.db.RecordingEntity
import com.example.data.preferences.TriggerAction
import com.example.data.preferences.UserSettings
import com.example.service.QuickActionAccessibilityService
import com.example.service.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as QuickRecordApplication
    private val settingsRepo = app.settingsRepository
    private val recordingsRepo = app.recordingsRepository
    private val cloudBackupManager = app.cloudBackupManager
    private val scheduledRecordingManager = com.example.schedule.ScheduledRecordingManager.getInstance(application)

    val settings: StateFlow<UserSettings> = settingsRepo.settings
    val activeSchedule: StateFlow<com.example.schedule.ScheduledRecording?> = scheduledRecordingManager.activeSchedule
    val remainingSeconds: StateFlow<Long> = scheduledRecordingManager.remainingSeconds
    val formattedCountdown: StateFlow<String> = scheduledRecordingManager.formattedCountdown

    val recordings: StateFlow<List<RecordingEntity>> = recordingsRepo.allRecordings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _isBackingUp = MutableStateFlow<Long?>(null)
    val isBackingUp: StateFlow<Long?> = _isBackingUp.asStateFlow()

    init {
        checkAccessibilityStatus(application)
    }

    fun checkAccessibilityStatus(context: Context) {
        _isAccessibilityEnabled.value =
            QuickActionAccessibilityService.isAccessibilityServiceEnabled(context)
    }

    fun onRecordingSaved(savedFile: File, durationMs: Long) {
        viewModelScope.launch {
            val titleFormat = SimpleDateFormat("MMM d, yyyy HH:mm:ss", Locale.getDefault())
            val title = "Quick Recording " + titleFormat.format(Date())
            val autoEncrypt = settings.value.autoEncrypt
            val autoBackup = settings.value.autoCloudBackup

            val entity = recordingsRepo.saveRecording(
                title = title,
                filePath = savedFile.absolutePath,
                uriString = savedFile.toURI().toString(),
                durationMs = durationMs,
                fileSize = savedFile.length(),
                autoEncrypt = autoEncrypt
            )

            _toastMessage.value = if (autoEncrypt) "Recording saved & encrypted!" else "Recording saved successfully!"

            if (autoBackup) {
                backupRecording(entity)
            }
        }
    }

    fun updateTriggerAction(action: TriggerAction) {
        settingsRepo.updateTriggerAction(action)
    }

    fun updateMaxDuration(seconds: Int) {
        settingsRepo.updateMaxDuration(seconds)
    }

    fun updateUseFrontCamera(front: Boolean) {
        settingsRepo.updateUseFrontCamera(front)
    }

    fun updateAutoEncrypt(enabled: Boolean) {
        settingsRepo.updateAutoEncrypt(enabled)
    }

    fun updateAutoCloudBackup(enabled: Boolean) {
        settingsRepo.updateAutoCloudBackup(enabled)
    }

    fun updateEnableLockScreenNotification(enabled: Boolean) {
        settingsRepo.updateEnableLockScreenNotification(enabled)
    }

    fun encryptRecording(entity: RecordingEntity) {
        viewModelScope.launch {
            val updated = recordingsRepo.encryptRecording(entity)
            if (updated != null) {
                _toastMessage.value = "File encrypted with AES-256 GCM"
            } else {
                _toastMessage.value = "Encryption failed"
            }
        }
    }

    fun decryptRecording(entity: RecordingEntity) {
        viewModelScope.launch {
            val updated = recordingsRepo.decryptRecording(entity)
            if (updated != null) {
                _toastMessage.value = "File decrypted successfully"
            } else {
                _toastMessage.value = "Decryption failed"
            }
        }
    }

    fun backupRecording(entity: RecordingEntity) {
        viewModelScope.launch {
            _isBackingUp.value = entity.id
            when (val result = cloudBackupManager.backupRecording(entity, settings.value.cloudEndpointUrl)) {
                is BackupResult.Success -> {
                    recordingsRepo.updateBackupStatus(entity.id, result.backupUrl)
                    _toastMessage.value = "Cloud backup complete: ${result.backupUrl}"
                }
                is BackupResult.Error -> {
                    _toastMessage.value = result.message
                }
            }
            _isBackingUp.value = null
        }
    }

    fun deleteRecording(entity: RecordingEntity) {
        viewModelScope.launch {
            recordingsRepo.deleteRecording(entity)
            _toastMessage.value = "Recording deleted"
        }
    }

    fun scheduleRecording(
        targetTimeMillis: Long,
        durationSeconds: Int,
        useFrontCamera: Boolean,
        preAlertEnabled: Boolean = true
    ) {
        val schedule = scheduledRecordingManager.scheduleRecording(
            targetTimeMillis = targetTimeMillis,
            durationSeconds = durationSeconds,
            useFrontCamera = useFrontCamera,
            preAlertEnabled = preAlertEnabled
        )
        val timeStr = com.example.schedule.ScheduledRecordingManager.formatTargetTime(targetTimeMillis)
        val durStr = com.example.schedule.ScheduledRecordingManager.formatDuration(durationSeconds)
        _toastMessage.value = "Recording scheduled for $timeStr ($durStr)"
    }

    fun cancelScheduledRecording() {
        scheduledRecordingManager.cancelSchedule()
        _toastMessage.value = "Scheduled recording canceled"
    }

    fun triggerScheduledRecordingNow() {
        scheduledRecordingManager.triggerNow()
    }

    fun clearToast() {
        _toastMessage.value = null
    }
}
