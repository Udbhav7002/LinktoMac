# LinkToMac — Zero-Ambiguity Agent Playbook & Stitching Guide (`allcode/`)

> **INSTRUCTIONS FOR AI AGENTS:**
> Do **NOT** invent your own protocol, do **NOT** rename JSON keys, and do **NOT** change port numbers.
> Everything you need to build **LinkToMac** for **macOS** and **Android** is already written for you in `ALLCODE_MACOS_COMPLETE.md` and `ALLCODE_ANDROID_COMPLETE.md`. Follow the exact steps below to extract the files into the `allcode/` directory and compile both apps.

---

## 1. Exact Directory Structure (`allcode/`)

Your agents must create the following exact folder hierarchy inside `allcode/`:

```text
allcode/
├── stitch_allcode.py                           # Automated extractor script (below)
├── LinkToMac-macOS/
│   ├── Package.swift                           # Swift Package Manager manifest (no .xcodeproj hassle!)
│   ├── Info.plist                              # macOS App Bundle metadata & Local Network permissions
│   ├── build_mac_app.sh                        # 1-click script that builds LinkToMac.app
│   └── Sources/
│       └── LinkToMac/
│           ├── LinkToMacApp.swift              # Main @main entry point, WindowGroup, MenuBarExtra & Call HUD
│           ├── ProtocolModels.swift            # Shared wire protocol models & frame encoder/decoder
│           ├── ConnectionManager.swift         # TCP Server (port 54921), Bonjour (_linktomac._tcp), File/Media receiver
│           ├── ADBBridge.swift                 # USB detection, auto `adb reverse`, scrcpy & screen-off controller
│           ├── ClipboardSyncManager.swift      # NSPasteboard monitor, password filter, URL Handoff
│           ├── MainDashboardView.swift         # Full Mac Command Center UI (All 7 tabs + QR Pairing + Menu Bar)
│           └── FeatureViews.swift              # Messages, Notifications, Files, Photos, Mirroring, Camera & Call HUD views
│
└── LinkToMac-Android/
    ├── settings.gradle.kts
    ├── build.gradle.kts
    └── app/
        ├── build.gradle.kts
        └── src/main/
            ├── AndroidManifest.xml             # All permissions & service declarations
            └── java/com/linktomac/android/
                ├── ProtocolModels.kt           # Exact matching wire protocol & binary framer
                ├── LinkConnectionService.kt    # Foreground socket client, mDNS discovery, USB 127.0.0.1 fallback, Heartbeat
                ├── LinkNotificationService.kt  # NotificationListenerService + RemoteInput inline reply execution
                ├── SmsContactsCallBridge.kt    # SMS read/send, Contacts sync, Telecom Call Answer/Decline/End, Media Remote
                ├── FilePhotosBridge.kt         # Storage directory browser, MediaStore thumbnails, 64KB chunked transfers
                ├── ScreenCameraStreamer.kt     # MediaProjection screen mirror, Camera2 webcam stream & Mic audio stream
                └── MainActivity.kt             # Material 3 UI, Permission checklist wizard, QR/IP Pairing & Quick Controls
```

---

## 2. The Shared Wire Protocol Specification (Locked Contract)

Both the macOS Swift code and the Android Kotlin code communicate over a persistent TCP socket on port **`54921`** (either over Local Wi-Fi IP discovered via mDNS `_linktomac._tcp.` or over USB via `adb reverse tcp:54921 tcp:54921` where Android connects to `127.0.0.1:54921`).

### Binary Frame Header ($5\text{ bytes}$)
Every packet sent in either direction has an identical $5\text{-byte}$ header:
$$
\underbrace{\text{Payload Length (UInt32 Big-Endian)}}_{4\text{ bytes}} + \underbrace{\text{Frame Type (UInt8)}}_{1\text{ byte}} + \underbrace{\text{Payload Bytes}}_{N\text{ bytes}}
$$

* **`0x01` (`FRAME_JSON`)**: UTF-8 JSON command or event message (`LinkEnvelope`).
* **`0x02` (`FRAME_FILE_CHUNK`)**: Raw binary file chunk: first $36\text{ bytes}$ = ASCII `transferId` (padded/UUID), remaining bytes = raw file chunk data.
* **`0x03` (`FRAME_VIDEO_JPEG`)**: Live Mirroring or Virtual Webcam compressed frame (JPEG/H.264 NAL unit).
* **`0x04` (`FRAME_AUDIO_PCM`)**: Live Microphone or Cast 16-bit PCM audio frame ($16000\text{ Hz}$, mono).

### Supported JSON `type` Values in `0x01` (`FRAME_JSON`)

