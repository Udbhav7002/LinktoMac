#!/bin/bash
set -e
echo "📥 Downloading official macOS ADB from Google..."
curl -L -o /tmp/platform-tools.zip "https://dl.google.com/android/repository/platform-tools-latest-darwin.zip"
echo "📦 Extracting ADB..."
unzip -q -o /tmp/platform-tools.zip -d bundled/
chmod +x bundled/platform-tools/adb
rm /tmp/platform-tools.zip
echo "✅ ADB bundled successfully at bundled/platform-tools/adb"
