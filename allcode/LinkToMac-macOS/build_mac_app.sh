#!/usr/bin/env bash
set -e

echo "=========================================="
echo " Building LinkToMac for macOS (v1.61)..."
echo "=========================================="

swift build -c release

APP_DIR="build/LinkToMac.app"
CONTENTS_DIR="$APP_DIR/Contents"
MACOS_DIR="$CONTENTS_DIR/MacOS"
RESOURCES_DIR="$CONTENTS_DIR/Resources"

rm -rf "$APP_DIR"
mkdir -p "$MACOS_DIR" "$RESOURCES_DIR"

cp ".build/release/LinkToMac" "$MACOS_DIR/LinkToMac"
cp "Info.plist" "$CONTENTS_DIR/Info.plist"
chmod +x "$MACOS_DIR/LinkToMac"

# Ad-hoc sign so macOS Local Network & Notification permissions work smoothly
codesign --force --deep --sign - "$APP_DIR"

echo "=========================================="
echo " SUCCESS: Built $APP_DIR"
echo " Run with: open $APP_DIR"
echo "=========================================="