| `type` String | Direction | Purpose |
| :--- | :--- | :--- |
| `"HELLO"` | Both | Initial pairing handshake (`deviceId`, `deviceName`, `pairToken`, `battery`, `transport`) |
| `"STATUS_UPDATE"` | Android $\to$ Mac | Battery %, charging state, transport type (`USB` / `Wi-Fi`), active media track info |
| `"NOTIFICATION_POSTED"` | Android $\to$ Mac | Real-time Android notification (`id`, `packageName`, `appName`, `title`, `body`, `canReply`) |
| `"NOTIFICATION_REMOVED"` | Both | Dismisses notification on Mac or Android (`id`) |
| `"NOTIFICATION_REPLY"` | Mac $\to$ Android | Triggers Android `RemoteInput` inline reply (`id`, `replyText`) |
| `"SMS_THREADS_SYNC"` | Android $\to$ Mac | Sends recent SMS conversations and Contacts directory |
| `"SMS_SEND"` | Mac $\to$ Android | Sends an SMS message from the Android phone (`address`, `body`) |
| `"CALL_STATE"` | Android $\to$ Mac | Incoming/Active/Ended cellular call (`state`, `number`, `contactName`) |
| `"CALL_ACTION"` | Mac $\to$ Android | `ANSWER`, `DECLINE`, `END`, `MUTE`, or `DECLINE_WITH_SMS` |
| `"CLIPBOARD_SYNC"` | Both | Bi-directional clipboard text/URL sync (`content`, `isUrl`, `hash`) |
| `"URL_HANDOFF"` | Both | Immediately opens a URL in the target device's default browser |
| `"FILE_LIST_REQ"` / `"FILE_LIST_RES"` | Both | Browses Android `/sdcard` folders and `MediaStore` photos with thumbnails |
| `"FILE_TRANSFER_START"` | Both | Announces incoming file (`transferId`, `fileName`, `totalBytes`, `destPath`) |
| `"FILE_TRANSFER_END"` | Both | Completes file write and verifies size |
| `"FILE_PULL_REQ"` | Mac $\to$ Android | Requests Android to stream a specific file or photo to the Mac |
| `"MEDIA_ACTION"` | Mac $\to$ Android | Controls Android media (`PLAY_PAUSE`, `NEXT`, `PREV`, `VOL_UP`, `VOL_DOWN`, `RING_PHONE`) |
| `"STREAM_CONTROL"` | Mac $\to$ Android | Starts/stops `MIRROR`, `WEBCAM_BACK`, `WEBCAM_FRONT`, `MIC_STREAM`, or `REMOTE_TAP` |

---

## 3. Automated Extractor Script (`stitch_allcode.py`)

Give your agents this Python script. When placed in the same directory as `ALLCODE_MACOS_COMPLETE.md` and `ALLCODE_ANDROID_COMPLETE.md`, it automatically parses every `### FILE: <path>` section and writes the exact files to disk!

```python
#!/usr/bin/env python3
"""
stitch_allcode.py
Automatically extracts all source files from ALLCODE_MACOS_COMPLETE.md and
ALLCODE_ANDROID_COMPLETE.md into the ./allcode/ directory.
"""
import os
import re
import stat

MARKDOWN_FILES = [
    "ALLCODE_MACOS_COMPLETE.md",
    "ALLCODE_ANDROID_COMPLETE.md",
]

FILE_PATTERN = re.compile(
    r"### FILE:\s*([^\n\r]+)\r?\n```[a-zA-Z0-9_-]*\r?\n(.*?)\r?\n```",
    re.DOTALL
)

def extract_from_markdown(md_path: str):
    if not os.path.exists(md_path):
        print(f"[WARN] {md_path} not found in current directory.")
        return 0
    with open(md_path, "r", encoding="utf-8") as f:
        content = f.read()

    matches = FILE_PATTERN.findall(content)
    count = 0
    for rel_path, code in matches:
        rel_path = rel_path.strip()
        full_path = os.path.join(".", rel_path)
        os.makedirs(os.path.dirname(full_path), exist_ok=True)
        with open(full_path, "w", encoding="utf-8") as out:
            out.write(code + "\n")
        if full_path.endswith(".sh"):
            st = os.stat(full_path)
            os.chmod(full_path, st.st_mode | stat.S_IEXEC)
        print(f"[OK] Created: {full_path}")
        count += 1
    return count

def main():
    total = 0
    for md in MARKDOWN_FILES:
        total += extract_from_markdown(md)
    print(f"\nDone! Extracted {total} files into ./allcode/")
    print("Next steps:")
    print("  1. Build & Run macOS App: cd allcode/LinkToMac-macOS && ./build_mac_app.sh")
    print("  2. Build Android App:     Open allcode/LinkToMac-Android in Android Studio and click Run")

if __name__ == "__main__":
    main()
```

