# Original User Request

## 2026-10-14T03:12:07Z

Execute a comprehensive end-to-end stabilization, security hardening, and performance remediation of the LinktoMac application across all three architectural layers: macOS Native Client (C++/Objective-C++/Metal), macOS Orchestrator & Web UI (Python/Pywebview/JavaScript), and Android Companion App (Kotlin).

Working directory: /Users/sanagariudbhav/Udbhav/Projects/LinktoMac
Integrity mode: development

## Requirements

### R1. Connection Stability & Ghost Connection Elimination
- **Eliminate TCP Port Probe Race Condition (`orchestrator/linktomac/backend.py` & `MirrorService.kt`)**: Remove the raw TCP `connect_ex` probe on port 27189 that consumes the single-client socket in `MirrorService.runServerMode()`. Direct local subnet/mDNS connections must launch `mirror_client` directly without burning the server socket, eliminating initial handshake contention and dropped keyframes.
- **Fix Remote Relay Session Recovery (`orchestrator/ui/script.js`)**: Ensure `attemptSessionRecovery()` updates Firestore (`users/{uid}/sessions/current`) with the newly negotiated Pinggy tunnel host and port when `res.mode === "remote"`, preventing Android from infinitely reconnecting to stale ports.
- **Eliminate Premature Firestore Writes (`orchestrator/ui/script.js`)**: Remove the eager write of `{ active: true, mode: "local" }` before `smart_connect` completes network probing, preventing conflicting state transitions and socket churn on Android.
- **Enforce Handshake Integrity (`native-client/src/mirror_client.cpp`)**: Require the standard 20-byte v2 handshake header (`HANDSHAKE_SIZE = 20`) and remove the corrupted 16-byte fallback partial-read path.

### R2. Video Pipeline Performance & Adaptive Bitrate (ABR)
- **Dual-Mode Video Capture (`android-app/.../ScreenEncoder.kt` & `MirrorService.kt`)**: Support hardware `MediaProjection` + `VirtualDisplay` as the primary 60 FPS capture pipeline, while maintaining the accessibility screenshot GPU `HardwareBuffer` rendering as an on-demand fallback when zero-permission mode is selected.
- **Wire Up ABR Feedback (`native-client/src/mirror_client.cpp` & `ScreenEncoder.kt`)**: Emit `EVENT_TYPE_NETWORK_FEEDBACK` (0x07) periodically from `MirrorClient::decode_thread_func()` based on dropped frame stats and jitter, allowing Android's `ScreenEncoder.onNetworkFeedback()` to dynamically throttle bitrates between 2 Mbps and 8+ Mbps during network congestion.
- **Auto-Resize Window on Device Rotation (`native-client/src/renderer.mm`)**: Dynamically invoke `SDL_SetWindowSize` inside `Renderer::reallocate_texture()` when the stream aspect ratio flips between portrait and landscape.

### R3. Input Control & Gesture Smoothness
- **Continuous Drag & Swipe Watchdog (`LinktoMacAccessibilityService.kt`)**: Marshal touch event mutation to the main thread looper, buffer `ACTION_MOVE` coordinates, and chain `continueStroke` executions on `GestureResultCallback.onCompleted()` to prevent gesture cancellation mid-drag.
- **Trackpad Scroll Delta Coalescing (`native-client/src/input_handler.cpp` & `LinktoMacAccessibilityService.kt`)**: Coalesce high-frequency macOS trackpad `SDL_MOUSEWHEEL` events in `InputHandler` (throttled to 80-100ms intervals) and avoid cancelling in-flight scroll gestures on Android.

### R4. Security Hardening & Multi-Tenant Cloud Isolation
- **Eradicate Global Account Collisions (`orchestrator/ui/script.js` & `MainActivity.kt`)**: Completely remove reads and writes to global `accounts/latest`. Android authentication must strictly route through loopback web auth (`launchLoopbackWebSignIn()`) or user-scoped documents.
- **Enforce Client Authorization on Port 27189 (`android-app/.../MirrorService.kt`)**: Remove unconditional `ApprovedClientsStore.approveClient()` auto-approvals on incoming raw sockets. Require valid authorization handshake or paired tokens before streaming.

## Acceptance Criteria

### Automated Build & Test Integrity
- [ ] `python3 -m unittest discover -s orchestrator -p "test_*.py"` passes 100% of test cases.
- [ ] `make client` compiles `mirror_client` with zero compilation errors or warnings.
- [ ] `./gradlew assembleDebug` in `android-app/` builds cleanly.

