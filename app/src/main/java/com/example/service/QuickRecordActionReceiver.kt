package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.example.QuickRecordApplication
import com.example.camera.CameraRecorderManager

/**
 * Receiver for instant lockscreen notification quick actions (e.g. "Start Record", "Stop Record")
 * that starts or stops video recording immediately without waking or opening MainActivity.
 */
class QuickRecordActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "QuickRecordActionReceiver received action: $action")

        val appContext = context.applicationContext
        val settingsRepo = (appContext as? QuickRecordApplication)?.settingsRepository

        when (action) {
            ACTION_START_RECORD -> {
                if (!CameraRecorderManager.isRecordingActive) {
                    val settings = settingsRepo?.settings?.value
                    val maxDuration = settings?.maxDurationSeconds ?: 60

                    val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    val wl = pm?.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "QuickRecord:ActionReceiverWakeLock"
                    )
                    try {
                        wl?.acquire(10_000L)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to acquire wake lock: ${e.message}")
                    }

                    RecordingForegroundService.start(appContext, 0, maxDuration)
                    val manager = CameraRecorderManager.getInstance(appContext)
                    if (manager.isInitialized) {
                        manager.startRecording(maxDuration)
                    } else {
                        manager.initializeCamera(previewView = null, useFrontCamera = settings?.useFrontCamera ?: false) {
                            manager.startRecording(maxDuration)
                        }
                    }
                }
            }
            ACTION_STOP_RECORD -> {
                CameraRecorderManager.stopActiveRecording()
            }
        }
    }

    companion object {
        private const val TAG = "QuickRecordActionRcvr"
        const val ACTION_START_RECORD = "com.example.service.ACTION_START_RECORD"
        const val ACTION_STOP_RECORD = "com.example.service.ACTION_STOP_RECORD"
    }
}
