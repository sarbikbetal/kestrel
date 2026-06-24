# Kestrel

An open-source augmented-reality flight tracker for Android. Point your phone at the sky and
Kestrel overlays live aircraft exactly where they are, sourced in real time from the free
[adsb.lol](https://adsb.lol) ADS-B network. No account, no API key, no subscription.

- **AR sky view** — live rear-camera feed with planes drawn at their true positions using
  your GPS and the device's orientation sensors, just like an astronomy sky-map app.
- **Sensor-driven placement** — each aircraft's azimuth and elevation angle are computed
  from your position and the plane's lat/lon/altitude, then projected onto the screen based
  on where the device is pointing.
- **Off-screen hints** — arrows point toward nearby planes that are outside the current
  field of view.
- **Live map** — a small OpenStreetMap (osmdroid) view shows your position; tap it for a
  full-screen map with all tracked aircraft.

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
app/src/main/java/com/sarbikbetal/kestrel/
  data/        AdsbApi, Aircraft DTO + domain model, AircraftRepository (polling)
  location/    LocationProvider (fused location Flow)
  sensors/     OrientationProvider (azimuth/elevation/roll Flow)
  domain/      SkyMath (bearing, elevation, screen projection)
  ui/          SkyViewModel, Navigation
  ui/sky/      ArSkyScreen, CameraPreview, SkyOverlay
  ui/map/      MiniMap, MapScreen, MapMarkers
  ui/settings/ SettingsScreen, SettingsViewModel
  MainActivity.kt
Dockerfile     Android SDK + JDK build image
build.sh       One-command build (+ install) helper
```

## Prerequisites

The project supports two build workflows:

| Workflow | What you need |
|----------|--------------|
| **Docker** (recommended, no local SDK) | Docker + `adb` |
| **Android Studio** | Android Studio Ladybug (2024.2.1) or later + Android SDK 37 |

`adb` on macOS: `brew install android-platform-tools`

## Android Studio setup

1. Install **Android Studio Ladybug (2024.2.1)** or later — it ships with AGP support for
   the version used here (AGP 9.2.1 requires at minimum Android Studio Ladybug).
2. Inside Android Studio, open **SDK Manager** and install:
   - **Android SDK Platform 37**
   - **Android SDK Build-Tools 37.0.0**
3. Open the project: **File → Open** → select the repo root folder (the one containing
   `build.gradle.kts`).
4. Android Studio will detect the project and prompt you to locate your Android SDK. It
   creates `local.properties` automatically. If it doesn't, copy `local.properties.template`
   to `local.properties` and set `sdk.dir` to your SDK path
   (e.g. `/Users/<you>/Library/Android/sdk`).
5. Click **Sync Project with Gradle Files** (the elephant icon) and wait for the sync to
   finish — this downloads Gradle 9.6.0 and all dependencies.
6. Select a connected device or emulator (API 26+) in the device picker, then click
   **Run ▶**.

> **Note:** The first Gradle sync downloads several hundred MB. Subsequent syncs are fast.

## Docker setup (one-time)

Build the Android builder image (JDK 17 + Android SDK). This is slow the first time as it
downloads the SDK, then it is cached:

```bash
docker build --platform linux/amd64 -t android-builder .
```

## Local build (Docker, recommended)

These commands are for local development speed. CI/release builds are handled separately by
GitHub Actions workflow(s).

If you prefer shortcuts, use Make targets:

```bash
make help
```

Build a debug APK without installing:

```bash
./build.sh --build-only
# or: make build
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

Build + install to a connected device:

```bash
./build.sh
# or: make install
```

Release variants:

```bash
./build.sh --release   # APK
./build.sh --bundle    # AAB
# or: make release / make bundle
```

### How `build.sh` works now

`build.sh` is optimized for local iteration speed:

- Reuses a persistent container: `kestrel-builder-dev`
- Reuses volumes:
  - `kestrel-gradle-cache` (`/root/.gradle`)
  - `kestrel-android-home` (`/root/.android`, includes debug keystore)
- Runs Gradle with `docker exec` so the Gradle daemon can stay warm between builds

The first build downloads Gradle and dependencies; later builds are much faster.

### Container maintenance (optional)

If local build state ever gets weird, reset the dev container:

```bash
docker rm -f kestrel-builder-dev
# or: make reset-container
```

To also clear caches:

```bash
docker volume rm kestrel-gradle-cache kestrel-android-home
# or: make reset-caches
```

You can still run Gradle manually inside the persistent dev container:

```bash
docker exec kestrel-builder-dev ./gradlew assembleDebug
```

## Run / deploy to a physical device

1. On your phone, enable **Developer options → USB debugging**.
2. Connect it via USB and confirm it appears:

   ```bash
   adb devices
   ```

3. Build and install in one step:

   ```bash
   ./build.sh
   ```

   This builds the APK in Docker, then installs it with `adb install -r`.

On first launch, grant **camera** and **location** permissions for the full AR experience.

## Test

Run unit tests with the local builder container:

```bash
docker exec kestrel-builder-dev ./gradlew test
# or: make test
```

If the container is not running yet, run `./build.sh --build-only` once first (it creates/starts
`kestrel-builder-dev`).

Instrumented tests (`connectedAndroidTest`) require a connected device or emulator; since
the device is attached to the host rather than the container, run those against a phone via
`adb` from the host once a test suite is added.

## Develop

- Edit Kotlin/Compose sources in `app/src/main/java/com/sarbikbetal/kestrel/`.
- Rebuild and redeploy with `./build.sh` (fast after the first build thanks to the Gradle
  cache).
- Inspect runtime logs from the host:

  ```bash
  adb logcat --pid=$(adb shell pidof -s com.sarbikbetal.kestrel)
  ```

### Tuning the AR alignment

These values are best calibrated on real hardware by pointing at an actual plane:

- Field-of-view constants `H_FOV` / `V_FOV` in
  [`app/src/main/java/com/sarbikbetal/kestrel/ui/sky/SkyOverlay.kt`](app/src/main/java/com/sarbikbetal/kestrel/ui/sky/SkyOverlay.kt)
- The roll rotation sign in `SkyMath.project` in
  [`app/src/main/java/com/sarbikbetal/kestrel/domain/SkyMath.kt`](app/src/main/java/com/sarbikbetal/kestrel/domain/SkyMath.kt)
- Poll interval / search radius in
  [`app/src/main/java/com/sarbikbetal/kestrel/ui/SkyViewModel.kt`](app/src/main/java/com/sarbikbetal/kestrel/ui/SkyViewModel.kt)

## Notes

- **No API keys needed.** Both adsb.lol and osmdroid (OpenStreetMap) are free and keyless.
  Please respect their usage/tile policies.

## Contributing

Contributions are welcome — bug reports, feature ideas, and pull requests all appreciated.
Please open an issue first for significant changes so we can discuss the approach.

## License

MIT
