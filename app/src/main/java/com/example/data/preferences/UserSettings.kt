package com.example.data.preferences

enum class TriggerAction(val title: String, val description: String) {
    VOLUME_DOWN_5X(
        "Volume Down 5-Press (Recommended)",
        "Press the physical Volume Down button 5 times quickly to start or stop recording in background"
    ),
    VOLUME_LONG_PRESS(
        "Volume Long-Press (Start / Stop)",
        "Hold Volume Down or Volume Up for 1 second to toggle (start or stop) video & audio recording"
    ),
    VOLUME_DOWN_DOUBLE(
        "Volume Down Double-Press",
        "Press the physical Volume Down button twice quickly (works on lock screen with Accessibility permission)"
    ),
    VOLUME_UP_DOUBLE(
        "Volume Up Double-Press",
        "Press the physical Volume Up button twice quickly (works on lock screen with Accessibility permission)"
    ),
    QUICK_SETTINGS_TILE(
        "Quick Settings Tile",
        "Tap the Quick Record tile in Android Quick Settings (accessible directly from lock screen shade)"
    ),
    LOCKSCREEN_NOTIFICATION(
        "Lock Screen Notification Action",
        "A persistent lock screen notification providing instant 1-tap recording without unlocking"
    ),
    SHAKE_GESTURE(
        "Device Shake Gesture",
        "Shake the device firmly 2-3 times to trigger video and audio capture"
    )
}

data class UserSettings(
    val triggerAction: TriggerAction = TriggerAction.VOLUME_DOWN_5X,
    val maxDurationSeconds: Int = 60, // 0 = unlimited, or 30, 60, 180, 300, 600
    val useFrontCamera: Boolean = false,
    val autoEncrypt: Boolean = false,
    val autoCloudBackup: Boolean = false,
    val showLockScreenHUD: Boolean = true,
    val enableLockScreenNotification: Boolean = true,
    val cloudEndpointUrl: String = "https://cloud-storage.example.com/api/v1/recordings/upload"
)
