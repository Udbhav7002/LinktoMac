# Remote IP Support Build Audit & Drift Analysis

**Date:** 2026-10-21  
**Author:** Multi-Platform Systems Engineer  
**Branch:** `feat/remote-ip-support`  
**Base:** `origin/main` (v1.61)

---

## 1. Executive Summary

This audit establishes the baseline across all LinktoMac subprojects (`allcode/LinkToMac-macOS`, `allcode/LinkToMac-Android`, `native-client`, `orchestrator`, `android-server`, `android-app`) before executing the WAN Remote IP Support enhancement.

The audit identifies architectural alignments and critical drifts between `all_code.md`, the phase specifications, and the existing codebase.

---

## 2. Identified Drift & Architectural Alignments

### 2.1 Android Project Structure & Firebase Configuration (Phase 1)
- **Repository Layout**:
  - `allcode/LinkToMac-Android` is the primary Kotlin Jetpack Compose companion app (`package com.linktomac.android`, `applicationId "com.linktomac.android"`).
  - `android-app` is the legacy companion app (`package com.linktomac.app`).
  - `android-app/app/google-services.json` is configured for package `com.linktomac.app` with Firebase project `linktomac`.
- **Drift**:
  - `allcode/LinkToMac-Android/app/` currently lacks `google-services.json`.
  - The `google-services.json` file in `android-app/app/` specifies `package_name: "com.linktomac.app"`. In order for Gradle's `com.google.gms.google-services` plugin to validate in `allcode/LinkToMac-Android` without build failure, `google-services.json` must declare `com.linktomac.android` (or client definitions for both packages).
  - `allcode/LinkToMac-Android/build.gradle.kts` (root) does not declare the `com.google.gms.google-services` plugin in its root plugins block. Gradle Kotlin DSL requires plugin declaration at the root or buildscript classpath before application in `app/build.gradle.kts`.
- **Resolution**:
  - Declare `id("com.google.gms.google-services") version "4.4.1" apply false` in `allcode/LinkToMac-Android/build.gradle.kts`.
  - Install a multi-package `google-services.json` in `allcode/LinkToMac-Android/app/` containing `com.linktomac.android` client credentials under the `linktomac` Firebase project.

### 2.2 Wire Handshake Direction in Video Channel (Phase 3)
- **Codebase State (`native-client/src/mirror_client.cpp`)**:
  - In server mode (`connect_mode_ == false`), `mirror_client` opens port 27189, waits for an incoming connection, and executes:
    `video_sock_.recv_exact(handshake, HANDSHAKE_SIZE)`
    It expects the connecting client (the Android screen capture source) to transmit the 20-byte handshake header:
    `[HANDSHAKE_MAGIC (4B)][version (4B)][width (4B)][height (4B)][flags (4B)]`.
  - The Android device is the screen capture source and determines the physical display resolution (e.g. 1080x2400) and H.264/H.265 codec flags.
- **Drift in Prompt Specification**:
  - The prompt's pseudo-code in Phase 3 suggested that Android's `startRemoteClient` should *read* a 20-byte handshake from the Mac `mirror_client`:
    `val magic = input.readInt()`
  - However, `mirror_client.cpp` does not send a handshake in server mode; it receives one. If Android waits to read 20 bytes from Mac while Mac is waiting to read 20 bytes from Android, a deadlock occurs.
- **Resolution**:
  - In `ScreenCameraStreamer.startRemoteClient`:
    1. Send the 36-byte null-padded authentication token.
    2. Transmit the 20-byte binary handshake packet (`HANDSHAKE_MAGIC`, version 2, `targetW`, `targetH`, flags=1).
    3. Stream H.264 NAL units formatted with 4-byte big-endian length prefix.
  - In `mirror_client.cpp`:
    1. Verify the 36-byte auth token via `verify_peer_token()`.
    2. Read and validate the 20-byte handshake packet via `recv_exact(handshake, HANDSHAKE_SIZE)`.
    3. Proceed to the NAL unit decode loop.

