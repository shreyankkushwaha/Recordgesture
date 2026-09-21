package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private fun loadSettings(): UserSettings {
        val triggerName = prefs.getString(KEY_TRIGGER, TriggerAction.VOLUME_DOWN_DOUBLE.name)
            ?: TriggerAction.VOLUME_DOWN_DOUBLE.name
        val trigger = try {
            TriggerAction.valueOf(triggerName)
        } catch (e: Exception) {
            TriggerAction.VOLUME_DOWN_DOUBLE
        }

        return UserSettings(
            triggerAction = trigger,
            maxDurationSeconds = prefs.getInt(KEY_MAX_DURATION, 60),
            useFrontCamera = prefs.getBoolean(KEY_FRONT_CAM, false),
            autoEncrypt = prefs.getBoolean(KEY_AUTO_ENCRYPT, false),
            autoCloudBackup = prefs.getBoolean(KEY_AUTO_BACKUP, false),
            showLockScreenHUD = prefs.getBoolean(KEY_LOCKSCREEN_HUD, true),
            enableLockScreenNotification = prefs.getBoolean(KEY_LOCKSCREEN_NOTIF, true),
            cloudEndpointUrl = prefs.getString(KEY_CLOUD_ENDPOINT, "https://cloud-storage.example.com/api/v1/recordings/upload")
                ?: "https://cloud-storage.example.com/api/v1/recordings/upload"
        )
    }

    fun updateTriggerAction(action: TriggerAction) {
        prefs.edit().putString(KEY_TRIGGER, action.name).apply()
        _settings.value = _settings.value.copy(triggerAction = action)
    }

    fun updateMaxDuration(seconds: Int) {
        prefs.edit().putInt(KEY_MAX_DURATION, seconds).apply()
        _settings.value = _settings.value.copy(maxDurationSeconds = seconds)
    }

    fun updateUseFrontCamera(front: Boolean) {
        prefs.edit().putBoolean(KEY_FRONT_CAM, front).apply()
        _settings.value = _settings.value.copy(useFrontCamera = front)
    }

    fun updateAutoEncrypt(enable: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ENCRYPT, enable).apply()
        _settings.value = _settings.value.copy(autoEncrypt = enable)
    }

    fun updateAutoCloudBackup(enable: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BACKUP, enable).apply()
        _settings.value = _settings.value.copy(autoCloudBackup = enable)
    }

    fun updateShowLockScreenHUD(enable: Boolean) {
        prefs.edit().putBoolean(KEY_LOCKSCREEN_HUD, enable).apply()
        _settings.value = _settings.value.copy(showLockScreenHUD = enable)
    }

    fun updateEnableLockScreenNotification(enable: Boolean) {
        prefs.edit().putBoolean(KEY_LOCKSCREEN_NOTIF, enable).apply()
        _settings.value = _settings.value.copy(enableLockScreenNotification = enable)
    }

    companion object {
        private const val PREFS_NAME = "quick_record_prefs"
        private const val KEY_TRIGGER = "trigger_action"
        private const val KEY_MAX_DURATION = "max_duration"
        private const val KEY_FRONT_CAM = "front_cam"
        private const val KEY_AUTO_ENCRYPT = "auto_encrypt"
        private const val KEY_AUTO_BACKUP = "auto_backup"
        private const val KEY_LOCKSCREEN_HUD = "lockscreen_hud"
        private const val KEY_LOCKSCREEN_NOTIF = "lockscreen_notif"
        private const val KEY_CLOUD_ENDPOINT = "cloud_endpoint"
    }
}
