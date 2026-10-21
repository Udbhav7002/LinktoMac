# LinkToMac — Installation Guide

## 🍏 macOS Installation

### Option A: Drag-and-Drop Disk Image (Recommended)
1. Double-click `LinkToMac.dmg`.
2. Drag `LinkToMac.app` into the **Applications** folder shortcut.
3. Open `LinkToMac` from **Applications**, **Launchpad**, or **Spotlight** (`Cmd + Space`).
4. *(First Launch)* If macOS displays an unnotarized developer alert, open **System Settings > Privacy & Security** and click **Open Anyway**, or run in Terminal:
   ```bash
   xattr -cr /Applications/LinkToMac.app
   ```

### Option B: ZIP Archive
1. Unzip `LinkToMac-macOS.zip`.
2. Move `LinkToMac.app` into `/Applications`.

---

## 🤖 Android Installation

### Option A: Direct APK Install
1. Transfer `LinkToMac.apk` to your Android device (or download directly from the web portal).
2. Tap the downloaded file and select **Install**.
3. If prompted, allow installation from this source.

### Option B: USB ADB One-Click Install
If your phone is plugged in with USB debugging enabled:
```bash
adb install -r LinkToMac.apk
```

---

## ⚡ First Time Setup

1. **On Android**: Launch **LinkToMac**. Follow the prompt to enable the **LinkToMac Accessibility Service** (needed for remote touch injection).
2. **On Mac**: Launch **LinkToMac**. Your phone will appear automatically via USB cable or over local Wi-Fi.
3. Enjoy sub-2ms hardware-accelerated screen mirroring and universal clipboard!

---

## 🌐 Remote Mode (Cross-Network / Cellular / WAN)

LinkToMac v1.62 introduces frictionless cross-network remote mirroring and control. Whether your Mac and Android device are on different Wi-Fi subnets, cellular 4G/5G, or separated across cities, LinkToMac connects without requiring a static public IP or port-forwarding.

### 1. One-Time Pairing Flow
- **Google Account Sign-In (Recommended)**: Sign in with the same Google Account on both the Mac app and the Android app. Sessions automatically sync via Firestore.
- **QR Code Pairing**: On the Mac app, click **Remote Pair** to display a dynamic QR code. In the Android app, tap **Scan QR** to instantly link your devices.

### 2. How the Dual-Port Relay Operates
When remote mode is initiated:
1. The Mac companion opens a secure dual-port reverse tunnel via Pinggy:
   - **Video Channel**: Port `27189` (H.264 / H.265 video stream + input feedback)
   - **Control Channel**: Port `54921` (JSON LinkEnvelope protocol for notifications, SMS, clipboard, files, calls)
2. The relay endpoints (`tcp://a.pinggy.io:...`) and a cryptographically generated 36-byte session token (144-bit entropy) are published to the user's private Firestore document (`users/{uid}/sessions/current`).
3. The Android companion receives the real-time snapshot and dials both endpoints over TLS 1.3.

### 3. Security & Wire Encryption
- **TLS 1.3 Wire Encryption**: Strict TLS 1.3 transport encryption is enforced on both streams (`TLS_AES_256_GCM_SHA384`). Plaintext remote traffic is strictly blocked.
- **Mutual Secret Token Handshake**: Both channels require a constant-time 36-byte token verification before accepting frames. Unauthorized probes are disconnected in <1ms.
- **ADB Isolation**: Low-level hardware ADB commands are strictly restricted to local USB connections. Remote input gestures route safely through the Android Accessibility Service.

### 4. Firewall & Network Port Requirements
LinkToMac Remote Mode does **NOT** require configuring router NAT port-forwarding or opening incoming ports on your firewall. Only outbound connections are required:
- **Port 443 / TCP (SSH)**: Outbound to `tcp@a.pinggy.io` (for the reverse proxy relay tunnel).
- **Port 443 / TCP (HTTPS & WSS)**: Outbound to Google Firebase / Firestore (for session negotiation and discovery).
- **Port 1024–65535 / TCP (TLS 1.3)**: Outbound from the Android client to the dynamically assigned Pinggy relay ports.
