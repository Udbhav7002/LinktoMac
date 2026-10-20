# Project: LinktoMac Consolidation & Hardening

## Architecture
LinktoMac is a high-performance, ultra-low latency screen mirroring and remote control system linking Android devices to macOS.
The architecture consists of:
1. **Android Application (`android-app/`)**:
   - Single unified Kotlin Android application (`com.linktomac.app`, targetSdk 35).
   - `MirrorService`: Foreground service with MediaProjection, MediaCodec H.264/HEVC encoder, bounded frame queue, and socket server/client on port 27189.
   - `LinktoMacAccessibilityService`: Touch, scroll, continuous multi-stroke drag, keycode, and UTF-8 text injection.
   - `NsdManager`: Native mDNS broadcaster (`_linktomac._tcp.local.`) on port 27189.
   - `ApprovedClientsStore`: Pairing security, client UUID authorization, and "Allow this Mac?" dialog.
2. **macOS Native Client (`native-client/`)**:
   - C++/Metal/SDL native client (`mirror_client`) for ultra-low latency hardware-accelerated video decoding (VideoToolbox/FFmpeg) and input capture.
   - Protocol version 2 with binary multiplexed stream on port 27189.
   - Decoupled navigation key mapping, UTF-8 text transmission (`0x06`), on-demand keyframe sync (`0x05`), and macOS shortcuts (`Cmd+H`, `Cmd+B`, `Cmd+Tab`/`Cmd+S`, `Cmd+P`, `Cmd+V`).
3. **macOS Orchestrator & Connection Hub (`orchestrator/`)**:
   - Python/pywebview desktop application (`ui_app.py` & web UI) opening directly to zero-config Connection Hub.
   - Background `UsbDeviceWatcher` polling `adb devices -l` for 1-click USB mirroring.
   - Zeroconf mDNS discovery listener for auto-discovering Wi-Fi Android devices and connecting direct TCP.
   - Hardened local HTTP callback with CSRF state tokens; secured remote tunneling.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Socket Write Synchronization | Mutex lock and handshake gating on `outputStream` to eliminate socket write races between handshake and encoder threads | M1 | R1 |
| 2 | Bounded Frame Queue & Backpressure | Bounded queue (capacity 2-3) on Android encoder dropping non-reference frames under congestion without latency explosion | M1 | R1 |
| 3 | Dynamic Display Metrics & Orientation | Query real WindowManager/DisplayMetrics aspect ratio and resolution instead of hardcoded 720x1600; adapt Mac renderer aspect ratio | M1 | R1 |
| 4 | On-Demand Keyframe Sync (`0x05`) | Implement 1-byte IDR sync request (`0x05`) on Mac client and Android `PARAMETER_KEY_REQUEST_SYNC_FRAME` | M1 | R1 |
| 5 | Continuous Gesture Dragging | Continuous multi-stroke gestures (`StrokeDescription` with `willContinue = true` and `continueStroke`) in `LinktoMacAccessibilityService` | M2 | R2 |
| 6 | Decoupled Navigation Keyboard Mapping | Map Backspace (67), Enter (66), Tab (61), Esc (4), and Arrows (19-22) from SDL keysyms to Android keycodes | M2 | R2 |
| 7 | UTF-8 Text Transmission | Decouple typing from raw keycodes; send `EVENT_TYPE_TEXT` (0x06) packets from Mac SDL text input and inject via `ACTION_SET_TEXT` | M2 | R2 |
| 8 | macOS System Shortcuts | Intercept `Cmd+H` (Home), `Cmd+B` (Back), `Cmd+Tab`/`Cmd+S` (Recents), `Cmd+P` (Power/Lock), `Cmd+V` (Clipboard) | M2 | R2 |
| 9 | Single Android App Consolidation | Standardize on `android-app`; deprecate/clean up redundant `android-client` and `android-server` in scripts and build configs | M3 | R3 |
| 10 | Native Wi-Fi mDNS Auto-Discovery | Android `NsdManager` broadcast (`_linktomac._tcp.local.`) on port 27189; Mac auto-discovery and direct TCP connection | M3 | R3 |
| 11 | Background USB Device Watcher | Background watcher in macOS orchestrator polling `adb devices -l` for instant 1-click cable mirroring | M3 | R3 |
| 12 | Zero-Config Connection Hub | Mac and Android apps launch directly to Connection Hub without mandatory Google sign-in gate | M3 | R3 |
| 13 | Port 27189 Security & Pairing | Pre-stream auth handshake, Android "Allow this Mac?" prompt, and `ApprovedClientsStore` on port 27189 | M4 | R4 |
| 14 | Package & Application ID Standardization | Standardize application ID to `com.linktomac.app` across manifests, build configs, scripts, and google-services | M4 | R4 |
| 15 | Remote Tunnel & Endpoint Hardening | Disable unauthenticated Pinggy remote tunnel by default; secure local HTTP `/callback` endpoint with CSRF state nonce | M4 | R4 |
| 16 | E2E Test Suite & Multi-Tier Verification | 4-tier opaque-box test suite + unit test verification (`pytest`, `./gradlew assembleDebug`, `cmake`/`make`) | M5 | Acceptance Criteria |
| 17 | Adversarial Coverage Hardening | White-box stress testing, race condition fuzzing, network jitter simulation, and security boundary tests | M6 | Phase 2 |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | M1: Streaming Core & Threading Stability | Synchronize socket writes, bounded queue backpressure, dynamic metrics, and 0x05 IDR request | None | PLANNED |
| 2 | M2: Input Controls & Smooth Dragging | Continuous dragging gestures, navigation key mapping, UTF-8 text transmission, macOS Cmd shortcuts | M1 | PLANNED |
| 3 | M3: Single-App Architecture & Connectivity | Consolidate to `android-app`, mDNS `NsdManager`, USB watcher, zero-config Connection Hub | M1 | PLANNED |
| 4 | M4: Security, Pairing & Packaging Hardening | Port 27189 auth gating, "Allow this Mac?" dialog, package ID `com.linktomac.app`, secure tunnel & callback | M3 | PLANNED |
| 5 | M5: E2E Test Suite & Test Runner | Implement 4-tier opaque box test suite + unit test & build verification | M1, M2, M3, M4 | PLANNED |
| 6 | M6: Adversarial Coverage Hardening | Tier 5 adversarial stress testing and coverage hardening | M5 | PLANNED |