---

## 4. Step-by-Step Instructions for Your Agents to Stitch & Run

### Step 1: Unpack All Code Files
Run in terminal:
```bash
python3 stitch_allcode.py
```

### Step 2: Build and Launch the macOS Application (`LinkToMac.app`)
Your agents do **not** even need to create an Xcode `.xcodeproj` manually! We use Swift Package Manager plus an automated bundle script:
```bash
cd allcode/LinkToMac-macOS
chmod +x build_mac_app.sh
./build_mac_app.sh
open build/LinkToMac.app
```
What `build_mac_app.sh` does:
1. Runs `swift build -c release` to compile the native macOS binary.
2. Creates the `build/LinkToMac.app/Contents/MacOS` and `Resources` bundle folders.
3. Copies the compiled binary and `Info.plist` (which includes `NSLocalNetworkUsageDescription` and `NSBonjourServices` for `_linktomac._tcp`).
4. Ad-hoc codesigns `build/LinkToMac.app` with `codesign --force --deep --sign - build/LinkToMac.app` so macOS allows local network & notification permissions immediately.

### Step 3: Build and Install the Android Companion App (`LinkToMac-Android`)
1. Open Android Studio $\to$ **Open** $\to$ select `allcode/LinkToMac-Android`.
2. Let Gradle sync (it uses standard AndroidX, Jetpack Compose, and CameraX/ZXing QR scanning—zero exotic repositories).
3. Plug in your Android phone via USB or use Wireless Debugging and click **Run (`Shift+F10`)** (or run `./gradlew assembleDebug` from `allcode/LinkToMac-Android`).

### Step 4: How to Pair & Test Every Feature in 60 Seconds
1. **Launch `LinkToMac.app` on your Mac**:
   - The app opens its dark-mode Command Center AND puts a LinkToMac icon in the macOS Menu Bar.
   - Click **"Pair Device (QR)"** in the sidebar or top bar. You will see your Mac's Local Wi-Fi IP, Port `54921`, 6-digit Pairing Token, and a live **QR Code**.
2. **Launch `LinkToMac` on your Android Phone**:
   - Tap the **Permission Setup Cards** at the top of the Android app:
     1. *Grant Notification Access* (opens Android Notification Listener settings—toggle LinkToMac ON).
     2. *Grant SMS, Contacts, Call & Storage Permissions* (taps system runtime dialog).
     3. *Disable Battery Optimization* (1-tap system prompt so Wi-Fi stays alive in your pocket).
   - **To connect over USB (Fastest)**: Simply plug in your USB cable with USB Debugging enabled! The Mac app's `ADBBridge` automatically detects your phone, sets up `adb reverse tcp:54921 tcp:54921`, and tap **"Connect via USB (ADB)"** on Android (connects to `127.0.0.1:54921`).
   - **To connect over Local Wi-Fi**: Tap **"Scan QR Code"** on Android (or tap the auto-discovered Mac name from mDNS `_linktomac._tcp`)!
3. **Verify All 6 Core Workflows**:
   - **01 Messages & Notifications**: Send your phone a WhatsApp/Telegram message or SMS—watch it appear on your Mac instantly, type a reply in the inline text box, and press `Enter ↵`.
   - **02 Photos & Files**: Click **Photos** or **Files** on Mac $\to$ browse your Android phone's real folders and camera roll thumbnails $\to$ click **Download to Mac** or drag files from Mac Finder into the window to send them to `/sdcard/Download/LinkToMac/`.
   - **03 Clipboard & URL Handoff**: Copy any text or URL on Mac (`Cmd+C`)—it immediately syncs to your Android clipboard (and vice versa).
   - **04 Screen Mirroring & App Control**: Click **Mirror & Control** $\to$ choose either **Wireless Live Stream** (built-in MediaProjection + remote tap/swipe controls) or **High-Speed USB Mirror (`scrcpy`)** with **Screen-Off Mode**.
   - **05 Call Controls**: Call your Android phone $\to$ a floating **Incoming Call HUD** pops up on your Mac with **Answer**, **Decline**, **Decline with SMS**, and **End Call** while cellular audio stays on your phone.
   - **06 Webcam, Mic & Media Remote**: Click **Camera & Media** on Mac $\to$ stream your Android Rear/Front camera and microphone live to the Mac, or control Spotify/YouTube Music playback and ring your lost phone.