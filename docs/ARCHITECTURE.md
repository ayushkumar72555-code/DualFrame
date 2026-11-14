# DualFrame Architecture

## Camera Input

Android Camera2 API provides one shared camera stream.

## Graphics Engine

OpenGL ES and EGL will process the shared camera input.

Two independent rendering outputs will be generated:

- Portrait: 9:16
- Landscape: 16:9

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

MediaCodec handles video encoding.

MediaMuxer generates the video containers.

## Storage

Android MediaStore manages captured media.

## Current implementation boundary

Milestone 2 opens one Camera2 device and displays one live TextureView preview. Dual rendering, photo capture, video encoding, and media storage are future milestones.