### Functional & Stability Verification
- [ ] Direct local connection establishes on the first attempt without ghost probe socket teardown, dropped initial keyframes, or UI flicker.
- [ ] Remote relay session drops trigger `attemptSessionRecovery()`, which publishes the updated Pinggy host/port to Firestore and successfully reconnects the Android client.
- [ ] Device rotation immediately updates the native macOS window size to match the new aspect ratio.
- [ ] Continuous mouse/trackpad drags and scrolling operate smoothly without dropped gestures or stutter.
- [ ] Multiple users signing in never collide on a shared `accounts/latest` Firestore document.

## 2026-10-14T13:28:36Z

Resolve all remaining real-world runtime bugs, dead/stubbed code, and state-machine traps in LinktoMac across the macOS Native Client (C++/Metal), macOS Orchestrator & Web UI (Python/JS), and Android Companion App (Kotlin) to achieve true 100% production readiness across all user flows.

Working directory: /Users/sanagariudbhav/Udbhav/Projects/LinktoMac
Integrity mode: development

## Requirements

### R1. Android Capture Flow & Frictionless LAN Auto-Authorization
- **Eliminate 10 FPS Accessibility Trap (`MainActivity.kt` & `ScreenEncoder.kt`)**: Prevent `MainActivity` from falling back to `accessibilityCaptureLoop()` without user awareness. When starting a stream or receiving remote session triggers, prompt for `MediaProjection` permissions via `requestStreamingPermissionsAndStart()`. If running without projection, ensure the UI exposes an immediate "Upgrade to 60 FPS Capture" action rather than hiding stream controls. Never enter a tight sleep spin when the accessibility service is disabled.
- **Frictionless Same-Account LAN Auto-Authorization (`MirrorService.kt`, `MainActivity.kt`, `backend.py`, `script.js`)**: Eliminate silent connection drops on first-time local Wi-Fi pairing. When the Mac initiates a connection, write the Mac's local LAN IP to Firestore (`users/{uid}/sessions/current`). `MainActivity`'s session listener will pre-authorize the Mac's IP in `ApprovedClientsStore` before the incoming socket arrives, enabling `MirrorService.isClientAuthorized()` to approve paired devices automatically without blocking or timing out on a foreground `AlertDialog`.

### R2. Mid-Stream Screen Rotation & VideoToolbox Hardware Frame Pool Resilience
- **Dynamic Resolution & Hardware Frame Pool Reallocation (`native-client/src/decoder.cpp`, `protocol.h`, `MirrorService.kt`)**: Ensure mid-stream orientation flips (portrait <-> landscape) never freeze or stall the VideoToolbox hardware decoding pipeline. In `Decoder::decode()`, detect dimension flips or format change signals before frame submission to prevent `avcodec_receive_frame()` from stalling on a fixed-dimension `hw_frames_ctx`. Reallocate the hardware frame pool cleanly with updated dimensions, maintaining continuous 60 FPS rendering in Metal/SDL2.

### R3. Hardware HEVC Selection & Wire Protocol Alignment
- **Enable Hardware HEVC (`android-app/.../ScreenEncoder.kt`)**: Update `selectSupportedMimeType()` to query `MediaCodecList` for hardware-accelerated `MIMETYPE_VIDEO_HEVC` encoders before defaulting to AVC. Correctly set `FLAG_CODEC_HEVC` in the 20-byte handshake when HEVC is selected.
- **Strict Network Feedback Wire Protocol Alignment (`StreamProtocol.kt`)**: Ensure `StreamProtocol.createNetworkFeedbackPacket()` serializes exactly 9 bytes (`0x07` opcode + float32 `loss_rate` + float32 `rtt_ms`), matching the wire format in `MirrorService.readControlEvents()` and `network.cpp`. Eradicate the obsolete 8-byte `timestampUs` field.

