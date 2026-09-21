# Quick Recorder

**Quick Recorder** is a high-reliability, hardware-triggered Android application engineered for rapid video and microphone audio capture with single-gesture and lock-screen invocation.

---

## Key Features

1. **Single Predefined Action / Hardware Trigger**:
   - Double-press Volume Down (`KEYCODE_VOLUME_DOWN`) via Android Accessibility Service.
   - Double-press Volume Up (`KEYCODE_VOLUME_UP`) via Android Accessibility Service.
   - Lock Screen Quick Settings Tile (`QuickRecordTileService`).
   - Lock Screen Persistent Action Notification (`RecordingForegroundService`).
   - Physical Accelerometer Shake Gesture (`ShakeDetector`).

2. **Lock Screen Integration & Security Compliance**:
   - `setShowWhenLocked(true)` and `setTurnScreenOn(true)` ensure immediate activity display when the device is locked.
   - Strict adherence to Android privacy and security restrictions: no hidden background spyware; transparent persistent notification with recording indicator is always prominently visible.

3. **High-Performance CameraX & Audio Capture**:
   - CameraX `VideoCapture` and `Recorder` bound to lifecycle.
   - Synchronous microphone audio capture using Android `AudioRecord` pipeline.
   - Front and Back camera switching with HD resolution.
   - Configurable maximum duration limits (30s, 60s, 3m, 5m, Unlimited) with automatic safety cut-off.

4. **Hardware-Backed AES-256 GCM Encryption**:
   - Integrated with `AndroidKeyStore` (`AES/GCM/NoPadding`).
   - 12-byte initialization vectors (IV) prefixed to ciphertext.
   - Authenticated tag ensures tamper-proof recordings.

5. **Cloud Synchronization & Backup**:
   - Chunked multipart file streaming to secure cloud storage.
   - Offline resilience and database tracking.

6. **Local Persistence (Room Database)**:
   - Full history of recordings with durations, sizes, encryption status, and backup logs.

---

## Installation & Local Development

### Prerequisites
- Android Studio Ladybug or later
- JDK 17 or higher
- Android SDK 34 / 35 (API 26 minimum)

### Build Instructions
```bash
# Clone the repository
git clone <repo-url>
cd <repo-folder>

# Build the Debug APK
gradle assembleDebug

# Run Unit and Robolectric Tests
gradle :app:testDebugUnitTest
```

---

## Folder Structure
```
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/
│   │   │   │   ├── QuickRecordApplication.kt
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── backup/
│   │   │   │   │   └── CloudBackupManager.kt
│   │   │   │   ├── camera/
│   │   │   │   │   └── CameraRecorderManager.kt
│   │   │   │   ├── data/
│   │   │   │   │   ├── db/
│   │   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   │   ├── RecordingDao.kt
│   │   │   │   │   │   └── RecordingEntity.kt
│   │   │   │   │   ├── preferences/
│   │   │   │   │   │   ├── SettingsRepository.kt
│   │   │   │   │   │   └── UserSettings.kt
│   │   │   │   │   └── repository/
│   │   │   │   │       └── RecordingsRepository.kt
│   │   │   │   ├── security/
│   │   │   │   │   └── CryptoManager.kt
│   │   │   │   ├── service/
│   │   │   │   │   ├── QuickActionAccessibilityService.kt
│   │   │   │   │   ├── QuickRecordTileService.kt
│   │   │   │   │   ├── RecordingForegroundService.kt
│   │   │   │   │   ├── RecordingState.kt
│   │   │   │   │   └── ShakeDetector.kt
│   │   │   │   └── ui/
│   │   │   │       ├── MainScreen.kt
│   │   │   │       ├── MainViewModel.kt
│   │   │   │       ├── components/
│   │   │   │       │   ├── PermissionsCard.kt
│   │   │   │       │   ├── RecordHud.kt
│   │   │   │       │   ├── RecordingsListSection.kt
│   │   │   │       │   ├── SettingsSection.kt
│   │   │   │       │   ├── TriggerSelectorCard.kt
│   │   │   │       │   └── VideoPlaybackDialog.kt
│   │   │   │       └── theme/
│   │   │   │           ├── Color.kt
│   │   │   │           ├── Theme.kt
│   │   │   │           └── Type.kt
│   │   │   └── res/
│   │   │       ├── drawable/
│   │   │       ├── values/
│   │   │       └── xml/
│   │   └── test/
│   │       └── java/com/example/
│   │           ├── ExampleRobolectricTest.kt
│   │           └── GreetingScreenshotTest.kt
└── docs/
    ├── README.md
    ├── ARCHITECTURE.md
    ├── COMPONENTS.md
    ├── API.md
    ├── DATABASE.md
    ├── CHANGELOG.md
    ├── TODO.md
    ├── DECISIONS.md
    └── CONTRIBUTING.md
```
