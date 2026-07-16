#!/bin/bash
set -e

# Configuration - adjust these paths if needed
ANDROID_HOME=${ANDROID_HOME:-$HOME/Library/Android/sdk}
PLATFORM_VERSION="36.1"  # Target API level
BUILD_TOOLS_VERSION="37.0.0"

ANDROID_JAR="$ANDROID_HOME/platforms/android-$PLATFORM_VERSION/android.jar"
D8="$ANDROID_HOME/build-tools/$BUILD_TOOLS_VERSION/d8"

SRC_DIR="src"
BUILD_DIR="build"
OUTPUT_JAR="server.jar"

if [ ! -f "$ANDROID_JAR" ]; then
    echo "Error: android.jar not found at $ANDROID_JAR"
    echo "Please ensure ANDROID_HOME is set correctly and platform $PLATFORM_VERSION is installed."
    exit 1
fi

if [ ! -f "$D8" ]; then
    echo "Error: d8 not found at $D8"
    echo "Please ensure build-tools $BUILD_TOOLS_VERSION is installed."
    exit 1
fi

# Clean
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes"

export JAVA_HOME="/opt/homebrew/opt/openjdk@11"
export PATH="$JAVA_HOME/bin:$PATH"

echo "Compiling Java sources..."
# Use android.jar as the classpath for Android API stubs
"$JAVA_HOME/bin/javac" -source 11 -target 11 \
    -classpath "$ANDROID_JAR" \
    -d "$BUILD_DIR/classes" \
    $(find "$SRC_DIR" -name "*.java")

echo "Converting to DEX..."
# Step 2: Convert .class → .dex using d8
"$D8" --output "$BUILD_DIR" \
    --lib "$ANDROID_JAR" \
    $(find "$BUILD_DIR/classes" -name "*.class")

echo "Packaging into JAR..."
# Step 3: Package the .dex into a .jar
# app_process expects a jar containing classes.dex
cd "$BUILD_DIR"
jar cf "../$OUTPUT_JAR" classes.dex
cd ..

echo "Built $OUTPUT_JAR successfully"
echo "Deploy with: adb push $OUTPUT_JAR /data/local/tmp/"
echo "Run with: adb shell CLASSPATH=/data/local/tmp/$OUTPUT_JAR app_process / com.linktomac.Server"