### R4. Codebase Sanitization, Menu Bar Modernization & Dead Stub Removal
- **Prune Cosmetic / Dead QUIC & 0-RTT Stubs (`native-client/src/network.h`, `network.cpp`)**: Remove non-functional QUIC packet header structs and incomplete 0-RTT file reading that transmit nothing over the wire. Keep the networking layer clean, transparent, and focused on robust TCP transport.
- **Modernize Menu Bar Application (`orchestrator/menubar_app.py`, `linktomac/menubar_app.py`)**: Fix broken method invocations (`pair_device`, `_find_adb_path`), replace missing third-party dependencies (`rumps`, `pyperclip`) with standard macOS utilities (`pbcopy`/`osascript`) or consolidate cleanly into the main orchestrator CLI/app.
- **Permanently Remove Obsolete Java Modules**: Permanently delete legacy directories `android-server/` and `android-client/` that use outdated 8-byte protocols and cause workspace clutter.

## Acceptance Criteria

### Automated Build & Test Integrity
- [ ] `python3 -m unittest discover -s orchestrator -p "test_*.py"` passes 100% of test cases.
- [ ] `make client` and `cmake --build native-client/build --clean-first` compile cleanly with 0 errors and 0 warnings.
- [ ] `./gradlew clean testDebugUnitTest assembleDebug` in `android-app/` builds cleanly and passes 100% of unit tests.

### Functional & Stability Verification
- [ ] First-time local Wi-Fi connection succeeds seamlessly when `MainActivity` is in the background without timeout or dropped handshake.
- [ ] Rotating the Android device mid-stream flips aspect ratio smoothly without VideoToolbox hardware frame pool stalls or Metal rendering corruption.
- [ ] Devices supporting hardware HEVC stream via H.265 with `FLAG_CODEC_HEVC` properly negotiated in the 20-byte handshake.
- [ ] `createNetworkFeedbackPacket()` wire serialization is exactly 9 bytes and passes automated fuzzing/validation tests.
- [ ] Obsolete Java subprojects `android-server/` and `android-client/` are completely removed, and `all_code.md` is refreshed with the clean codebase.


## 2026-10-16T09:11:10Z

Comprehensive stabilization, bug resolution, and end-to-end reliability overhaul across the LinktoMac macOS orchestrator, native C++ video/input client, and Android companion to achieve 100% stable mirroring over Wi-Fi, USB ADB, and remote tunnels.

Working directory: /Users/sanagariudbhav/Udbhav/Projects/LinktoMac
Integrity mode: demo

## Requirements

### R1. macOS Orchestrator & UI Resiliency
Eliminate all runtime crashes, thread deadlocks, and resource leaks across the macOS Python orchestrator, Web UI, and menu bar application. Pairing, authentication, discovery listener restarts, and process monitors must handle failures gracefully without freezing the UI or leaving orphaned background threads.

### R2. Native C++ Streaming Pipeline & Transport Integrity
Ensure the native mirroring client (`native-client`) correctly processes video frames, multiplexed stream packets, and touch/keyboard input without memory safety violations, socket file descriptor races, or color space inversions (e.g. UV channel swaps). The client must build reliably, cleanly accept arguments, and shut down deterministically.

### R3. Android Companion & Protocol Concurrency
Ensure the Android companion and server components reliably capture, encode, and transmit the screen buffer while handling device rotation, network interface selection, and input injection without unhandled exceptions or connection dropouts.

### R4. Comprehensive Automated Test Validation
All existing and newly created adversarial and regression test suites across Python orchestrator, native client, and Android must execute and pass cleanly under non-interactive automated test runners.

## Acceptance Criteria

### Stability & Error Handling
- [ ] No unhandled exceptions or thread lockups when starting, switching, or cancelling pairing and remote sessions.
- [ ] The menu bar app, web dashboard, and CLI mirror processes shut down cleanly with 0 zombie subprocesses and 0 leaked socket descriptors.
- [ ] TLS certificate generation and pairing handshakes complete successfully without `TypeError` or missing backend method errors.

### Media & Input Streaming Correctness
- [ ] The C++ native client compiles with zero build errors and handles stream frames and packet demultiplexing without data corruption or memory leaks.
- [ ] Screen projection renders accurately with correct color channel mapping (no red/blue inversions) and responds to touch and keyboard input across display orientations.
- [ ] Reconnecting or restarting discovery after a network interruption recovers automatically without requiring an application restart.

### Verification Suite
- [ ] All unit and adversarial tests under `orchestrator/` pass: `python3 -m unittest discover -s orchestrator`.
- [ ] All adversarial stress tests under `tests/` pass without timeouts or assertion failures.
- [ ] Native C++ test harnesses in `tests/adversarial_native/` compile and pass.

## 2026-10-19T14:44:13Z

