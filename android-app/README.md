# LinktoMac Android Companion Module

Engineering documentation and architecture for the Android companion application subsystem.

```text
+-----------------------------------------------------------------------------+
|                     ANDROID COMPANION ARCHITECTURE                          |
+-----------------------------------------------------------------------------+
|                                                                             |
|  [ MainActivity ]           [ LinkConnectionService ]                       |
|   - Permission Requests      - TCP Socket Server (Port 27189/27190)         |
|   - Foreground Controls      - Foreground Service Lifecycle                 |
|   - PIN State Display        - mDNS Service Registration (Port 27191)       |
|                                                                             |
|  [ ScreenCameraStreamer ]   [ LinkTouchAccessibilityService ]               |
|   - MediaProjection          - MotionEvent Injection                        |
|   - Hardware MediaCodec      - Global Navigation Actions                    |
|   - Dynamic HEVC/AVC         - Text & IME Input Bridge                      |
|                                                                             |
+-----------------------------------------------------------------------------+
```

## Technical Specification

1. **Display Frame Acquisition**: Leverages Android `MediaProjection` to acquire virtual display buffers directly from the surface flinger.
2. **Hardware Video Compression**: Streams frames into on-device `MediaCodec` encoders with hardware-accelerated HEVC (H.265) or AVC (H.264) baseline profiles.
3. **Local Network Beacon**: Publishes `_linktomac._tcp.local` via `NsdManager` for automatic zero-configuration discovery by the macOS workstation.
4. **Input Dispatcher**: `LinkTouchAccessibilityService` dispatches gestures and touch sequences without requiring device rooting or continuous ADB tethering.

## Build Instructions

```bash
# Debug build
./gradlew assembleDebug

# Release APK build
./gradlew assembleRelease
```
