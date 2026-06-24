#!/usr/bin/env bash
set -e

IMAGE="android-builder"
GRADLE_CACHE="kestrel-gradle-cache"
# Persists the auto-generated debug.keystore so every build is signed with the
# same key (otherwise installs fail with INSTALL_FAILED_UPDATE_INCOMPATIBLE).
ANDROID_HOME_CACHE="kestrel-android-home"
CONTAINER_NAME="kestrel-builder-dev"
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

PROJECT_DIR="$(pwd)"

ensure_builder_container() {
    local existing_project_mount=""
    existing_project_mount=$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/project"}}{{.Source}}{{end}}{{end}}' "$CONTAINER_NAME" 2>/dev/null || true)

    if [ -n "$existing_project_mount" ] && [ "$existing_project_mount" != "$PROJECT_DIR" ]; then
        echo "▶ Recreating local builder container (project path changed)..."
        docker rm -f "$CONTAINER_NAME" >/dev/null
    fi

    if ! docker ps -a --format '{{.Names}}' | grep -Fxq "$CONTAINER_NAME"; then
        echo "▶ Creating persistent local builder container..."
        docker run -d \
            --platform linux/amd64 \
            --name "$CONTAINER_NAME" \
            -w /project \
            -v "$PROJECT_DIR:/project" \
            -v "$GRADLE_CACHE:/root/.gradle" \
            -v "$ANDROID_HOME_CACHE:/root/.android" \
            "$IMAGE" \
            tail -f /dev/null >/dev/null
    elif ! docker ps --format '{{.Names}}' | grep -Fxq "$CONTAINER_NAME"; then
        echo "▶ Starting persistent local builder container..."
        docker start "$CONTAINER_NAME" >/dev/null
    fi
}

echo "▶ Building APK inside local Docker builder..."
ensure_builder_container
docker exec "$CONTAINER_NAME" ./gradlew "$TASK"

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
