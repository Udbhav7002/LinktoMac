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

1. **On Android**: Launch **LinkToMac**. Follow prompt to enable the **LinkToMac Accessibility Service** (needed for remote touch injection).
2. **On Mac**: Launch **LinkToMac**. Your phone will appear automatically via USB cable or over local Wi-Fi.
3. Enjoy sub-2ms hardware-accelerated screen mirroring and universal clipboard!
