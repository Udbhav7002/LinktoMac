<p align="center">
  <img src="assets/banner.jpg" alt="LinktoMac Banner" width="100%">
</p>

<h1 align="center">LinktoMac 📱💻</h1>

<p align="center">
  <strong>Ultra-Low Latency, Zero-Config Wireless Screen Mirroring for Android & macOS</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-macOS%20%7C%20Android-blue?style=for-the-badge&logo=apple" alt="Platform">
  <img src="https://img.shields.io/badge/Latency-%3C2ms-success?style=for-the-badge&logo=speedtest" alt="Latency">
  <img src="https://img.shields.io/badge/License-MIT-purple?style=for-the-badge" alt="License">
</p>

<p align="center">
  Mirror your Android screen to macOS with <strong>sub-2ms latency</strong> over Wi-Fi or USB.<br>
  <em>No cloud, no accounts, no complex configuration — it just works.</em>
</p>

---

## ✨ Why LinktoMac?

| Feature | LinktoMac | Typical Alternatives |
| :--- | :--- | :--- |
| ⚡ **Latency** | **1–5 ms** (HEVC + VideoToolbox) | 50–200 ms |
| 🎯 **Setup** | **Zero config** (mDNS + PIN) | Manual IP / QR codes |
| 🔒 **Privacy** | **100% local** (no internet needed) | Often cloud relay |
| 🎮 **Input** | **Touch + Keyboard + Scroll** | View only or laggy |
| 🌐 **Transports** | **Wi-Fi / USB ADB / Pinggy** | Usually one method |
| 📱 **Android 14+** | ✅ Full support | Often broken |

---

## 📥 Get the App

