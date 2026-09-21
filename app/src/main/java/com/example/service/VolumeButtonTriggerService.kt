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
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.TextUtils
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

/**
 * Service to listen for hardware volume button events including:
 * - Volume button long-press (hold Volume Down or Volume Up for >= 800ms) to start or stop video recording
 * - Volume button double-press sequences
 *
 * Works seamlessly on locked devices and in the background, automatically triggering
 * recording without requiring the user to physically open the app first.
 */
class VolumeButtonTriggerService : AccessibilityService() {

    private lateinit var settingsRepository: SettingsRepository
    private val mainHandler = Handler(Looper.getMainLooper())

    // State tracking for Volume Long-Press
    private var isVolumeDownPressed = false
    private var isVolumeUpPressed = false
    private var volumeDownPressStartTime = 0L
    private var volumeUpPressStartTime = 0L
    private var isLongPressTriggered = false

    // State tracking for Volume Double-Press
    private var lastVolumeDownClickTime = 0L
    private var lastVolumeUpClickTime = 0L

    private val longPressRunnable = Runnable {
        isLongPressTriggered = true
        handleLongPressTriggered()
    }

    override fun onCreate() {
        super.onCreate()
        settingsRepository = (application as? QuickRecordApplication)?.settingsRepository
            ?: SettingsRepository(this)
        Log.d(TAG, "VolumeButtonTriggerService created")
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
            Log.d(TAG, "VolumeButtonTriggerService connected and dynamically configured")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring serviceInfo in onServiceConnected", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed; filtering key events only
    }

    override fun onInterrupt() {
        Log.d(TAG, "VolumeButtonTriggerService interrupted")
        cancelLongPressTimer()
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)

        val currentTrigger = settingsRepository.settings.value.triggerAction
        val keyCode = event.keyCode
        val action = event.action

