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
