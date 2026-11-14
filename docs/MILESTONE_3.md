# Milestone 3 — OpenGL Dual Composition Preview

## Scope

- Replace the direct TextureView camera target with an OpenGL ES 2.0 external texture input.
- Camera2 produces one preview stream into a Surface backed by SurfaceTexture.
- OpenGL samples that one external OES texture.
- Render two independently cropped compositions:
  - Portrait: 9:16 centered crop.
  - Landscape: 16:9 centered crop.
- Fit both compositions into separate preview regions without stretching.
- Preserve camera permission, discovery, and front/rear switching.
- Add unit tests for crop calculations.

## Pipeline

```text
Camera2
  |
  v
Surface -> SurfaceTexture -> GL_TEXTURE_EXTERNAL_OES
                                  |
                                  v
                         OpenGL ES 2.0 shader
                           /             \
                          v               v
                    9:16 crop         16:9 crop
                          \               /
                           v             v
                         Dual preview surface
```

## Limitations and validation

- This milestone renders two live compositions; it does not export photos or record video.
- Initial crop math assumes a 16:9 camera input and a centered portrait crop.
- Sensor/display rotation and front-camera mirroring are not yet implemented in the GL shader; verify and address them in the next refinement.
- The two viewports are preview surfaces, not encoder output surfaces.
- Camera and OpenGL behavior requires physical-device validation.

## Local verification

```powershell
git pull origin main
.\gradlew.bat :app:assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

## Device acceptance checklist

- [ ] Grant permission and open camera.
- [ ] Confirm both compositions update from one camera stream.
- [ ] Confirm portrait crop is centered and landscape retains the wider scene.
- [ ] Switch front/rear camera.
- [ ] Background/foreground the app.
- [ ] Navigate away and reopen camera.
- [ ] Validate rotation and front-camera mirroring.
- [ ] Check GL errors, camera disconnects, and resource leaks.
