# DualFrame

A professional Android camera application designed to capture independently composed portrait and landscape photographs and videos from a shared camera stream.

## Vision

DualFrame aims to provide two independent visual compositions from a single camera input.

### Output formats

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
- Milestone 1: Android project foundation — implementation committed; local build verification pending.
- Milestone 2: runtime camera permission, Camera2 discovery, live preview, and front/rear switching — implementation committed; build and physical-device verification pending.

See [Milestone 1](docs/MILESTONE_1.md) and [Milestone 2](docs/MILESTONE_2.md).

## Minimum Android version

Android 8.0 (API 26).

## License

To be decided.
