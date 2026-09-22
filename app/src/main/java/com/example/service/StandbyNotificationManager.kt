package com.example.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.camera.CameraRecorderManager

/**
 * Manages the persistent Lock Screen standby notification.
 * Enables 1-tap instant recording directly from the lock screen or notification shade,
 * and maintains low OOM kill priority so Xiaomi/MIUI does not force stop the background services.
 */
object StandbyNotificationManager {
    const val STANDBY_NOTIFICATION_ID = 2007

    fun showStandbyNotification(context: Context) {
        if (CameraRecorderManager.isRecordingActive) {
            // Live recording active, RecordingForegroundService handles active recording notification
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // 1. Content Intent (Open App)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            201,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Direct 1-Tap Record Action Intent (Broadcast)
        val recordIntent = Intent(context, QuickRecordActionReceiver::class.java).apply {
            action = QuickRecordActionReceiver.ACTION_START_RECORD
        }
        val recordPendingIntent = PendingIntent.getBroadcast(
            context,
            202,
            recordIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, RecordingForegroundService.CHANNEL_TRIGGER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Quick Recorder Ready")
            .setContentText("Volume buttons active • Tap Record to capture video")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "● Start Recording",
                recordPendingIntent
            )
            .build()

        notificationManager.notify(STANDBY_NOTIFICATION_ID, notification)
    }

    fun dismissStandbyNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(STANDBY_NOTIFICATION_ID)
    }
}
