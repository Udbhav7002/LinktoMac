# LinktoMac Remote IP Support — Implementation Worklog

## Overview
Extending LinktoMac screen-mirror and remote-control ecosystem to operate seamlessly across different networks (cellular, NAT'd Wi-Fi, cross-network) via dual-port Pinggy relay, Firestore discovery, TLS 1.3 encryption, and shared-secret authentication.

---

## Phase Status Checklist

- [x] **Phase 0 — Audit & Branch Setup**
  - Branch created: `feat/remote-ip-support`
  - Canonical review of `all_code.md` and repository components
  - Documented drift and architectural alignment in `docs/REMOTE_BUILD_AUDIT.md`
- [x] **Phase 1 — Android Cloud Sync Listener**
  - Configured Firebase dependencies (`firebase-auth-ktx`, `firebase-firestore-ktx`, `play-services-auth`) and multi-package `google-services.json`
  - Implemented `CloudSessionSync.kt` listening to Firestore `users/{uid}/sessions/current`
  - Integrated `CloudSessionSync` and `disconnect()` into `LinkConnectionService.kt`
  - Added unit test suite `CloudSessionSyncTest.kt` verifying snapshot-driven relay connection triggers (< 2s)
  - Unit tests verified: `./gradlew testDebugUnitTest` passed cleanly
- [x] **Phase 2 — Dual-Port Relay**
  - Updated `start_remote_session()` in `orchestrator/linktomac/backend.py` to open dual reverse tunnels for video (27189) and control (54921)
  - Implemented dual URL parser (`_extract_pinggy_url`) extracting both endpoints from Pinggy log output
  - Added Firestore publishing logic (`_publish_session_to_firestore`, `_publish_session_to_user`) saving session token, IDs, and endpoints
  - Patched `smart_connect(self, device_data, uid=None)` and `ui_app.py` to seamlessly route authenticated user sessions to Firestore
  - Added Phase 2 acceptance test verifying dual URL parsing, mirror_client `--auth-token` argument, and Firestore persistence; all 17 tests passed cleanly
- [x] **Phase 3 — Android Outbound Video Client**
  - Extended `ScreenCameraStreamer.kt` with `startRemoteClient(host, port, token)` initiating outbound video TCP client toward Pinggy relay
  - Implemented hardware MediaCodec H.264 encoder in `ScreenCameraStreamer` streaming NAL units with 4-byte length prefix
  - Added `--auth-token` CLI flag support in `native-client/src/main.cpp`
  - Implemented constant-time 36-byte token verification in `MirrorClient::verify_peer_token()` on listen accept
  - Added Phase 3 acceptance test `test_remote_token_handshake.py` verifying token rejection of unauthorized clients and successful acceptance of valid token + handshake
- [x] **Phase 4 — TLS 1.3 on Both Streams (Release Blocker)**
  - Wrapped `TcpConnection` socket in OpenSSL TLS 1.3 (`connect_tls`, `listen_and_accept_tls`) in `native-client/src/network.h` and `network.cpp`
  - Configured CMakeLists.txt to link OpenSSL 3 (`OpenSSL::SSL`, `OpenSSL::Crypto`)
  - Implemented `makeTlsSocket` in `LinkConnectionService.kt` with strict `TLSv1.3` context for remote relay endpoints
  - Acceptance test verified: TLS 1.3 succeeded, lower TLS versions (TLS 1.2, etc.) strictly refused
- [x] **Phase 5 — Shared-Secret Token Handshake (Release Blocker)**
  - Configured 36-byte shared secret token generated in `backend.py`, published to Firestore and passed to `mirror_client --auth-token`
  - Added asynchronous token verification `verifyPeerTokenAsync` in `ConnectionManager.swift` on port 54921
  - Transmitted 36-byte token first in `LinkConnectionService.kt` (control) and `ScreenCameraStreamer.kt` (video)
  - Acceptance test verified: unauthorized connections refused within 0.6ms (< 100ms threshold)
- [ ] **Phase 6 — ADB Fallback for Remote Mode**
- [ ] **Phase 7 — Adaptive Bitrate Over WAN**
- [ ] **Phase 8 — Session Recovery Hardening**
- [ ] **Phase 9 — Security & Pen-Test Pass**
- [ ] **Phase 10 — Release (v1.62)**

---

## Detailed Phase Execution Log

### Phase 0: Audit & Branch Setup
- Checked git status on `main`, created branch `feat/remote-ip-support`.
- Audited `all_code.md` against live code across macOS Swift (`allcode/LinkToMac-macOS`), Android Kotlin (`allcode/LinkToMac-Android`, `android-app`), C++ Native Client (`native-client`), and Python Orchestrator (`orchestrator`).
- Identified and documented 8 key drift areas in `docs/REMOTE_BUILD_AUDIT.md`:
  1. Multi-package `google-services.json` requirements and Gradle root plugin declaration.
  2. Video channel 20-byte handshake direction (Android transmits resolution & codec parameters to Mac mirror_client).
  3. C++ Native Client OpenSSL 3 linking requirements for TLS 1.3 support.
  4. Dual-port reverse tunneling (`-R0:localhost:27189` and `-R0:localhost:54921`) in `orchestrator/linktomac/backend.py`.
  5. 36-byte token authentication on control channel (port 54921) via `NWConnection` in `ConnectionManager.swift`.
  6. ADB transport isolation for remote connections.
  7. ABR network telemetry handler in `ScreenEncoder.java`.
  8. Version numbering baseline (161 / 1.61 -> 162 / 1.62).
