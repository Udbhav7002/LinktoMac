#!/usr/bin/env bash
set -e

# ==============================================================================
# LinkToMac — Universal Production Release Builder (macOS & Android)
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RELEASE_DIR="$REPO_ROOT/release"

echo "=========================================================="
echo " Packaging Official LinkToMac Release (macOS + Android)   "
echo "=========================================================="

mkdir -p "$RELEASE_DIR"
rm -f "$RELEASE_DIR"/*

# ------------------------------------------------------------------------------
# 1. Build Native macOS App & DMG
# ------------------------------------------------------------------------------
echo "🍏 [1/4] Building macOS Native Application (LinkToMac.app)..."
cd "$REPO_ROOT/allcode/LinkToMac-macOS"
bash build_mac_app.sh

APP_BUNDLE="$REPO_ROOT/allcode/LinkToMac-macOS/build/LinkToMac.app"
DMG_SRC="$REPO_ROOT/allcode/LinkToMac-macOS/build/LinkToMac.dmg"

# Copy DMG to release folder
cp -f "$DMG_SRC" "$RELEASE_DIR/LinkToMac.dmg"

# Create clean ZIP archive preserving code-signing and extended attributes
echo "📦 [2/4] Packaging macOS ZIP Archive..."
ditto -c -k --keepParent "$APP_BUNDLE" "$RELEASE_DIR/LinkToMac-macOS.zip"

# ------------------------------------------------------------------------------
# 2. Build Android Companion APK
# ------------------------------------------------------------------------------
echo "🤖 [3/4] Building Android Companion Application (LinkToMac.apk)..."
cd "$REPO_ROOT/allcode/LinkToMac-Android"
./gradlew assembleDebug --no-daemon

APK_SRC="$REPO_ROOT/allcode/LinkToMac-Android/app/build/outputs/apk/debug/app-debug.apk"
cp -f "$APK_SRC" "$RELEASE_DIR/LinkToMac.apk"

# Sync web portal distribution files
mkdir -p "$REPO_ROOT/public/downloads"
cp -f "$RELEASE_DIR/LinkToMac.apk" "$REPO_ROOT/public/downloads/LinkToMac.apk"
cp -f "$RELEASE_DIR/LinkToMac.dmg" "$REPO_ROOT/public/downloads/LinkToMac.dmg"

# ------------------------------------------------------------------------------
# 3. Cryptographic Checksums
# ------------------------------------------------------------------------------
echo "🔒 [4/4] Generating SHA-256 Checksums..."
cd "$RELEASE_DIR"
shasum -a 256 LinkToMac.dmg LinkToMac-macOS.zip LinkToMac.apk > SHA256SUMS.txt

# ------------------------------------------------------------------------------
# 4. Write Installation Documentation
# ------------------------------------------------------------------------------
cat << 'EOF' > "$RELEASE_DIR/INSTALL.md"
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
EOF

echo ""
echo "=========================================================="
echo " ✅ RELEASE BUILD COMPLETE!"
echo " Output files in $RELEASE_DIR:"
ls -lh "$RELEASE_DIR"
echo "=========================================================="
