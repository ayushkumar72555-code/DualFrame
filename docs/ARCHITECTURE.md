\# DualFrame Architecture



\## Camera Input



Android Camera2 API provides the shared camera stream.



\## Graphics Engine



OpenGL ES and EGL process the camera input.



Two independent rendering outputs are generated:



\- Portrait: 9:16

\- Landscape: 16:9



\## Application Layers



\- Presentation

\- Domain

\- Data

\- Camera

\- Graphics

\- Recording

\- Storage

\- Core

\- Dependency Injection



\## Recording



MediaCodec handles video encoding.

MediaMuxer generates the video containers.



\## Storage



Android MediaStore manages captured media.

