# Milestone 2 — Camera Foundation

## Scope delivered

- Declare the runtime CAMERA permission and optional camera hardware feature.
- Request camera permission and provide an app-settings path after denial.
- Discover Camera2 devices and report lens facing, hardware level, flash availability, sensor orientation, and preview sizes.
- Open the selected camera with Camera2.
- Display one live camera preview.
- Switch between front and rear cameras when both are available.
- Close camera resources when the camera screen pauses or is disposed.
- Add unit tests for preview-size selection.

## Toolchain

- Android Gradle Plugin: 9.3.1
- Gradle wrapper: 9.6.0
- Kotlin / Compose compiler plugin: 2.4.21
- KSP: 2.3.12
- Dagger Hilt: 2.60.1
- Compile SDK / Target SDK: 36
- Minimum SDK: 26
- Java compatibility: 17

## Verification

```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat help
.\gradlew.bat :app:assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

## Historical boundary

Milestone 2 initially used a TextureView preview. Milestone 3 replaces that output with an OpenGL ES external texture pipeline.

## Device checks

- [ ] Permission grant, denial, and app-settings recovery.
- [ ] Rear camera preview.
- [ ] Front/rear switching.
- [ ] Background/foreground lifecycle.
- [ ] Returning to the camera screen.
- [ ] Camera release when another app takes camera access.

## Known limitations

Camera availability and supported resolutions vary by device. Physical-device validation is required.
