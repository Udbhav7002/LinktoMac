#!/bin/bash
set -e

echo "Building LinktoMac.app..."

# 1. Clean previous builds
rm -rf build dist LinktoMac.spec

# 2. Build via PyInstaller
# We need to include the UI folder, the native-client, and the android-server jar
# --windowed creates a Mac .app bundle
# --noconsole removes the terminal window
# Detect SDL2 library used by the native client
SDL2_DYLIB=$(find /opt/homebrew/lib /usr/local/lib -name "libSDL2*.dylib" -maxdepth 1 2>/dev/null | head -1)
if [ -z "$SDL2_DYLIB" ]; then
    echo "Warning: libSDL2 not found, trying libSDL3..."
    SDL2_DYLIB=$(find /opt/homebrew/lib /usr/local/lib -name "libSDL3*.dylib" -maxdepth 1 2>/dev/null | head -1)
fi

PYINSTALLER_ARGS=(
    --name "LinktoMac"
    --windowed
    --noconsole
    --add-data "orchestrator/ui:orchestrator/ui"
    --add-binary "native-client/build/mirror_client:."
    --add-data "android-server/server.jar:android-server"
    --paths "orchestrator"
)

if [ -n "$SDL2_DYLIB" ]; then
    PYINSTALLER_ARGS+=(--add-binary "$SDL2_DYLIB:.")
fi

pyinstaller "${PYINSTALLER_ARGS[@]}" orchestrator/ui_app.py

if [ -d "dist/LinktoMac.app" ]; then
    echo "Ensuring SDL library is properly linked in the bundle..."
    # No symlink needed — PyInstaller handles @rpath
    
    echo "Re-signing the app bundle..."
    codesign --force --deep --sign - dist/LinktoMac.app
    
    echo "Done! The app is available at dist/LinktoMac.app"
fi
