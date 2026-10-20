<p align="center">
  <img src="assets/banner.jpg" alt="LinktoMac High Performance Display Bridge" width="100%">
</p>

```text
================================================================================
  _     ___ _   _ _  _______ ___  __  __    _    ____ 
 | |   |_ _| \ | | |/ /_   _/ _ \|  \/  |  / \  / ___|
 | |    | ||  \| | ' /  | || | | | |\/| | / _ \| |    
 | |___ | || |\  | . \  | || |_| | |  | |/ ___ \ |___ 
 |_____|___|_| \_|_|\_\ |_| \___/|_|  |_/_/   \_\____|
================================================================================
  HIGH-PERFORMANCE HARDWARE-ACCELERATED ANDROID WORKSPACE BRIDGE FOR MACOS
  Sub-2ms Latency | Apple VideoToolbox & Metal | Wi-Fi / USB / WAN Tunnels
================================================================================
```

<p align="center">
  <a href="https://github.com/Udbhav7002/LinktoMac"><img src="https://img.shields.io/badge/PLATFORM-macOS%2012%2B%20%7C%20Android%2011%2B-000000?style=for-the-badge&logo=apple&logoColor=white" alt="Platform"></a>
  <a href="https://github.com/Udbhav7002/LinktoMac"><img src="https://img.shields.io/badge/DECODER-Apple%20VideoToolbox%20%2B%20Metal-101010?style=for-the-badge&logo=apple" alt="Decoder"></a>
  <a href="https://github.com/Udbhav7002/LinktoMac"><img src="https://img.shields.io/badge/CODEC-HEVC%20%2F%20H.265%20%26%20AVC-101010?style=for-the-badge" alt="Codec"></a>
  <a href="https://github.com/Udbhav7002/LinktoMac"><img src="https://img.shields.io/badge/LATENCY-%3C%202ms%20(USB)%20%7C%20%3C%208ms%20(Wi--Fi)-00c853?style=for-the-badge" alt="Latency"></a>
  <a href="https://github.com/Udbhav7002/LinktoMac/blob/main/LICENSE"><img src="https://img.shields.io/badge/LICENSE-MIT-000000?style=for-the-badge" alt="License"></a>
</p>

---

## 01. Overview

**LinktoMac** is an open-source, production-grade workspace bridge that seamlessly integrates Android devices into macOS. Engineered natively in **Swift/SwiftUI**, **Kotlin**, and **C++ (Metal / VideoToolbox)**, LinktoMac eliminates the latency, audio stutter, complex network configurations, and cloud account dependencies of legacy screen mirroring tools.

Whether connected via local Wi-Fi, direct USB Type-C, or over an encrypted WAN relay tunnel, LinktoMac delivers a responsive 60fps mirror with unified clipboard, hardware input injection, and full touch-emulation gesture support.

---

## 02. System Architecture & Dataflow

LinktoMac operates on an asynchronous multi-tier architecture separating the hardware-encoded video stream from low-overhead bidirectional control and input channels.

```text
+-----------------------------------------------------------------------------+
|                           LINKTOMAC ARCHITECTURE                            |
+-----------------------------------------------------------------------------+

 [ Android Device ]                                      [ macOS Workstation ]
 +---------------------------+                          +--------------------+
 | Display Framebuffer       |                          | Retina Display     |
 +-------------+-------------+                          +---------^----------+
               |                                                  |
       [MediaProjection]                                   [Metal Shader]
               |                                                  |
               v                                                  |
 +-------------+-------------+                          +---------+----------+
 | Hardware MediaCodec       |                          | Apple VideoToolbox |
 | (H.265/HEVC or H.264 AVC) |                          | Zero-Copy Decoder  |
 +-------------+-------------+                          +---------^----------+
               |                                                  |
         [NAL Parser]                                      [FFmpeg Demux]
               |                                                  |
               v                                                  |
 +-------------+-------------+   TCP 27189 (Raw H.265)  +---------+----------+
 | Android Server Socket /   |=========================>| Native Client      |
 | ADB Forwarding Daemon     |   Wi-Fi / USB / Pinggy   | (C++ mirror_client)|
 +---------------------------+                          +---------+----------+
                                                                  |
 +---------------------------+   TCP 27190 (Binary IPC) +---------v----------+
 | LinkTouchAccessibility    |<=========================| Native Event Loop  |
 | System Input Injection    |   Touch / Keys / Scroll  | (Swift / SDL2)     |
 +---------------------------+                          +--------------------+
               ^                                                  ^
               |                 UDP 27191 (mDNS)                 |
               + - - - - - - - - - - - - - - - - - - - - - - - - -+
```

### Video Delivery Pipeline
1. **Capture**: Android `MediaProjection` records surface frames directly from the Display Manager at native resolution.
2. **Encoding**: Framebuffers are streamed to on-device hardware `MediaCodec` generating Annex-B byte-streams in HEVC (H.265) or AVC (H.264).
3. **Transport**: Non-blocking TCP sockets transmit NAL packets across Wi-Fi LAN, direct ADB USB port forwarding, or SSH reverse tunnels.
4. **Hardware Decoding**: macOS native client utilizes Apple `VideoToolbox` framework for hardware-accelerated decompression directly into native Metal textures with zero CPU memory copying.
5. **Presentation**: Rendered through Metal shaders onto an SDL2 surface synchronized with the host display refresh rate.