Fix critical platform bugs, broken packaging, Android 12–15 display capture incompatibilities, and architectural contradictions in LinktoMac to make it a reliable, production-ready, open-source wireless and USB screen mirroring solution for Android and macOS.

Working directory: /Users/sanagariudbhav/Udbhav/Projects/LinktoMac
Integrity mode: development

## Requirements

### R1. USB ADB Mode & Android 12–15 Display Capture Modernization
Modernize the ADB headless server (`android-server`) to support Android 12, 13, 14, and 15 without referencing removed reflection APIs (`SurfaceControl.openTransaction`). Ensure the build system automatically resolves the host Android SDK platform, compiles with 0 errors, and runs Dalvik/D8 dexing so that `server.jar` contains a valid `classes.dex` executable via `app_process`.

### R2. Local-First Zero-Config Connection Architecture
Refactor the connection and UI flow so that local-first mirroring (Wi-Fi mDNS auto-discovery and direct USB cable) is the primary, zero-config default experience. The desktop and mobile apps must launch directly into the local Connection Hub without blocking on Google Sign-In, cloud Firestore, or external internet access. Retain Google/Firebase authentication solely as an optional secondary tab for remote internet relay.

### R3. macOS Application Packaging & Dynamic Linker Hardening
Fix `build_mac_app.sh` and `backend.py` binary search paths so that `mirror_client` is packaged and located reliably inside the macOS `.app` bundle, preventing `FileNotFoundError` upon launch. Ensure SDL2 dependency resolution is dynamic (supporting both Apple Silicon `/opt/homebrew` and Intel `/usr/local`), and produce a clean `.dmg` installer.

### R4. Video Pipeline & Codec Negotiation Integrity
Enable genuine dynamic codec negotiation in the Android video encoder (`ScreenEncoder.kt` and `android-server`), selecting hardware HEVC (H.265) when supported by the device and falling back cleanly to AVC (H.264). Eliminate hardcoded single-codec restrictions and remove low-framerate fallback capture loops.

### R5. Codebase Cleanup
Completely remove the obsolete, legacy `android-client/` directory to eliminate contributor confusion and reduce repo bloat.

### R6. Automated Verification & Regression Guardrails
Ensure all subprojects (`android-server`, `android-app`, `native-client`, `orchestrator`) compile and pass their automated test suites cleanly, with zero regressions.

---

## Acceptance Criteria

### 1. Android Server (`android-server`)
- [ ] `./gradlew build` (or `./gradlew jar`) succeeds with 0 compiler errors on modern Android SDK platforms (API 34/35).
- [ ] Output `server.jar` contains valid Dalvik `classes.dex` verified via `jar tf`.
- [ ] `ScreenCapture.java` does not call `SurfaceControl.openTransaction()` or `SurfaceControl.closeTransaction()`.

### 2. macOS Packaging (`build_mac_app.sh`)
- [ ] `./build_mac_app.sh` completes cleanly and produces `dist/LinktoMac.app` and `dist/LinktoMac.dmg`.
- [ ] `mirror_client` is bundled in the `.app` bundle and matches the path resolved by `LinktoMacBackend`.
- [ ] Script dynamically resolves `sdl2` without hardcoded `/opt/homebrew` paths and without referencing `sdl3`.

### 3. Connection & UX Architecture
- [ ] Mac Desktop app (`ui_app.py`) opens directly into the Connection Hub without requiring Google Sign-In or external internet connectivity.
- [ ] Discovered devices list populates via local mDNS/Zeroconf when phone is on the same Wi-Fi network.
- [ ] USB mode triggers ADB port forwarding and connects reliably.
- [ ] Obsolete directory `android-client/` is completely removed from the filesystem.

### 4. Video Pipeline & Codecs
- [ ] `ScreenEncoder.kt` dynamically inspects encoder capabilities and selects HEVC when available, with AVC fallback.
- [ ] Native `mirror_client` continues to decode both H.264 and HEVC streams via VideoToolbox/FFmpeg without assertion failures.

### 5. Test Suite & Verification Integrity
- [ ] All unit tests in `orchestrator` pass: `python3 -m unittest discover -s orchestrator`.
- [ ] Android companion app builds cleanly: `cd android-app && ./gradlew :app:assembleDebug`.
- [ ] Native client builds cleanly: `cd native-client && cmake -B build && cmake --build build`.
