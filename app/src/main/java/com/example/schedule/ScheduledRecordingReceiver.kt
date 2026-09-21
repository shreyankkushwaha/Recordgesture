package com.example.schedule

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

/**
 * BroadcastReceiver triggered by AlarmManager when a scheduled recording's target time arrives,
 * or when the user cancels the schedule from the persistent system notification.
 */
class ScheduledRecordingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Received broadcast action: $action")

        val manager = ScheduledRecordingManager.getInstance(context)

        when (action) {
            ScheduledRecordingManager.ACTION_CANCEL_SCHEDULE -> {
                Log.d(TAG, "User canceled schedule from notification or system")
                manager.cancelSchedule()
            }

            ScheduledRecordingManager.ACTION_ALARM_TRIGGER -> {
                val duration = intent.getIntExtra(ScheduledRecordingManager.EXTRA_DURATION_SECONDS, 60)
                val useFrontCamera = intent.getBooleanExtra(ScheduledRecordingManager.EXTRA_USE_FRONT_CAMERA, false)
                val preAlert = intent.getBooleanExtra(ScheduledRecordingManager.EXTRA_PRE_ALERT, true)

                Log.d(TAG, "Scheduled alarm fired! Triggering recording with duration=${duration}s, frontCamera=$useFrontCamera")

                // Acquire temporary WakeLock to ensure smooth lockscreen unlock & recording startup
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "QuickRecord:ScheduledWakeLock"
                )
                try {
                    wakeLock.acquire(10_000L) // 10 seconds max
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to acquire wake lock", e)
                }

                // Tactile alert if requested
                if (preAlert) {
                    performTactileAlert(context)
                }

                // Dismiss scheduled notification
                manager.dismissScheduleNotification()
                manager.cancelSchedule()

                // Launch MainActivity with trigger payload
                launchRecordingActivity(context, duration, useFrontCamera)
            }
        }
    }

    private fun performTactileAlert(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 300, 150, 300), -1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing vibration alert", e)
        }
    }

    private fun launchRecordingActivity(context: Context, durationSeconds: Int, useFrontCamera: Boolean) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.example.ACTION_TRIGGER_RECORD"
            putExtra("EXTRA_SCHEDULED_DURATION", durationSeconds)
            putExtra("EXTRA_USE_FRONT_CAMERA", useFrontCamera)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }

        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            LAUNCH_REQUEST_CODE,
            launchIntent,
            pendingFlags
        )

        // Show full screen notification for reliable lockscreen presentation on Android 10+
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = ScheduledRecordingManager.CHANNEL_SCHEDULE

        val startNotification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Scheduled Recording Triggered")
            .setContentText("Capturing video for ${ScheduledRecordingManager.formatDuration(durationSeconds)}…")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_TRIGGER_ID, startNotification)

        // Also launch directly
        try {
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Direct startActivity failed, relying on fullScreenIntent", e)
        }
    }

    companion object {
        private const val TAG = "ScheduledRecReceiver"
        private const val LAUNCH_REQUEST_CODE = 5001
        private const val NOTIFICATION_TRIGGER_ID = 3002
    }
}