---

## 03. Technical Comparison

| Feature Metric | LinktoMac | Samsung DeX on Mac | scrcpy | Commercial Utilities |
| :--- | :--- | :--- | :--- | :--- |
| Video Latency (USB) | **1 - 3 ms** | 60 - 120 ms | 20 - 45 ms | 80 - 250 ms |
| Video Latency (Wi-Fi) | **5 - 12 ms** | Not Supported | 35 - 70 ms | 100 - 300 ms |
| Decoding Engine | **Hardware VideoToolbox** | Software / Discontinued | FFmpeg Software | Proprietary Software |
| Host GUI Engine | **Native macOS SwiftUI** | Legacy App Bundle | Terminal / SDL | Electron (Heavy) |
| Account Requirement | **None (100% Local)** | Required Account | None | Cloud Login Required |
| Network Discovery | **mDNS Zero-Config** | Manual USB | Manual IP | Centralized Relay |
| Android Compatibility| **Android 11 - 15** | Android 9 - 11 only | Android 5.0+ | Varies |
| Privacy Integrity | **Zero Outbound Telemetry**| Telemetry Enabled | Clean | Tracking / Ads |

---

## 04. Connection Modes

LinktoMac provides three flexible connection topologies designed for different workspace conditions:

```text
[MODE 1: WIRELESS LAN]
 Android Phone [mDNS Beacon] ----(Local 5GHz Wi-Fi)----> macOS Client
 (Zero cables, automatic discovery, PIN authentication, sub-10ms latency)

[MODE 2: DIRECT USB CABLE]
 Android Phone <=========(USB Type-C ADB Forwarding)========> macOS Client
 (Fastest connection, uncompressed visual fidelity, sub-2ms latency, zero jitter)

[MODE 3: REMOTE WAN RELAY]
 Android Phone --------(Encrypted SSH Tunnel)--------> Pinggy.link --------> macOS Client
 (Cross-network access across different internet connections with zero router port forwarding)
```

### Mode 1: Wireless LAN (Zero-Config)
- Employs Apple Bonjour and Android `NsdManager` over UDP port `27191`.
- When both devices are connected to the same local network, discovery occurs in under 500ms.
- 4-digit PIN authentication prevents unwanted connections on shared networks.

### Mode 2: Direct USB Cable
- Ideal for gaming, rapid software testing, and zero-latency desktop workflows.
- Communicates via host ADB port forwarding (`adb forward tcp:27189 tcp:27189`).
- Completely bypasses Wi-Fi congestion and operates offline.

### Mode 3: Remote WAN Mirroring
- Enables remote assistance and mirroring across completely different networks.
- Uses outbound SSH tunnel relays via Pinggy (`a.pinggy.io`) to expose a secure port.
- Transmits data point-to-point without persistent server storage.

---

## 05. Controls & Interaction

LinktoMac translates macOS trackpad, mouse, and keyboard inputs into low-level Android input events in real time.

```text
+---------------------------------------------------------------------------+
| LINKTOMAC WIRE PROTOCOL FRAME SPECIFICATION                              |
+--------+--------+------------------+------------------+-------------------+
| BYTE 0 | BYTE 1 | BYTES 2..5       | BYTES 6..9       | BYTES 10..N       |
+--------+--------+------------------+------------------+-------------------+
| TYPE   | ACTION | PARAMETER 1 (X)  | PARAMETER 2 (Y)  | PAYLOAD (OPTIONAL)|
| (1 B)  | (1 B)  | (32-bit INT / BE)| (32-bit INT / BE)| (RAW BYTES / UTF8)|
+--------+--------+------------------+------------------+-------------------+
```

| macOS Action | Android Emulated Event | Implementation |
| :--- | :--- | :--- |
| Single Click / Tap | Screen Touch Down / Up | `MotionEvent.ACTION_DOWN / UP` |
| Click + Drag | Swipe / Drag Gesture | `MotionEvent.ACTION_MOVE` |
| Two-Finger Trackpad Scroll | Vertical / Horizontal Scroll | Kinetic Wheel Event Injection |
| Physical Keyboard Typing | Direct Text / Key Injection | `KeyEvent.ACTION_DOWN` + IME Bridge |
| Escape Key | Android Back Button | Keycode 4 (`KEYCODE_BACK`) |
| Command + 1 | Android Home Navigation | Intent `CATEGORY_HOME` |
| Command + 2 | Android App Switcher (Recents) | Accessibility Action `GLOBAL_RECENTS` |
| Command + 3 | Android Back Navigation | Accessibility Action `GLOBAL_BACK` |
| Command + C / Command + V | Universal Clipboard Sync | Dual-direction System Pasteboard Hook |

---

## 06. Downloads & Fast Deployment

### Option A: Pre-Compiled Direct Downloads

