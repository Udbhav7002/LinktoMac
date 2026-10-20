# LinktoMac Android Companion App

This directory is reserved for the Android companion application.
To make LinktoMac a true "One-Shot" application, you need to implement an Android app that:

1. Uses `MediaProjection` to capture the Android screen.
2. Uses `MediaCodec` to encode the frames in H.265 (HEVC).
3. Connects to the Mac over TCP (port 27189) or listens if `AdbClient` forwarded it.
4. Uses `NsdManager` (mDNS) to broadcast the `_linktomac._tcp.local` service so the Mac automatically discovers the phone on the same Wi-Fi.

## Basic Structure:
- `MainActivity.kt`: Handles the PIN display and Permissions (Screen Capture).
- `MirrorService.kt`: A Foreground Service that runs the MediaCodec video encoder and streams sockets.
- `NsdBroadcaster.kt`: Broadcasts the mDNS service.

For USB mode, you can continue to use the headless `server.jar` located in `orchestrator/linktomac/android-server/server.jar`.
