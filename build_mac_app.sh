#!/bin/bash
set -e

echo "🔗 LinktoMac - Production Build Script"
echo "======================================"

# 1. Check Dependencies
check_dep() { command -v $1 &>/dev/null || { echo "❌ Missing: $1. Install with: $2"; exit 1; }; }
check_dep cmake "brew install cmake"
check_dep ffmpeg "brew install ffmpeg"
check_dep sdl2-config "brew install sdl2"  
check_dep python3 "brew install python3"
check_dep hdiutil "built-in to macOS"

# 2. Download ADB if not bundled
if [ ! -f "bundled/platform-tools/adb" ]; then
    if command -v adb &>/dev/null; then
        echo "📦 Using system adb for bundled/platform-tools/adb..."
        mkdir -p bundled/platform-tools
        cp "$(command -v adb)" bundled/platform-tools/adb
        chmod +x bundled/platform-tools/adb
    else
        echo "📥 Downloading ADB..."
        bash scripts/download_adb.sh
    fi
fi

# 3. Build C++ native client
echo "🔨 Building C++ native client..."
cd native-client
rm -rf build
cmake -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --parallel
cd ..

# 4. Ensure Python Dependencies
echo "🐍 Verifying Python dependencies..."
# pip3 install --quiet --break-system-packages pywebview pyinstaller zeroconf cryptography pyobjc-framework-Cocoa rumps || true

# 5. Clean old build
echo "🧹 Cleaning previous builds..."
rm -rf build/ dist/ LinktoMac.spec

# Dynamically resolve SDL2 dynamic library across Apple Silicon (/opt/homebrew) and Intel (/usr/local)
echo "🔍 Resolving SDL2 dynamic library..."
SDL2_LIB=""
if command -v sdl2-config &>/dev/null; then
    SDL2_PREFIX="$(sdl2-config --prefix)"
    for cand in "$SDL2_PREFIX/lib/libSDL2-2.0.0.dylib" "$SDL2_PREFIX/lib/libSDL2.dylib" "$SDL2_PREFIX/opt/sdl2-compat/lib/libSDL2-2.0.0.dylib" "$SDL2_PREFIX/opt/sdl2/lib/libSDL2-2.0.0.dylib"; do
        if [ -f "$cand" ]; then SDL2_LIB="$cand"; break; fi
    done
fi
if [ -z "$SDL2_LIB" ]; then
    for prefix in "/opt/homebrew" "/usr/local"; do
        for cand in "$prefix/lib/libSDL2-2.0.0.dylib" "$prefix/lib/libSDL2.dylib" "$prefix/opt/sdl2/lib/libSDL2-2.0.0.dylib" "$prefix/opt/sdl2/lib/libSDL2.dylib" "$prefix/opt/sdl2-compat/lib/libSDL2-2.0.0.dylib" "$prefix/opt/sdl2-compat/lib/libSDL2.dylib"; do
            if [ -f "$cand" ]; then SDL2_LIB="$cand"; break 2; fi
        done
    done
fi
if [ -z "$SDL2_LIB" ]; then
    echo "❌ Missing SDL2 library. Install with: brew install sdl2"
    exit 1
fi
echo "Found SDL2 library: $SDL2_LIB"

# 6. Run PyInstaller
echo "📦 Packaging with PyInstaller..."
# Create a PyInstaller spec and build
pyinstaller --noconfirm \
    --name "LinktoMac" \
    --windowed \
    --add-binary "native-client/build/mirror_client:." \
    --add-binary "bundled/platform-tools/adb:platform-tools" \
    --add-binary "$SDL2_LIB:." \
    --add-data "orchestrator/ui:orchestrator/ui" \
    --hidden-import "zeroconf" \
    --hidden-import "webview" \
    --hidden-import "cryptography" \
    --hidden-import "rumps" \
    orchestrator/ui_app.py

# Guarantee mirror_client and SDL2 are in Contents/Frameworks and Contents/MacOS
echo "📦 Ensuring mirror_client and SDL2 runtime binaries in app bundle..."
mkdir -p dist/LinktoMac.app/Contents/Frameworks
mkdir -p dist/LinktoMac.app/Contents/MacOS
cp -f native-client/build/mirror_client dist/LinktoMac.app/Contents/MacOS/
chmod +x dist/LinktoMac.app/Contents/MacOS/mirror_client
cp -f native-client/build/mirror_client dist/LinktoMac.app/Contents/Frameworks/
chmod +x dist/LinktoMac.app/Contents/Frameworks/mirror_client
cp -f "$SDL2_LIB" dist/LinktoMac.app/Contents/Frameworks/
cp -f "$SDL2_LIB" dist/LinktoMac.app/Contents/MacOS/

echo "📝 Signing the .app bundle (Ad-Hoc to prevent Gatekeeper errors locally)..."
codesign --deep --force --sign - dist/LinktoMac.app

# 7. Create DMG
echo "💿 Creating DMG..."
# Remove old dmg if exists
rm -f dist/LinktoMac.dmg
# Create DMG from the dist/LinktoMac.app
hdiutil create -volname "LinktoMac" -srcfolder "dist/LinktoMac.app" -ov -format UDZO "dist/LinktoMac.dmg"

echo "✅ Build complete! Production output: dist/LinktoMac.dmg"
