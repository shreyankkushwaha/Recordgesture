package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class RecordingForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "QuickRecord::RecordingWakeLock"
        ).apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                StandbyNotificationManager.dismissStandbyNotification(this)
                val elapsed = intent.getIntExtra(EXTRA_ELAPSED, 0)
                val max = intent.getIntExtra(EXTRA_MAX_DURATION, 60)
                acquireWakeLock()
                startForegroundNotification(elapsed, max)
            }
            ACTION_UPDATE -> {
                val elapsed = intent.getIntExtra(EXTRA_ELAPSED, 0)
                val max = intent.getIntExtra(EXTRA_MAX_DURATION, 60)
                updateNotification(elapsed, max)
            }
            ACTION_STOP -> {
                releaseWakeLock()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                restoreStandbyNotificationIfEnabled()
                stopSelf()
            }
            ACTION_STOP_FROM_NOTIFICATION -> {
                com.example.camera.CameraRecorderManager.stopActiveRecording()
                releaseWakeLock()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                restoreStandbyNotificationIfEnabled()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun restoreStandbyNotificationIfEnabled() {
        val app = application as? com.example.QuickRecordApplication
        if (app?.settingsRepository?.settings?.value?.enableLockScreenNotification == true) {
            StandbyNotificationManager.showStandbyNotification(this)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        android.util.Log.d(
            "RecordingForegroundService",
            "App swiped away from recents; activeRecording=${com.example.camera.CameraRecorderManager.isRecordingActive}"
        )
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(30 * 60 * 1000L) // 30 min max safety
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startForegroundNotification(elapsedSeconds: Int, maxSeconds: Int) {
        val notification = buildRecordingNotification(elapsedSeconds, maxSeconds)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            startForeground(NOTIFICATION_ID_RECORDING, notification, foregroundServiceType)
        } else {
            startForeground(NOTIFICATION_ID_RECORDING, notification)
        }
    }

    private fun updateNotification(elapsedSeconds: Int, maxSeconds: Int) {
        val notification = buildRecordingNotification(elapsedSeconds, maxSeconds)
        notificationManager.notify(NOTIFICATION_ID_RECORDING, notification)
    }

    private fun buildRecordingNotification(elapsedSeconds: Int, maxSeconds: Int): Notification {
        val elapsedFormatted = formatTime(elapsedSeconds)
        val maxFormatted = if (maxSeconds > 0) formatTime(maxSeconds) else "∞"
        val contentText = "Recording: $elapsedFormatted / $maxFormatted • Microphone Active"

        // Open app intent
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Direct stop recording intent (stops background capture immediately from notification)
        val stopIntent = Intent(this, RecordingForegroundService::class.java).apply {
            action = ACTION_STOP_FROM_NOTIFICATION
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_recording_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.action_stop_recording),
                stopPendingIntent
            )
            .build()
    }

    private fun formatTime(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val recChannel = NotificationChannel(
                CHANNEL_RECORDING,
                getString(R.string.notification_channel_recording),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_recording_desc)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val triggerChannel = NotificationChannel(
                CHANNEL_TRIGGER,
                getString(R.string.notification_channel_quick_trigger),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_quick_trigger_desc)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(recChannel)
            notificationManager.createNotificationChannel(triggerChannel)
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_RECORDING = "quick_record_active_channel"
        const val CHANNEL_TRIGGER = "quick_record_trigger_channel"

        const val NOTIFICATION_ID_RECORDING = 2001
        const val NOTIFICATION_ID_TRIGGER = 2002

        const val ACTION_START = "com.example.service.START_RECORDING_SERVICE"
        const val ACTION_UPDATE = "com.example.service.UPDATE_RECORDING_SERVICE"
        const val ACTION_STOP = "com.example.service.STOP_RECORDING_SERVICE"
        const val ACTION_STOP_FROM_NOTIFICATION = "com.example.service.ACTION_STOP_FROM_NOTIFICATION"

        const val EXTRA_ELAPSED = "extra_elapsed"
        const val EXTRA_MAX_DURATION = "extra_max_duration"

        fun start(context: Context, elapsedSeconds: Int, maxSeconds: Int) {
            val intent = Intent(context, RecordingForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ELAPSED, elapsedSeconds)
                putExtra(EXTRA_MAX_DURATION, maxSeconds)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun update(context: Context, elapsedSeconds: Int, maxSeconds: Int) {
            val intent = Intent(context, RecordingForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_ELAPSED, elapsedSeconds)
                putExtra(EXTRA_MAX_DURATION, maxSeconds)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RecordingForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
