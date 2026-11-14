# Milestone 1 — Android Project Foundation

## Scope

- Preserve the Gradle 9.6.0 wrapper.
- Configure the Android application module and version catalog.
- Use JDK 17 and Java 17 compatibility.
- Configure Jetpack Compose, Material 3, Navigation Compose, Hilt, and KSP.
- Establish the `com.dualframe.camera` application package.
- Add the Hilt application class and Compose activity.
- Add the initial dark, gold-accented home screen.

## Toolchain

- Android Gradle Plugin: 9.3.1
- Gradle wrapper: 9.6.0
- Kotlin Compose compiler plugin: 2.4.21
- KSP: 2.3.12
- Dagger Hilt: 2.60.1
- Compose BOM: 2026.09.00
- Compile SDK: 36
- Target SDK: 36
- Minimum SDK: 26
- Java compatibility: 17

## Local verification

Run from the repository root on a machine with JDK 17 and Android SDK 36 installed:

```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat help
.\gradlew.bat :app:assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

## Acceptance criteria

- [ ] Gradle wrapper reports 9.6.0.
- [ ] Gradle runs on JDK 17.
- [ ] Gradle configuration and dependency resolution succeed.
- [ ] Debug APK assembles successfully.
- [ ] Unit tests pass.
- [ ] Lint completes and findings are reviewed.
- [ ] Application launches on an Android device or emulator.

## Next milestone

Milestone 2: camera permission flow and Camera2 capability discovery.
