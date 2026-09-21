# Changelog

All notable changes to the Quick Recorder project are documented in this file.

## [1.2.0] - Scheduled Recording Feature

### Added
- **Scheduled Video & Audio Recording (`ScheduledRecordingManager`)**:
  - Ability to set a future start time (via quick presets: +15s, +30s, +1m, +3m, +5m, +10m, +15m, +30m, +1h; or exact 24h clock hour:minute picker with today/tomorrow option).
  - Configurable recording duration presets (15s, 30s, 1 min, 2 min, 3 min, 5 min, 10 min, 15 min, 30 min) with automatic stop and video finalization.
  - Camera lens selection (Back Camera HD vs Front Selfie Camera).
  - Optional pre-alert tactile haptic feedback (3 seconds prior to recording startup).
  - Integration with Android `AlarmManager` (`setAlarmClock` / `setExactAndAllowWhileIdle`) and `SCHEDULE_EXACT_ALARM` permissions to ensure reliable execution even when the phone is locked or deep sleeping.
- **Scheduled Recording Broadcast Receiver (`ScheduledRecordingReceiver`)**:
  - Catches exact alarm triggers and screen wakeup intent with temporary wake lock.
  - Displays high-priority full-screen intent notification over lockscreen.
  - Handles cancel action directly from the persistent ongoing system notification.
- **Scheduled Recording UI Components**:
  - `ScheduleRecordingDialog`: Material 3 modal sheet with tabbed time picker (Quick Delays vs Exact Clock Time), duration chips, lens selection, and live target time calculation preview.
  - `ScheduledRecordingCard`: Prominent card embedded in both Capture and Settings screens showing live real-time countdown timer (`00:04:15`), scheduled start time, duration, lens info, and quick actions ("Start Now", "Cancel", "Edit").
- **Robolectric Test Suite**:
  - Added unit tests validating schedule registration, duration formatting, countdown formatting, and cancellation in `ExampleRobolectricTest.kt`.

## [1.1.0] - Volume Long-Press Trigger Service

### Added
- **Hardware Volume Button Long-Press Trigger Service (`VolumeButtonTriggerService`)**:
  - Implemented real-time volume button hold detection with `LONG_PRESS_THRESHOLD_MS` (800ms).
  - Toggles video + microphone audio recording (starts if idle, stops if actively recording).
  - Vibrates device with tactile haptic feedback upon reaching the long-press threshold.
  - Intercepts and consumes key events when triggered while preserving normal volume adjustments on short press.
  - Fully operates over the lock screen with `AccessibilityService` and `showWhenLocked` integration.
- **Companion Tracking**:
  - Added thread-safe volatile `CameraRecorderManager.isRecordingActive` flag for instant start/stop toggle state queries from background services.
- **UI & Configuration Updates**:
  - Added `TriggerAction.VOLUME_LONG_PRESS` as the primary/default hardware trigger option.
  - Updated `TriggerSelectorCard` and accessibility status banners to reflect long-press capabilities.

## [1.0.0] - Initial Release

### Added
- **Single Hardware / Gesture Quick Trigger**:
  - Double Volume Down keypress detection (`QuickActionAccessibilityService`).
  - Double Volume Up keypress detection.
  - Quick Settings tile trigger (`QuickRecordTileService`).
  - Lock screen notification trigger (`RecordingForegroundService`).
  - Accelerometer shake detection (`ShakeDetector`).
- **Lock Screen Support**:
  - Enabled `showWhenLocked="true"` and `turnScreenOn="true"` in `AndroidManifest.xml` and `MainActivity`.
  - Added keyguard dismissal request for immediate camera access.
- **CameraX Video + Microphone Recording**:
  - Live preview viewfinder using `PreviewView`.
  - HD video recording with synchronous audio capture via CameraX VideoCapture and Recorder.
  - Front and back camera lens switching.
  - Configurable maximum duration cut-off (30s, 60s, 3m, 5m, Unlimited).
- **Foreground Service & Indicator Compliance**:
  - Persistent ongoing recording notification with elapsed time and stop action.
  - Pulsing red HUD indicator, audio level wave visualizer, and strict compliance with Android privacy/security policies.
- **Hardware AES-256 GCM Encryption**:
  - `CryptoManager` utilizing `AndroidKeyStore` with GCM 128-bit authentication tag and random 12-byte IV.
- **Local Persistence & Cloud Sync**:
  - Room database (`AppDatabase`, `RecordingDao`, `RecordingEntity`).
  - OkHttp-based multipart cloud backup (`CloudBackupManager`).
- **Comprehensive Documentation**:
  - Architecture, Components, API, Database, Decisions, and Contributing guides in `/docs`.
