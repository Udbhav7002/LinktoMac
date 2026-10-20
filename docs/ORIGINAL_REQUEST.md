# Original User Request

## 2026-10-10T09:29:40Z

This is a single self-contained fix; keep it small and focused. Complete the LinktoMac consolidation and connectivity overhaul (R3 and R4) on top of the completed R1 (Streaming Core) and R2 (Input Controls) implementations in the workspace.

Working directory: /Users/sanagariudbhav/Udbhav/Projects/LinktoMac
Integrity mode: demo

## Requirements

### R1. Verify & Preserve Completed Streaming & Input Core (M1 & M2)
- Preserve the existing thread-safe socket write locks, 4-tier `BoundedFrameQueue` (`NalParser.kt`), dynamic `DisplayMetricsHelper`, and `0x05` IDR keyframe sync in `MirrorService.kt` and `native-client`.
- Preserve the continuous multi-stroke gesture dragging (`willContinue = true` and `continueStroke`), `EVENT_TYPE_TEXT` (`0x06`) UTF-8 text injection, navigation keycode mapping, and macOS `Cmd` shortcuts (`Cmd+H`, `Cmd+B`, `Cmd+Tab`/`Cmd+S`, `Cmd+P`, `Cmd+V`).

### R2. Single-App Architecture & Frictionless Connectivity (M3 — Looks & Flow First)
- Standardize on `android-app` as the sole Android codebase; deprecate/remove `server.jar` dependencies from `backend.py`, `cli.py`, and build scripts.
- Implement native Android `NsdManager` broadcast (`_linktomac._tcp`) on port 27189 in `MainActivity.kt` / `MirrorService.kt` for instant local Wi-Fi discovery on Mac.
- Implement background `UsbDeviceWatcher` in `orchestrator/linktomac/backend.py` polling `adb devices -l` for 1-click USB mirroring, and update `launch_mirror(ip)` to connect `mirror_client` directly to `<ip>:27189` (or `127.0.0.1:27189` with `adb forward tcp:27189 tcp:27189` for USB).
- Remove the mandatory Google sign-in gate: open both macOS (`orchestrator/ui/index.html` & `script.js`) and Android (`activity_main.xml` & `MainActivity.kt`) directly to the zero-config Connection Hub.

### R3. Security, Pairing & Package Standardization (M4 — Security Last)
- Standardize `applicationId` to `com.linktomac.app` across `android-app/app/build.gradle`, `google-services.json`, and `orchestrator/linktomac/adb.py`.
- Gate port 27189 on Android with client authorization ("Allow this Mac?" prompt and `ApprovedClientsStore` for Wi-Fi clients, auto-allowing `127.0.0.1` USB connections).
- Secure local HTTP `/callback` in `orchestrator/ui_app.py` with a CSRF state token and disable raw unauthenticated Pinggy tunnel by default.

## Acceptance Criteria

### Build & Test Verification
- [ ] Android app compiles and all unit tests pass: `cd android-app && ./gradlew :app:assembleDebug testDebugUnitTest`.
- [ ] Native C++ client compiles cleanly: `cmake -S native-client -B native-client/build && cmake --build native-client/build`.
- [ ] Python orchestrator test suites pass: `pytest orchestrator/test_backend.py orchestrator/test_ui_app.py orchestrator/test_adversarial_mac.py`.

### Functional Completeness
- [ ] `MainActivity.kt` registers `_linktomac._tcp` via `NsdManager` on port 27189.
- [ ] `backend.py` implements `UsbDeviceWatcher` and routes `launch_mirror` directly to port 27189 without `server.jar`.
- [ ] `orchestrator/ui/index.html` opens directly to `#dashboard-screen` (Connection Hub) without blocking on `#login-screen`.
- [ ] `applicationId` is standardized to `com.linktomac.app`.
