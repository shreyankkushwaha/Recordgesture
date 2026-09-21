# API and Intent Interface Documentation

## System Intents & Trigger Contract

### 1. `ACTION_TRIGGER_RECORD`
- **Action String**: `com.example.ACTION_TRIGGER_RECORD`
- **Sender**: `QuickActionAccessibilityService`, `QuickRecordTileService`, Notification Shade
- **Target**: `MainActivity`
- **Flags**: `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP`
- **Behavior**: Dismisses keyguard if permitted, turns screen on, initializes CameraX viewfinder, and starts video + audio recording.

### 2. `ACTION_STOP_RECORD`
- **Action String**: `com.example.ACTION_STOP_RECORD`
- **Sender**: Notification action button, lock screen action
- **Target**: `MainActivity`
- **Behavior**: Safely finalizes CameraX `Recording`, flushes buffers to disk, releases wakelock, and updates the database.

---

## Cloud Backup API Specification

When cloud backup is enabled, recordings are uploaded via HTTP POST to the configured secure endpoint.

### Endpoint: `POST /api/v1/recordings/upload`
- **Headers**:
  - `Content-Type: multipart/form-data`
  - `X-Recording-Id: <id>`
  - `X-Encrypted: <true|false>`
  - `X-Duration-Ms: <ms>`
- **Body Parts**:
  - `video_file`: Binary file stream
  - `metadata`: JSON string containing title, timestamp, and hash
- **Response**:
  - `200 OK`: `{"status": "success", "backup_url": "https://storage.googleapis.com/.../rec_123.mp4"}`
  - `4xx / 5xx`: `{"status": "error", "message": "Failed to authenticate or process upload"}`