### 2.3 OpenSSL Linkage in Native Client (Phase 4)
- **Codebase State (`native-client/CMakeLists.txt`)**:
  - `CMakeLists.txt` currently links FFmpeg, SDL2, and Apple frameworks (`VideoToolbox`, `CoreMedia`, `CoreVideo`, `CoreFoundation`, `AppKit`).
  - OpenSSL is not currently linked.
- **Drift**:
  - Phase 4 requires wrapping the video socket in TLS 1.3 (`<openssl/ssl.h>`, `<openssl/err.h>`).
- **Resolution**:
  - Update `native-client/CMakeLists.txt` to find and link OpenSSL (`find_package(OpenSSL REQUIRED)` and target link `OpenSSL::SSL OpenSSL::Crypto`), pointing to `/opt/homebrew/opt/openssl@3` on macOS Apple Silicon.

### 2.4 Dual Pinggy Reverse Tunneling & Firestore Session Publishing (Phase 2)
- **Codebase State (`orchestrator/linktomac/backend.py`)**:
  - `start_remote_session()` previously ran `ssh -tt -p 443 -R0:localhost:27189 tcp@a.pinggy.io`, extracting a single URL for port 27189, and returned `{"success": True, "code": public_url}`.
  - Port 54921 (control envelope) was not forwarded.
- **Drift & Resolution**:
  - Expand SSH command to include `-R0:localhost:27189 -R0:localhost:54921`.
  - Extract both TCP URLs from Pinggy output.
  - Generate a secure 36-character hexadecimal token (`secrets.token_hex(18)`).
  - Launch `mirror_client` with `--video-port 27189 --auth-token <token>`.
  - Write session descriptor to Firestore under `users/{uid}/sessions/current` and `sessions/{sessionId}`.
  - Preserve backward compatibility by including `"code": f"{video_url['host']}:{video_url['port']}"` in the response dict alongside new fields (`video`, `control`, `token`, `session_id`).

### 2.5 Token Verification on Control Port 54921 (Phase 5)
- **Codebase State (`ConnectionManager.swift`)**:
  - `NWListener` accepts connections on port 54921 and sends `HELLO` immediately upon the connection state transitioning to `.ready`.
- **Resolution**:
  - Introduce `verifyPeerToken(_ connection: NWConnection) -> Bool` in `ConnectionManager.swift` that synchronously reads the first 36 bytes before sending `HELLO`.
  - Update Android `LinkConnectionService.connectToMac()` to send the 36-byte token immediately upon establishing the socket.

### 2.6 ADB Transport Isolation (Phase 6)
- **Codebase State (`ConnectionManager.swift`)**:
  - `sendRemoteTap` and `sendRemoteKeyEvent` invoke `ADBBridge.shared` unconditionally.
- **Resolution**:
  - Wrap `ADBBridge` calls with `if connectionTransport == "USB"` so ADB is only triggered when connected over a physical cable.

### 2.7 Adaptive Bitrate Feedback (Phase 7)
- **Codebase State (`android-server/src/main/java/com/linktomac/ScreenEncoder.java`)**:
  - Bitrate is currently static (`final int bitRate`).
- **Resolution**:
  - Introduce dynamic `currentBitrate` and `handleNetworkFeedback(byte[] packet)` method to adjust encoder parameters dynamically on `EVENT_TYPE_NETWORK_FEEDBACK = 0x07`.

### 2.8 Versioning & Release Metadata (Phase 10)
- Current version across `allcode/LinkToMac-Android/app/build.gradle.kts`, `android-app/app/build.gradle`, and `allcode/LinkToMac-macOS/Info.plist` is `1.61` (code `161`).
- Target release version is `1.62` (code `162`).

---

## 3. Pre-Flight Test Baseline
- Native client fuzzer: `tests/adversarial_m4/test_handshake_malformed_fuzzer.py` passed 100% (25 flood connections, dimension overflows, corrupt magics).
- Swift macOS app: compiles cleanly with Swift 6.4 / Xcode 27.
- OpenSSL 3: verified at `/opt/homebrew/opt/openssl@3`.
- Android SDK: verified at `/Users/sanagariudbhav/Library/Android/sdk`.