### Option 1: Download Pre-built (Easiest)
| Component | Download Link | Instructions |
| :--- | :--- | :--- |
| 🍏 **macOS App** | [LinktoMac.dmg](https://github.com/Udbhav7002/LinktoMac/releases/latest) | Drag to Applications, double-click to run. |
| 🤖 **Android App** | [app-release.apk](https://github.com/Udbhav7002/LinktoMac/releases/latest) | Install on your phone (Companion UI). |
| 🔌 **Android Server** | [server.jar](https://github.com/Udbhav7002/LinktoMac/releases/latest) | For USB ADB mode (no app install needed). |

> **Note:** If there are no releases available yet, you can build it yourself in under 2 minutes (see Option 2).

### Option 2: Build & Run (2 minutes)

#### 🍏 macOS App (`.app` bundle + `.dmg`)
```bash
# 1. Clone
git clone https://github.com/Udbhav7002/LinktoMac.git
cd LinktoMac

# 2. Build (auto-installs deps, builds native client, packages .app + .dmg)
./build_mac_app.sh

# 3. Run!
open dist/LinktoMac.dmg
```

**Outputs:**
- `dist/LinktoMac.app` — Universal macOS app bundle (arm64 + x86_64)
- `dist/LinktoMac.dmg` — Distributable disk image

#### 🍏 Menu Bar Mode (Optional)
```bash
# Run as menu bar app (no dock icon)
./build_mac_app.sh
# Then run the built app with:
open -a dist/LinktoMac.app --args --menubar
```

#### 🤖 Android Apps
```bash
# Companion App (Wireless UI + mDNS)
cd android-app && ./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk

# ADB Server JAR (for headless USB mode)
cd android-server && ./gradlew jar
# Output: build/libs/server.jar
```

---

## 🚀 Quick Start (3 Minutes)

### Prerequisites
- **macOS** 12+ (Intel or Apple Silicon)
- **Android** 7.0+ (Android 14+ recommended)
- **Same Wi-Fi network** (or a USB cable)

---

### Method 1: Wireless (Recommended) 📶

1. **Open LinktoMac on Mac** → Click **"Start Listening"**.
2. **Open LinktoMac on Android** → Note the **4-digit PIN** displayed on the screen.
3. **Enter the PIN** on your Mac and click **Pair**.
4. **Accept** the Screen Capture permission prompt on your Android device.
5. **Done!** Your phone screen will instantly appear on your Mac.

---

### Method 2: USB ADB (Fastest, No Wi-Fi Needed) 🔌

For the absolute lowest latency (e.g., for gaming), use a direct USB connection.

1. Enable **USB Debugging** on your Android device (Settings → Developer Options).
2. Connect your phone to your Mac via USB.
3. Verify the connection:
   ```bash
   adb devices
   ```
4. Run the CLI orchestrator:
   ```bash
   cd orchestrator
   python3 -m linktomac.cli
   ```
   *The CLI automatically pushes the server to your phone, forwards the ports, and launches the high-performance C++ client.*

---

### Method 3: Remote / Internet (Pinggy Tunnel) 🌐

Want to mirror over the internet across different networks?

1. On the Mac app, click **"Remote Session"**.
2. A secure SSH tunnel via **Pinggy.io** is created automatically (free, no account required).
3. The app generates a public URL (e.g., `tcp://xxx.pinggy.link:port`).
4. Enter this link into the Android companion app to connect securely from anywhere in the world.

---

## 🎮 Controls & Interaction

Your Mac seamlessly controls your Android device.

| Mac Action | Android Result |
| :--- | :--- |
| **Click / Tap** | Single touch/tap |
| **Click + Drag** | Swipe / Drag |
| **Two-finger Scroll** | Vertical / Horizontal Scroll |
| **Typing** | Keyboard input is injected directly |
| **Escape Key** | Android "Back" Button |
| **Cmd + 1 / 2 / 3** | Home / Recents / Back |

---

## 🏗️ Architecture Under the Hood

```mermaid
graph LR
    subgraph Mac ["macOS"]
        A[Python Orchestrator] --> B[C++ mirror_client]
        B --> C[FFmpeg + VideoToolbox]
        C --> D[SDL2 + Metal Renderer]
        A --> E[mDNS / Zeroconf]
        A --> F[PIN Pairing Server]
    end
    
    subgraph Android ["Android"]
        G[Companion App] --> H[MediaProjection]
        G --> I[MediaCodec HEVC Encoder]
        G --> J[InputController]
        K[ADB Server JAR] --> H
        K --> I
        K --> J
    end
    
    B -->|TCP 27189| I
    B -->|TCP 27190| J
    E -.->|UDP 27191| G
    F -->|TCP 27192| G
```

### Data Pipeline
**Video:** Android Screen → MediaProjection → MediaCodec (HEVC) → TCP Socket → FFmpeg Parser → VideoToolbox (HW Decode) → SDL2 Texture → Metal → Mac Screen  
**Input:** Mac SDL Events → Custom Protocol → TCP → Android InputManager.injectInputEvent()

---

## 🔐 Security & Privacy

- **100% Local:** No data leaves your network in default Wireless/USB modes.
- **PIN Pairing:** Prevents unauthorized connections on public Wi-Fi.
- **mDNS Discovery:** Only broadcasts locally.
- **Secure Tunnels:** Remote mode uses encrypted SSH tunnels via Pinggy.

---

## 🛠️ Troubleshooting

### ADB Not Found
**Error:** `adb not found in PATH` or `ADB error: adb not found`
**Fix:** The app bundles ADB automatically. If you see this error:
- Rebuild with `./build_mac_app.sh` (downloads and bundles ADB)
- Or install manually: `brew install android-platform-tools`

### Phone Not Discovered (Wireless)
**Symptom:** "Scanning for devices..." never finds your phone
**Fixes:**
- Ensure both devices are on the **same Wi-Fi network** (same subnet)
- Check firewall: allow incoming UDP 27191 and TCP 27189/27190/27192 on macOS
- Restart the Android companion app
- Try "Discover Devices" in the menu bar app
- Some routers isolate clients (AP Isolation) — disable it in router settings

### USB ADB Not Working
**Symptom:** "No USB device connected" or `adb devices` shows empty
**Fixes:**
- Enable **USB Debugging** in Android Developer Options
- Authorize the computer on the phone's prompt
- Use a data-capable USB cable (not charge-only)
- Try: `adb kill-server && adb start-server`
- On macOS, allow terminal/IDE in **System Settings → Privacy & Security → USB**

### Mirror Window Doesn't Appear / Black Screen
**Symptom:** Mirroring starts but no window or black screen
**Fixes:**
- Ensure `mirror_client` built successfully (check `native-client/build/`)
- Check logs at `~/.linktomax/app.log`
- Verify SDL2 is installed: `brew install sdl2`
- Some Android versions require "Display over other apps" permission for the companion app

### High Latency / Lag
**Fixes:**
- Use **USB ADB mode** for lowest latency (Method 2)
- Ensure 5 GHz Wi-Fi for wireless mode
- Close other bandwidth-heavy apps
- Android 14+ has better MediaCodec performance

### Remote Session (Pinggy) Fails
**Symptom:** "Pinggy failed to establish a tunnel"
**Fixes:**
- Check internet connectivity
- Firewall may block SSH outbound (port 443)
- Try again — Pinggy occasionally has capacity issues
- Free tier has session limits

### Build Fails on macOS
**Common issues:**
| Error | Fix |
|-------|-----|
| `cmake: command not found` | `brew install cmake` |
| `SDL2 not found` | `brew install sdl2` |
| `pyinstaller: command not found` | `pip3 install pyinstaller` |
| Codesign fails | Run `codesign --force --deep --sign - dist/LinktoMac.app` manually |

### Menu Bar App Doesn't Show
- The menu bar app runs without a Dock icon by design
- Look for the 🔗 icon in the top-right menu bar
- If not visible, check **System Settings → Control Center → Other Modules → LinktoMac** → "Show in Menu Bar"

---

## 🤝 Contributing & License

Contributions are what make the open-source community such an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

Distributed under the MIT License. See `LICENSE` for more information.

---

<p align="center">
  <strong>Made with ❤️ for seamless Android ↔ Mac workflows.</strong><br>
  <a href="https://github.com/Udbhav7002/LinktoMac/issues">Report Bug</a> •
  <a href="https://github.com/Udbhav7002/LinktoMac/issues">Request Feature</a>
</p>
