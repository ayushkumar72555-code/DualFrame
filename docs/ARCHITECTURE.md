# DualFrame Architecture

## Camera Input

Android Camera2 API produces one shared preview stream into a Surface backed by SurfaceTexture.

## Graphics Engine

OpenGL ES 2.0 samples the external OES texture and renders two independently cropped preview compositions:

- Portrait: 9:16 centered crop.
- Landscape: 16:9 composition.

Both previews are generated from the same camera frame and do not open a second physical camera.

## Application Layers

- Presentation
- Domain
- Data
- Camera
- Graphics
- Recording
- Storage
- Core
- Dependency Injection

## Recording

MediaCodec will encode video frames. MediaMuxer will package the encoded output.

## Storage

Android MediaStore will manage captured media.

## Current implementation boundary

Milestone 3 displays two live preview compositions using OpenGL ES. Photo capture, encoder surfaces, video recording, saving, sensor/display rotation correction, and front-camera mirroring are not yet implemented.