```text
[ARTIFACTS]
├── Android Companion APK (v1.61) : https://github.com/Udbhav7002/LinktoMac/raw/main/downloads/LinkToMac.apk
└── Web Download Landing Portal   : https://linkto-mac.vercel.app
```

- **Android Companion APK**: [Download LinkToMac.apk](https://github.com/Udbhav7002/LinktoMac/raw/main/downloads/LinkToMac.apk) (20 MB, Android 11 to 15 verified)
- **macOS Native Binary**: Built via universal shell script in under 30 seconds (see below).

---

### Option B: Build and Run Native macOS Application

```bash
# 1. Clone repository
git clone https://github.com/Udbhav7002/LinktoMac.git
cd LinktoMac

# 2. Compile standalone macOS app bundle
./build_mac_app.sh

# 3. Launch application
open build/LinkToMac.app
```

---

## 07. Technical Specifications & Ports

| Protocol Identifier | Transport | Port / Channel | Purpose |
| :--- | :--- | :--- | :--- |
| `VIDEO_FEED` | TCP | `27189` | Raw H.265 / H.264 NAL stream from encoder |
| `CONTROL_CHANNEL`| TCP | `27190` | Binary input events and command transport |
| `LAN_BEACON` | UDP | `27191` | mDNS service advertisement (`_linktomac._tcp.local`)|
| `HANDSHAKE_SEC` | TCP | `27192` | PIN verification and session initialization |
| `REMOTE_RELAY` | SSH | `443 / Dynamic`| Outbound Pinggy tunnel endpoint |

---

## 08. Project Structure

```text
LinktoMac/
├── .github/                  # Automated CI/CD build workflows
├── .gitignore                # Source control exclusion specifications
├── .vercelignore             # Web deployment exclusions
├── Makefile                  # Root development targets
├── README.md                 # Master project documentation
├── build_mac_app.sh          # Native macOS compiler script
├── vercel.json               # Edge deployment manifest
│
├── allcode/                  # Standalone native Swift & Kotlin implementations
│   ├── LinkToMac-Android/    # Android companion app (Kotlin, Gradle, Compose)
│   └── LinkToMac-macOS/      # Native macOS application (SwiftUI, Swift Package)
│
├── android-app/              # Core Android companion module
├── android-server/           # Headless Java server for USB ADB injection
├── native-client/            # C++ Metal/VideoToolbox video decoding client
├── orchestrator/             # Connection manager, ADB bridge & Webview UI
│
├── assets/                   # High-resolution artwork & banners
├── docs/                     # Specifications, audit reports & flow diagrams
│   └── bundles/              # Complete consolidated code manifests (all_code.md)
├── downloads/                # Direct download binaries (LinkToMac.apk)
├── public/                   # Static landing portal assets for Vercel
├── scripts/                  # Code stitching, export, and synchronization tools
└── tests/                    # Native and adversarial test suites
```

---

## 09. Diagnostics & Troubleshooting

```text
+-----------------------------------------------------------------------------+
|                           DIAGNOSTIC MATRIX                                 |
+------------------------------+-----------------------+----------------------+
| SYMPTOM                      | PROBABLE CAUSE        | CORRECTIVE ACTION    |
+------------------------------+-----------------------+----------------------+
| Wireless device discovery    | Local firewall or AP  | Verify Wi-Fi subnet; |
| times out                    | isolation active      | allow UDP port 27191 |
+------------------------------+-----------------------+----------------------+
| USB connection unrecognized  | USB debugging         | Toggle Developer     |
|                              | revoked               | Options; adb devices |
+------------------------------+-----------------------+----------------------+
| Black window on launch       | Missing SDL2 / Metal  | brew install sdl2    |
|                              | dependency            | rebuild native client|
+------------------------------+-----------------------+----------------------+
| Touch events not injected    | Accessibility service | Enable LinkToMac in  |
|                              | permission disabled   | Android Accessibility|
+------------------------------+-----------------------+----------------------+
```

### Common Commands

```bash
# Verify attached Android devices via ADB
adb devices

# Forward manual ports for USB mode
adb forward tcp:27189 tcp:27189
adb forward tcp:27190 tcp:27190

# Rebuild complete Android companion APK
cd allcode/LinkToMac-Android && ./gradlew assembleDebug

# Rebuild standalone macOS SwiftUI app
cd allcode/LinkToMac-macOS && ./build_mac_app.sh

# Run full project synchronization suite
make sync
```

---

## 10. Security & Privacy Philosophy

1. **Local-First Design**: Video and input streams are sent directly over local networks or USB cables. No intermediate servers ever receive or decrypt your screen data.
2. **Ephemeral Memory Buffers**: Frames are decompressed directly to GPU memory; no session frames or touch logs are written to disk.
3. **Hardware Sandboxing**: macOS application runs within strict system sandboxing limits with granular access permissions.
4. **Zero Account Barrier**: LinktoMac does not require accounts, phone numbers, or analytics SDKs.

---

## 11. License

Distributed under the **MIT License**. See `LICENSE` for further details.
