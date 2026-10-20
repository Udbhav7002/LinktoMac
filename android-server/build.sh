#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Resolve ANDROID_HOME if not already exported
if [ -z "$ANDROID_HOME" ]; then
    if [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
    elif [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    fi
fi

echo "Building server.jar with Gradle..."
./gradlew clean buildServerJar

echo "Built server.jar successfully at $SCRIPT_DIR/server.jar"
echo "Deploy with: adb push server.jar /data/local/tmp/"
echo "Run with: adb shell CLASSPATH=/data/local/tmp/server.jar app_process / com.linktomac.Server"

