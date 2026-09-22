package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.backup.CloudBackupManager
import com.example.data.db.AppDatabase
import com.example.data.preferences.SettingsRepository
import com.example.data.repository.RecordingsRepository
import com.example.security.CryptoManager
import com.example.service.RecordingForegroundService

class QuickRecordApplication : Application() {

    lateinit var appDatabase: AppDatabase
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var recordingsRepository: RecordingsRepository
        private set

    lateinit var cryptoManager: CryptoManager
        private set

    lateinit var cloudBackupManager: CloudBackupManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        appDatabase = AppDatabase.getInstance(this)
        settingsRepository = SettingsRepository(this)
        cryptoManager = CryptoManager()
        recordingsRepository = RecordingsRepository(this, appDatabase, cryptoManager)
        cloudBackupManager = CloudBackupManager(this)

        createNotificationChannels()

        if (settingsRepository.settings.value.enableLockScreenNotification) {
            com.example.service.StandbyNotificationManager.showStandbyNotification(this)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val recChannel = NotificationChannel(
                RecordingForegroundService.CHANNEL_RECORDING,
                getString(R.string.notification_channel_recording),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_recording_desc)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val triggerChannel = NotificationChannel(
                RecordingForegroundService.CHANNEL_TRIGGER,
                getString(R.string.notification_channel_quick_trigger),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_quick_trigger_desc)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(recChannel)
            notificationManager.createNotificationChannel(triggerChannel)
        }
    }

    companion object {
        lateinit var instance: QuickRecordApplication
            private set
    }
}
