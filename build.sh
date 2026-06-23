#!/usr/bin/env bash
set -e

IMAGE="android-builder"
GRADLE_CACHE="kestrel-gradle-cache"
# Persists the auto-generated debug.keystore so every build is signed with the
# same key (otherwise installs fail with INSTALL_FAILED_UPDATE_INCOMPATIBLE).
ANDROID_HOME_CACHE="kestrel-android-home"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

# Parse flags
INSTALL=true
TASK="assembleDebug"
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --build-only) INSTALL=false ;;
        --release) TASK="assembleRelease" ;;
        --bundle) TASK="bundleRelease"; INSTALL=false ;;
        *) echo "Unknown flag: $1"; exit 1 ;;
    esac
    shift
done

if [ "$TASK" = "assembleRelease" ]; then
    APK_PATH="app/build/outputs/apk/release/app-release.apk"
elif [ "$TASK" = "bundleRelease" ]; then
    APK_PATH="app/build/outputs/bundle/release/app-release.aab"
fi

echo "▶ Building APK inside Docker..."
docker run --rm \
    --platform linux/amd64 \
    -v "$(pwd):/project" \
    -v "$GRADLE_CACHE:/root/.gradle" \
    -v "$ANDROID_HOME_CACHE:/root/.android" \
    "$IMAGE" \
    ./gradlew $TASK

if [ "$INSTALL" = true ]; then
    echo ""
    echo "▶ Checking for connected device..."
    DEVICES=$(adb devices | grep -v "List of devices" | grep "device$" | wc -l | tr -d ' ')

    if [ "$DEVICES" -eq 0 ]; then
        echo "✗ No Android device connected. Plug in your phone and enable USB debugging."
        echo "  APK is at: $APK_PATH"
        exit 1
    fi

    echo "▶ Installing APK to device..."
    adb install -r "$APK_PATH"
    echo "✓ Done. App installed."
else
    echo "✓ Build complete. APK at: $APK_PATH"
fi
