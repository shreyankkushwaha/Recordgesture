package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.example.MainActivity
import com.example.camera.CameraRecorderManager
import com.example.data.preferences.SettingsRepository
import com.example.data.preferences.TriggerAction

/**
 * Service to listen for hardware volume button events including:
 * - Volume button long-press (e.g. hold Volume Down or Volume Up for >= 800ms) to start or stop video recording
 * - Volume button double-press sequences
 *
 * Works seamlessly on locked devices and when the screen is off/ambient,
 * provided the user grants Accessibility permission.
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
        settingsRepository = SettingsRepository(this)
        Log.d(TAG, "VolumeButtonTriggerService created")
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

        val targetAction = if (isCurrentlyRecording) {
            "com.example.ACTION_STOP_RECORD"
        } else {
            "com.example.ACTION_TRIGGER_RECORD"
        }

        Log.d(TAG, "Toggling recording. isRecordingActive=$isCurrentlyRecording, firing action=$targetAction")

        val intent = Intent(this, MainActivity::class.java).apply {
            action = targetAction
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        startActivity(intent)
    }

    private fun provideHapticFeedback() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
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

    override fun onDestroy() {
        cancelLongPressTimer()
        super.onDestroy()
        Log.d(TAG, "VolumeButtonTriggerService destroyed")
    }

    companion object {
        private const val TAG = "VolumeTriggerService"
        const val LONG_PRESS_THRESHOLD_MS = 800L
        private const val DOUBLE_PRESS_WINDOW_MS = 600L

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
