# System Architecture

## Architectural Principles & Paradigm
Quick Recorder uses modern **Model-View-ViewModel (MVVM)** and **Clean Architecture** patterns, leveraging Kotlin Coroutines and Flows for uni-directional reactive data flow.

```
┌─────────────────────────────────────────────────────────────┐
│                       UI / Compose                          │
│  MainScreen ─── RecordHud ─── Settings ─── RecordingsList  │
└──────────────────────────────▲──────────────────────────────┘
                               │ StateFlow
┌──────────────────────────────┴──────────────────────────────┐
│                      MainViewModel                          │
│  State aggregation, intent handling, lifecycle orchestration│
└──────────────▲───────────────────────────────▲──────────────┘
               │                               │
┌──────────────┴──────────────┐ ┌──────────────┴──────────────┐
│    RecordingsRepository     │ │     SettingsRepository      │
│  Room Database + Crypto     │ │   Encrypted SharedPreferences│
└──────────────┬──────────────┘ └─────────────────────────────┘
               │
┌──────────────▼──────────────┐
│        Hardware / OS        │
│  CameraX ── Audio ── KeyStore ── Accessibility ── Quick Tile│
└─────────────────────────────┘
```

---

## Component Layers

### 1. Presentation Layer (Jetpack Compose + ViewModel)
- **`MainActivity`**: Root lifecycle activity configured with `showWhenLocked="true"` and `turnScreenOn="true"`.
- **`MainViewModel`**: Exposes immutable state flows (`recordingState`, `recordings`, `settings`, `isAccessibilityEnabled`).
- **`RecordHud`**: Real-time display showing pulsing recording indicator, audio level visualizer, elapsed time, and tactile stop button.

### 2. Camera & Audio Capture Engine
- **`CameraRecorderManager`**: Controls CameraX `ProcessCameraProvider` and `VideoCapture<Recorder>`.
- Configures `FileOutputOptions` and enables simultaneous audio track.
- Emits real-time audio amplitudes for the live visualizer.

### 3. Trigger & System Services
- **`VolumeButtonTriggerService`**: Accessibility Service that intercepts hardware volume button key events (`ACTION_DOWN` / `ACTION_UP`) to detect long-presses (800ms) and double-presses. Automatically toggles recording on and off, provides haptic vibration feedback, and safely passes through short volume clicks to Android.
- **`QuickActionAccessibilityService`**: Compatibility service delegating status and intent helpers.
- **`QuickRecordTileService`**: System Quick Settings tile triggering recording directly from the lock screen shade.
- **`RecordingForegroundService`**: Maintains ongoing foreground notification (`FOREGROUND_SERVICE_TYPE_CAMERA` & `FOREGROUND_SERVICE_TYPE_MICROPHONE`) to ensure uninterrupted recording during lock screen and app transitions.
- **`ShakeDetector`**: Computes g-force acceleration vector to identify intentional shake gestures.

### 4. Data & Security Layer
- **`CryptoManager`**: AES-256-GCM symmetric cipher backed by `AndroidKeyStore`.
- **`AppDatabase` & `RecordingDao`**: SQLite persistence via Room for recording metadata.
- **`CloudBackupManager`**: Encrypted cloud streaming via OkHttp.
