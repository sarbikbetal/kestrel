# Plane Spotter

An augmented-reality "window to the sky" for Android. Point your phone upward and
it draws real aircraft flying above you — sourced live from the free
[adsb.lol](https://adsb.lol) ADS-B API — positioned using your GPS location and the
device's orientation sensors, much like an astronomy sky-map app.

- **AR sky view** — live rear-camera feed with a dark scrim; planes are overlaid where
  they actually are in the sky. If camera permission is denied, a synthetic
  horizon/sky background is shown instead.
- **Sensor-driven placement** — each aircraft's azimuth (compass bearing) and elevation
  angle are computed from your position and the plane's lat/lon/altitude, then projected
  onto the screen based on where the device is pointing.
- **Off-screen hints** — arrows point toward nearby planes that are outside the current
  field of view.
- **Mini-map** — a small OpenStreetMap (osmdroid) view in the bottom-left shows your
  position. Tap it to open a full live map with all tracked aircraft.

## Tech stack

- Kotlin + Jetpack Compose (Material 3)
- CameraX (AR background)
- `TYPE_ROTATION_VECTOR` sensor (orientation)
- FusedLocationProvider (GPS)
- Retrofit + OkHttp + kotlinx.serialization (adsb.lol API)
- osmdroid (maps, no API key required)
- Compose Navigation

## Project layout

```
app/src/main/java/com/adsbdroid/
  data/        AdsbApi, Aircraft DTO + domain model, AircraftRepository (polling)
  location/    LocationProvider (fused location Flow)
  sensors/     OrientationProvider (azimuth/elevation/roll Flow)
  domain/      SkyMath (bearing, elevation, screen projection)
  ui/          SkyViewModel, Navigation
  ui/sky/      ArSkyScreen, CameraPreview, SkyOverlay
  ui/map/      MiniMap, MapScreen, MapMarkers
  MainActivity.kt
Dockerfile     Android SDK + JDK build image
build.sh       One-command build (+ install) helper
```

## Prerequisites

This project is built entirely inside Docker — **no Android Studio or local Android SDK
required**. You only need:

- **Docker** (running)
- **adb** on the host (`brew install android-platform-tools` on macOS) — used only to
  install the APK to a physical device

## One-time setup

Build the Android builder image (JDK 17 + Android SDK). This is slow the first time as it
downloads the SDK, then it is cached:

```bash
docker build --platform linux/amd64 -t android-builder .
```

The Gradle wrapper is already committed, so no extra setup is needed.

## Build

Build a debug APK without installing:

```bash
./build.sh --build-only
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

The first build downloads Gradle, the Android Gradle Plugin, and all dependencies (several
minutes). These are cached in a Docker volume (`adsb-droid-gradle-cache`), so later builds
are much faster.

You can also invoke Gradle directly inside the container if needed:

```bash
docker run --rm --platform linux/amd64 \
  -v "$(pwd):/project" \
  -v "adsb-droid-gradle-cache:/root/.gradle" \
  android-builder \
  ./gradlew assembleDebug
```

## Run / deploy to a physical device

1. On your phone, enable **Developer options -> USB debugging**.
2. Connect it via USB and confirm it appears:

   ```bash
   adb devices
   ```

3. Build and install in one step:

   ```bash
   ./build.sh
   ```

   This builds the APK in Docker, then `adb install -r`'s it onto the connected phone.

On first launch, grant **camera** and **location** permissions for the full AR experience.

## Test

Run unit tests inside the container:

```bash
docker run --rm --platform linux/amd64 \
  -v "$(pwd):/project" \
  -v "adsb-droid-gradle-cache:/root/.gradle" \
  android-builder \
  ./gradlew test
```

Instrumented tests (`connectedAndroidTest`) require a connected device or emulator; since
the device is attached to the host rather than the container, run those against a phone via
`adb` from the host once a test suite is added.

## Develop

- Edit Kotlin/Compose sources in `app/src/main/java/com/adsbdroid/`.
- Rebuild and redeploy with `./build.sh` (fast after the first build thanks to the Gradle
  cache).
- Inspect runtime logs from the host:

  ```bash
  adb logcat --pid=$(adb shell pidof -s com.adsbdroid)
  ```

### Tuning the AR alignment

These values are best calibrated on real hardware by pointing at an actual plane:

- Field-of-view constants `H_FOV` / `V_FOV` in
  [`app/src/main/java/com/adsbdroid/ui/sky/SkyOverlay.kt`](app/src/main/java/com/adsbdroid/ui/sky/SkyOverlay.kt)
- The roll rotation sign in `SkyMath.project` in
  [`app/src/main/java/com/adsbdroid/domain/SkyMath.kt`](app/src/main/java/com/adsbdroid/domain/SkyMath.kt)
- Poll interval / search radius in
  [`app/src/main/java/com/adsbdroid/ui/SkyViewModel.kt`](app/src/main/java/com/adsbdroid/ui/SkyViewModel.kt)

## Notes

- **Everything targets Android 37.** `compileSdk = 37` with `compileSdkMinor = 0`,
  `buildToolsVersion = "37.0.0"`, and `targetSdk = 37`, matching the SDK packages baked into
  the Docker image (`platforms;android-37.0`, `build-tools;37.0.0`). This requires AGP 8.10.1+
  (for `compileSdkMinor`); the project uses AGP 9.2.1 + Gradle 9.6.0 + Kotlin 2.2.21. Because
  the build is pinned to the exact 37 packages in the image, Gradle no longer auto-downloads
  any other build-tools/platform on each run.
- **No API keys needed.** Both adsb.lol and osmdroid (OpenStreetMap) are free and keyless.
  Please respect their usage/tile policies.
