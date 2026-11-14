# Milestone 2 — Camera Foundation

## Scope delivered in this milestone

- Add the runtime CAMERA permission declaration and optional camera hardware feature.
- Request camera permission from the camera screen, with an app-settings path after a permanent denial.
- Discover available Camera2 devices and report lens facing, hardware level, flash availability, sensor orientation, and preview output sizes.
- Open a selected camera using Camera2.
- Display a live TextureView preview.
- Switch between front and rear cameras when both are available.
- Close the capture session and camera device when the screen pauses or is disposed.
- Add pure unit tests for preview-size selection.
- Align Gradle, Kotlin, Compose compiler, and KSP versions to a supported toolchain combination.

## Toolchain

- Android Gradle Plugin: 9.3.1
- Gradle wrapper: 9.6.0
- Kotlin / Compose compiler plugin: 2.4.21
- KSP: 2.3.12
- Dagger Hilt: 2.60.1
- Compose BOM: 2026.09.00
- Compile SDK / Target SDK: 37
- Minimum SDK: 26
- Java compatibility: 17

The selected AGP version supports API 37 and requires Gradle 9.5.0 or newer. Kotlin 2.4.21 is within its documented AGP compatibility range through AGP 9.3.1. KSP 2.3.12 supports Kotlin 2.3.20–2.4.x, AGP 8.13 or newer, and Gradle 8.13 or newer.

## Local verification required

Run on a machine with JDK 17 and Android SDK 37 installed:

```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat help
.\gradlew.bat :app:assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Then install on a physical device and verify:

1. Permission grant, denial, and app-settings recovery.
2. Rear camera preview.
3. Front/rear switching.
4. Rotation and preview crop on portrait and landscape orientations.
5. Leaving and returning to the camera screen.
6. Backgrounding and foregrounding the app.
7. Opening another camera app and returning to DualFrame.

## Known limitations

- This milestone creates one live preview only.
- It does not capture photos or record video.
- It does not yet produce portrait and landscape outputs simultaneously.
- Preview rotation and crop behavior must be validated on physical devices with different sensor orientations.
- Camera availability and supported resolutions vary by device.

## Acceptance criteria

- [ ] Gradle configuration succeeds with the pinned toolchain.
- [ ] Debug APK assembles.
- [ ] Unit tests pass.
- [ ] Lint findings are reviewed.
- [ ] Permission states behave correctly on a physical device.
- [ ] Camera opens, previews, switches, and closes without leaking the camera resource.
- [ ] Portrait and landscape device rotation are verified on a physical device.
