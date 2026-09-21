# UI Component Documentation

## Component Registry

### 1. `RecordHud`
- **Location**: `com.example.ui.components.RecordHud`
- **Purpose**: Live recording HUD displayed during active capture.
- **Props**:
  - `recordingState: RecordingState`: Current state (duration, amplitudes, error status).
  - `onStopRecording: () -> Unit`: Callback triggered when the user taps STOP RECORDING.
- **Features**:
  - Infinite pulsating crimson indicator for immediate visual scanning.
  - Formatted timer (`00:15 / 01:00`) with smooth linear progress indicator.
  - Multi-bar animated audio visualizer synchronized with microphone input.

### 2. `TriggerSelectorCard`
- **Location**: `com.example.ui.components.TriggerSelectorCard`
- **Purpose**: Lets users select one single hardware/gesture trigger.
- **Props**:
  - `currentTrigger: TriggerAction`: Currently configured trigger.
  - `isAccessibilityEnabled: Boolean`: Accessibility permission status.
  - `onSelectTrigger: (TriggerAction) -> Unit`: Selection callback.
  - `onOpenAccessibilitySettings: () -> Unit`: Intent launcher for Android settings.
- **Features**:
  - Clear explanations of each trigger's lockscreen behavior.
  - Warning banner and quick-fix button when accessibility permission is missing.

### 3. `RecordingsListSection`
- **Location**: `com.example.ui.components.RecordingsListSection`
- **Purpose**: Displays stored recordings, status badges, and action triggers.
- **Props**:
  - `recordings: List<RecordingEntity>`: List of saved recordings from Room.
  - `isBackingUpId: Long?`: ID of recording currently being uploaded.
  - `onPlayRecording: (RecordingEntity) -> Unit`: Play video callback.
  - `onEncrypt: (RecordingEntity) -> Unit`: Encrypt callback.
  - `onDecrypt: (RecordingEntity) -> Unit`: Decrypt callback.
  - `onCloudBackup: (RecordingEntity) -> Unit`: Cloud upload callback.
  - `onDelete: (RecordingEntity) -> Unit`: Delete recording callback.

### 4. `VideoPlaybackDialog`
- **Location**: `com.example.ui.components.VideoPlaybackDialog`
- **Purpose**: Built-in modal video player with audio and media controls.
- **Props**:
  - `recording: RecordingEntity`: Recording being previewed.
  - `onDismiss: () -> Unit`: Close dialog.
  - `onDecryptToPlay: (RecordingEntity) -> Unit`: 1-tap decryption if file is currently encrypted.

### 5. `SettingsSection`
- **Location**: `com.example.ui.components.SettingsSection`
- **Purpose**: Granular configuration for video duration, camera lens, encryption, and cloud backup.
- **Props**:
  - `settings: UserSettings`: Immutable preferences.
  - Callbacks for duration, front/back camera, auto-encrypt, and cloud backup.

### 6. `PermissionsCard`
- **Location**: `com.example.ui.components.PermissionsCard`
- **Purpose**: Diagnostics and one-tap permission granting for Camera, Audio, and Notification permissions.

### 7. `VolumeButtonTriggerService`
- **Location**: `com.example.service.VolumeButtonTriggerService`
- **Purpose**: System-level `AccessibilityService` that filters hardware key events to detect volume button long-press and double-press actions.
- **Features**:
  - Long-press threshold tracking (800ms) on Volume Down or Volume Up.
  - Toggles recording (initiates start when idle; terminates when active).
  - Tactile haptic feedback via `Vibrator` / `VibratorManager`.
  - Operates when screen is locked or off through `canRequestFilterKeyEvents`.
  - Passes through standard short volume presses to the Android audio subsystem.
