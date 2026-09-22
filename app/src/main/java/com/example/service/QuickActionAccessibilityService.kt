package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityOptions
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.QuickRecordApplication
import com.example.R
import com.example.camera.CameraRecorderManager
import com.example.data.preferences.SettingsRepository
import com.example.data.preferences.TriggerAction

class QuickActionAccessibilityService : AccessibilityService() {

    private lateinit var settingsRepository: SettingsRepository
    private var lastVolumeDownTime = 0L
    private var lastVolumeUpTime = 0L
    private var volumeDownPressCount = 0
    private var lastVolumeDownPressTimestamp = 0L

    override fun onCreate() {
        super.onCreate()
        settingsRepository = (application as? QuickRecordApplication)?.settingsRepository
            ?: SettingsRepository(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC or
                AccessibilityServiceInfo.FEEDBACK_HAPTIC
            serviceInfo = info
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op; we only handle key events
    }

    override fun onInterrupt() {
        // Required method
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null || event.action != KeyEvent.ACTION_DOWN) {
            return super.onKeyEvent(event)
        }

        val currentTrigger = settingsRepository.settings.value.triggerAction
        val now = System.currentTimeMillis()

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (currentTrigger == TriggerAction.VOLUME_DOWN_5X) {
                    if (now - lastVolumeDownPressTimestamp > MULTI_PRESS_TIMEOUT_MS) {
                        volumeDownPressCount = 1
                    } else {
                        volumeDownPressCount++
                    }
                    lastVolumeDownPressTimestamp = now
                    provideTickHapticFeedback()

                    if (volumeDownPressCount >= 5) {
                        volumeDownPressCount = 0
                        lastVolumeDownPressTimestamp = 0L
                        provideHapticFeedback()
                        triggerQuickRecord()
                        return true
                    }
                } else if (currentTrigger == TriggerAction.VOLUME_DOWN_DOUBLE) {
                    if (now - lastVolumeDownTime < DOUBLE_PRESS_WINDOW_MS) {
                        lastVolumeDownTime = 0L
                        provideHapticFeedback()
                        triggerQuickRecord()
                        return true
                    } else {
                        lastVolumeDownTime = now
                    }
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (currentTrigger == TriggerAction.VOLUME_UP_DOUBLE) {
                    if (now - lastVolumeUpTime < DOUBLE_PRESS_WINDOW_MS) {
                        lastVolumeUpTime = 0L
                        provideHapticFeedback()
                        triggerQuickRecord()
                        return true
                    } else {
                        lastVolumeUpTime = now
                    }
                }
            }
        }

        return super.onKeyEvent(event)
    }

    private fun triggerQuickRecord() {
        if (CameraRecorderManager.isRecordingActive) {
            CameraRecorderManager.stopActiveRecording()
            provideDoubleHapticFeedback()
            return
        }

        provideHapticFeedback()

        val settings = settingsRepository.settings.value
        val maxDuration = settings.maxDurationSeconds

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "QuickRecord:QuickActionWakeLock"
        )
        try {
            wakeLock?.acquire(15_000L)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Start Foreground Service immediately
        try {
            RecordingForegroundService.start(applicationContext, 0, maxDuration)
        } catch (e: Exception) {
            Log.w("QuickActionService", "Foreground service start: ${e.message}")
        }

        // START RECORDING DIRECTLY in CameraRecorderManager!
        try {
            val recorderManager = CameraRecorderManager.getInstance(applicationContext)
            if (recorderManager.isInitialized) {
                recorderManager.startRecording(maxDuration)
            } else {
                recorderManager.initializeCamera(
                    previewView = null,
                    useFrontCamera = settings.useFrontCamera,
                    onInitialized = {
                        recorderManager.startRecording(maxDuration)
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("QuickActionService", "Failed to start recording directly: ${e.message}", e)
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            action = "com.example.ACTION_TRIGGER_RECORD"
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }

        val activityOptionsBundle: Bundle? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
            }.toBundle()
        } else {
            null
        }

        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(
            this,
            5002,
            launchIntent,
            pendingFlags
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val channelId = RecordingForegroundService.CHANNEL_RECORDING

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_recording_title))
            .setContentText("Hardware button triggered: recording active in background…")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .setTimeoutAfter(6000L)
            .build()

        notificationManager?.notify(2006, notification)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && activityOptionsBundle != null) {
                pendingIntent.send(this, 0, null, null, null, null, activityOptionsBundle)
            } else {
                pendingIntent.send()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            if (activityOptionsBundle != null) {
                startActivity(launchIntent, activityOptionsBundle)
            } else {
                startActivity(launchIntent)
            }
        } catch (e: Exception) {
            Log.w("QuickActionService", "startActivity suppressed, background recording active: ${e.message}")
        }
    }

    private fun provideHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(80L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(80L)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun provideDoubleHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 100), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 100), -1)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 100, 80, 100), -1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun provideTickHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val vibratorManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                } else null
                val vibrator = vibratorManager?.defaultVibrator ?: (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
                vibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(25L)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val DOUBLE_PRESS_WINDOW_MS = 600L
        private const val MULTI_PRESS_TIMEOUT_MS = 750L

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            return VolumeButtonTriggerService.isAccessibilityServiceEnabled(context)
        }

        fun openAccessibilitySettingsIntent(): Intent {
            return VolumeButtonTriggerService.openAccessibilitySettingsIntent()
        }
    }
}