        // Check if current setting is Volume Long-Press
        if (currentTrigger == TriggerAction.VOLUME_LONG_PRESS) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    return handleVolumeLongPressKeyEvent(action, keyCode)
                }
            }
        }

        // Check if current setting is Volume Double-Press
        if (action == KeyEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (currentTrigger == TriggerAction.VOLUME_DOWN_DOUBLE) {
                        if (now - lastVolumeDownClickTime < DOUBLE_PRESS_WINDOW_MS) {
                            lastVolumeDownClickTime = 0L
                            provideHapticFeedback()
                            triggerToggleRecording()
                            return true
                        } else {
                            lastVolumeDownClickTime = now
                        }
                    }
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (currentTrigger == TriggerAction.VOLUME_UP_DOUBLE) {
                        if (now - lastVolumeUpClickTime < DOUBLE_PRESS_WINDOW_MS) {
                            lastVolumeUpClickTime = 0L
                            provideHapticFeedback()
                            triggerToggleRecording()
                            return true
                        } else {
                            lastVolumeUpClickTime = now
                        }
                    }
                }
            }
        }

        return super.onKeyEvent(event)
    }

    private fun handleVolumeLongPressKeyEvent(action: Int, keyCode: Int): Boolean {
        when (action) {
            KeyEvent.ACTION_DOWN -> {
                val isDownKey = (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
                val isUpKey = (keyCode == KeyEvent.KEYCODE_VOLUME_UP)

                val wasPressed = if (isDownKey) isVolumeDownPressed else isVolumeUpPressed

                if (!wasPressed) {
                    if (isDownKey) {
                        isVolumeDownPressed = true
                        volumeDownPressStartTime = System.currentTimeMillis()
                    } else {
                        isVolumeUpPressed = true
                        volumeUpPressStartTime = System.currentTimeMillis()
                    }

                    isLongPressTriggered = false
                    // Schedule trigger after long-press duration
                    mainHandler.removeCallbacks(longPressRunnable)
                    mainHandler.postDelayed(longPressRunnable, LONG_PRESS_THRESHOLD_MS)
                }
                // Intercept key down event while testing for long-press
                return true
            }

            KeyEvent.ACTION_UP -> {
                val isDownKey = (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
                val isUpKey = (keyCode == KeyEvent.KEYCODE_VOLUME_UP)

                val pressDuration = if (isDownKey) {
                    System.currentTimeMillis() - volumeDownPressStartTime
                } else {
                    System.currentTimeMillis() - volumeUpPressStartTime
                }

                if (isDownKey) isVolumeDownPressed = false
                if (isUpKey) isVolumeUpPressed = false

                cancelLongPressTimer()

                if (isLongPressTriggered) {
                    // Consumed by long-press action
                    isLongPressTriggered = false
                    return true
                } else if (pressDuration >= LONG_PRESS_THRESHOLD_MS) {
                    // Fallback if runnable was slightly delayed
                    handleLongPressTriggered()
                    return true
                }

                // Was a short tap; let system adjust volume normally
                return false
            }
        }
        return false
    }

    private fun cancelLongPressTimer() {
        mainHandler.removeCallbacks(longPressRunnable)
    }

    private fun handleLongPressTriggered() {
        // Haptic feedback to inform user their long-press succeeded
        provideHapticFeedback()

        // Toggle recording (start if idle, stop if active)
        triggerToggleRecording()
    }

    private fun triggerToggleRecording() {
        val isCurrentlyRecording = CameraRecorderManager.isRecordingActive

        if (isCurrentlyRecording) {
            Log.d(TAG, "Active recording found! Stopping immediately directly from background service.")
            val stopped = CameraRecorderManager.stopActiveRecording()
            if (stopped) {
                provideDoubleHapticFeedback()
            }
            return
        }

        Log.d(TAG, "Triggering automatic background launch for recording start.")

        // 1. Acquire WakeLock to turn screen on and prevent sleep
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "QuickRecord:VolumeTriggerWakeLock"
        )
        try {
            wakeLock?.acquire(15_000L) // 15 seconds
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }

        // 2. Prepare launch intent for MainActivity
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            action = "com.example.ACTION_TRIGGER_RECORD"
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }

        // 3. Android 14+ (API 34+) background activity launch options
        val activityOptionsBundle: Bundle? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
            }.toBundle()
        } else {
            null
        }

        // 4. Create PendingIntent
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(
            this,
            TRIGGER_REQUEST_CODE,
            launchIntent,
            pendingFlags
        )

        // 5. Post high-priority Heads-Up Notification with FullScreenIntent
        // FullScreenIntent allows Android system to immediately pop the Activity to foreground
        // even from locked screen or background
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val channelId = RecordingForegroundService.CHANNEL_RECORDING

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_recording_title))
            .setContentText("Hardware button triggered: starting capture automatically…")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .setTimeoutAfter(6000L)
            .build()

        notificationManager?.notify(TRIGGER_NOTIFICATION_ID, notification)

        // 6. Direct launch with PendingIntent and startActivity
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && activityOptionsBundle != null) {
                pendingIntent.send(this, 0, null, null, null, null, activityOptionsBundle)
            } else {
                pendingIntent.send()
            }
        } catch (e: Exception) {
            Log.w(TAG, "pendingIntent.send failed", e)
        }

        try {
            if (activityOptionsBundle != null) {
                startActivity(launchIntent, activityOptionsBundle)
            } else {
                startActivity(launchIntent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct startActivity failed, relying on fullScreenIntent", e)
        }
    }

    private fun provideHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(80L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(80L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating haptic feedback", e)
        }
    }

    private fun provideDoubleHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(
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
            Log.e(TAG, "Error generating double haptic feedback", e)
        }
    }

    override fun onDestroy() {
        cancelLongPressTimer()
        super.onDestroy()
        Log.d(TAG, "VolumeButtonTriggerService destroyed")
    }

    companion object {
        private const val TAG = "VolumeTriggerService"
        const val LONG_PRESS_THRESHOLD_MS = 800L
        private const val DOUBLE_PRESS_WINDOW_MS = 600L
        private const val TRIGGER_REQUEST_CODE = 4001
        private const val TRIGGER_NOTIFICATION_ID = 2005

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${VolumeButtonTriggerService::class.java.canonicalName}"
            val legacyServiceName = "${context.packageName}/${QuickActionAccessibilityService::class.java.canonicalName}"

            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)

            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedServiceName, ignoreCase = true) ||
                    componentName.equals(legacyServiceName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

        fun openAccessibilitySettingsIntent(): Intent {
            return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}

