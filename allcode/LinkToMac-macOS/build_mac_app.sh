#!/usr/bin/env bash
set -e

echo "=========================================="
echo " Building LinkToMac for macOS (v1.61)..."
echo "=========================================="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$SCRIPT_DIR"

swift build -c release

APP_DIR="build/LinkToMac.app"
CONTENTS_DIR="$APP_DIR/Contents"
MACOS_DIR="$CONTENTS_DIR/MacOS"
RESOURCES_DIR="$CONTENTS_DIR/Resources"

rm -rf "$APP_DIR"
mkdir -p "$MACOS_DIR" "$RESOURCES_DIR"

# 1. Main Executable & Info.plist
cp ".build/release/LinkToMac" "$MACOS_DIR/LinkToMac"
cp "Info.plist" "$CONTENTS_DIR/Info.plist"
chmod +x "$MACOS_DIR/LinkToMac"

# 2. Bundle App Icon
if [ -f "AppIcon.icns" ]; then
    cp "AppIcon.icns" "$RESOURCES_DIR/AppIcon.icns"
elif [ -f "$REPO_ROOT/assets/AppIcon.icns" ]; then
    cp "$REPO_ROOT/assets/AppIcon.icns" "$RESOURCES_DIR/AppIcon.icns"
fi

# 3. Bundle ADB binary for self-contained execution
if [ -f "$REPO_ROOT/bundled/platform-tools/adb" ]; then
    cp "$REPO_ROOT/bundled/platform-tools/adb" "$MACOS_DIR/adb"
    chmod +x "$MACOS_DIR/adb"
elif command -v adb &>/dev/null; then
    cp "$(command -v adb)" "$MACOS_DIR/adb"
    chmod +x "$MACOS_DIR/adb"
fi

# 4. Ad-hoc sign so macOS Local Network & Notification permissions work smoothly
codesign --force --deep --sign - "$APP_DIR"

# 5. Build Drag-to-Install DMG
echo "💿 Creating Drag-to-Applications DMG installer..."
DMG_PATH="build/LinkToMac.dmg"
DMG_STAGING="build/dmg_staging"
rm -rf "$DMG_STAGING" "$DMG_PATH"
mkdir -p "$DMG_STAGING"

# Copy app and create symlink to /Applications
cp -R "$APP_DIR" "$DMG_STAGING/"
ln -s /Applications "$DMG_STAGING/Applications"

# Set DMG volume icon if possible
hdiutil create -volname "LinkToMac" -srcfolder "$DMG_STAGING" -ov -format UDZO "$DMG_PATH"
rm -rf "$DMG_STAGING"

# Copy DMG to repository root and dist for easy access
mkdir -p "$REPO_ROOT/dist"
cp -f "$DMG_PATH" "$REPO_ROOT/dist/LinkToMac.dmg"
cp -f "$DMG_PATH" "$REPO_ROOT/LinkToMac.dmg"

# 6. Install to /Applications if writable so it's instantly usable
if [ -w "/Applications" ]; then
    echo "📲 Installing LinkToMac.app to /Applications..."
    rm -rf "/Applications/LinkToMac.app"
    cp -R "$APP_DIR" "/Applications/LinkToMac.app"
    # Touch /Applications to inform LaunchServices / Spotlight
    touch "/Applications/LinkToMac.app"
fi

echo "=========================================="
echo " SUCCESS: Built $APP_DIR"
echo " DMG created at: $DMG_PATH (and $REPO_ROOT/LinkToMac.dmg)"
echo " Installed to: /Applications/LinkToMac.app"
echo " Run with: open /Applications/LinkToMac.app"
echo "=========================================="