## Interface Contracts
### Mac Native Client (`native-client`) ↔ Android (`MirrorService.kt`)
- **Port**: 27189 (TCP)
- **Handshake Packet (20 Bytes)**:
  - `[0x4C, 0x49, 0x4E, 0x4B]` (Magic "LINK", 4B)
  - `[0x00, 0x02]` (Version 2, 2B)
  - `[Width: 2B BE][Height: 2B BE]`
  - `[Flags: 2B BE]` (Codec: 0=H.264, 1=H.265/HEVC)
  - `[Reserved: 8B]`
- **Video Stream Packets**:
  - `[NAL Length: 4B BE][Annex-B / AVCC NAL Payload]`
- **Control Packets**:
  - `0x01`: Touch Event (14B) `[0x01][action: 1B][x: 4B BE][y: 4B BE][pressure: 4B float BE]`
  - `0x02`: Key Event (5B) `[0x02][action: 1B][keycode: 4B BE]`
  - `0x03`: Scroll Event (13B) `[0x03][x: 4B BE][y: 4B BE][hscroll: 2B float BE][vscroll: 2B float BE]`
  - `0x05`: Sync Request / IDR Request (1B) `[0x05]`
  - `0x06`: Text Event `[0x06][length: 4B BE][UTF-8 bytes]`
- **Security Auth Packet (Pre-stream)**:
  - `[0x4C, 0x4D, 0x41, 0x43]` (Magic "LMAC", 4B)
  - `[ClientUUID: 16B][ClientNameLen: 2B BE][ClientName: N B][TokenLen: 2B BE][Token: N B]`
  - Response: `0x06` (AUTH_OK) or `0x07` (AUTH_DENIED)

### macOS Orchestrator (`orchestrator/linktomac/`) ↔ UI Frontend (`ui/`)
- Pywebview Javascript API (`window.pywebview.api`):
  - `api.launch_mirror(ip)`: Direct TCP launch on port 27189.
  - `api.launch_mirror_usb()`: 1-click ADB forward port 27189 + launch.
  - `api.stop_mirror(ip)`: Graceful process group termination.
  - Callback: `window.updateDiscoveredDevices(devices)` and `window.updateUsbDevices(devices)`.

## Code Layout
- `android-app/app/src/main/java/com/linktomac/app/`:
  - `MainActivity.kt`: Zero-config Connection Hub UI, permissions, mDNS advertiser.
  - `MirrorService.kt`: Foreground service, TCP server/client, socket synchronization, frame queue backpressure.
  - `ScreenEncoder.kt`: MediaCodec H.264/HEVC encoding, dynamic metrics, IDR generation.
  - `LinktoMacAccessibilityService.kt`: Input injection, continuous gesture dragging, keycode mapping, UTF-8 text handling.
  - `StreamProtocol.kt`: Binary protocol packet serialization & constants.
  - `ApprovedClientsStore.kt`: Pairing store and security gating.
- `native-client/src/`:
  - `protocol.h`: Binary protocol definitions, byte order helpers.
  - `input_handler.cpp` / `.h`: Navigation keycodes, UTF-8 text sending, macOS shortcuts.
  - `mirror_client.cpp` / `.h`: Client connection, socket handling, on-demand IDR 0x05 emission.
  - `decoder.cpp` / `.h`: Hardware/software video decoding and packet parsing.
  - `renderer.mm` / `.h`: Aspect ratio handling and rendering.
- `orchestrator/`:
  - `ui_app.py`: Pywebview application entry point, non-blocking HTTP server, clean shutdown.
  - `ui/`: Connection Hub frontend (`index.html`, `script.js`, `style.css`).
  - `linktomac/backend.py`: Unified backend, `UsbDeviceWatcher`, direct TCP router, process management.
  - `linktomac/pairing.py`: Zeroconf discovery listener.
  - `linktomac/adb.py`: ADB wrapper, port forwarding, accessibility configuration.
