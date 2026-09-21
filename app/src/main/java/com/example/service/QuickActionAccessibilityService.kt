package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.example.MainActivity
import com.example.data.preferences.SettingsRepository
import com.example.data.preferences.TriggerAction

class QuickActionAccessibilityService : AccessibilityService() {

    private lateinit var settingsRepository: SettingsRepository
    private var lastVolumeDownTime = 0L
    private var lastVolumeUpTime = 0L

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
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
                if (currentTrigger == TriggerAction.VOLUME_DOWN_DOUBLE) {
                    if (now - lastVolumeDownTime < DOUBLE_PRESS_WINDOW_MS) {
                        lastVolumeDownTime = 0L
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
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "com.example.ACTION_TRIGGER_RECORD"
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        startActivity(intent)
    }

    companion object {
        private const val DOUBLE_PRESS_WINDOW_MS = 600L

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            return VolumeButtonTriggerService.isAccessibilityServiceEnabled(context)
        }

        fun openAccessibilitySettingsIntent(): Intent {
            return VolumeButtonTriggerService.openAccessibilitySettingsIntent()
        }
    }
}
