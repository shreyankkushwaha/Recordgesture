# Technical Architecture Decisions (ADR)

## ADR 001: Selection of Hardware Trigger Mechanisms
- **Context**: The user needs to start a video recording with microphone audio using a single predefined gesture or hardware-button action, including when the phone is on the lock screen.
- **Alternatives Considered**:
  1. *Power Button Double-Tap*: Historically intercepted via `CAMERA` action intents, but Android OS restricts third-party re-mapping on many modern OEM skins.
  2. *Volume Button Sequences*: Captured via `AccessibilityService.onKeyEvent`. Supported across all Android versions, even when locked, requiring only explicit user consent.
  3. *Quick Settings Tile*: Fully supported standard Android platform feature accessible directly on the lock screen shade.
- **Decision**: Provide the Volume Button double-press as the primary hardware button trigger, with Quick Settings Tile, Lock Screen Notification, and Shake gestures as officially supported alternative quick actions.

## ADR 002: CameraX over Camera2
- **Context**: Direct Camera2 implementation is verbose, vendor-prone, and prone to device-specific lifecycle race conditions.
- **Decision**: Use `androidx.camera:camera-video:1.5.0`. CameraX handles format negotiation, audio stream synchronization, surface attachment, and lifecycle management robustly across all Android devices.

## ADR 003: Privacy and Transparency Compliance
- **Context**: Android strictly prohibits background camera recording without explicit user knowledge and prominent foreground notification indicators.
- **Decision**: Always start a foreground service with `FOREGROUND_SERVICE_TYPE_CAMERA | FOREGROUND_SERVICE_TYPE_MICROPHONE`, display an ongoing notification with elapsed time and a prominent "Stop Recording" button, and render an unmistakable pulsing red recording indicator on the UI.
