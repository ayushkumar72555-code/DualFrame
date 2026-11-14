# DualFrame

An Android camera application designed to create independently composed portrait and landscape photographs and videos from a shared camera stream.

## Output formats

- Portrait: 9:16
- Landscape: 16:9

## Technology stack

- Kotlin
- Jetpack Compose
- Material 3
- Camera2 API
- OpenGL ES / EGL
- MediaCodec
- MediaMuxer
- Hilt
- Kotlin Coroutines

## Architecture

Clean Architecture with MVVM/MVI presentation.

## Development status

- Milestone 0: repository setup — completed.
- Milestone 1: Android foundation — implementation committed; CI verification pending.
- Milestone 2: Camera2 discovery and live preview — implemented; physical-device verification pending.
- Milestone 3: OpenGL dual composition preview — implemented; build and physical-device verification pending.

See [Milestone 1](docs/MILESTONE_1.md), [Milestone 2](docs/MILESTONE_2.md), [Milestone 3](docs/MILESTONE_3.md), and [Architecture](docs/ARCHITECTURE.md).

Minimum Android version: Android 8.0 (API 26).

License: To be decided.


## Dual viewfinder interface

The camera screen now supports two live layouts backed by the same Camera2 preview texture:

- **PiP Overlay:** a full-screen primary crop with a draggable, resizable secondary crop. Swap switches which aspect ratio is primary.
- **Stacked Split:** landscape and portrait compositions are shown in separate stacked viewports.
- **Composition controls:** quick zoom presets, rule-of-thirds grid, layout switching, PiP resizing/dragging, camera switching, and settings panel.

The OpenGL renderer applies the independent 9:16 and 16:9 crops. Zoom is applied in the preview shader. Device orientation correction, front-camera mirroring, tap-to-focus, paired still-photo output, MediaCodec video recording, and MediaStore saving remain follow-up implementation work; the interface does not claim these functions are ready.

### Android SDK

The project compiles against Android API 37 to satisfy the current AndroidX dependency metadata. The app target SDK remains 36 until Android 17 behavior compatibility is deliberately validated.
