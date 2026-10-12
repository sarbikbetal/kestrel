# Development Guide

## Prerequisites

| Workflow | What you need |
|----------|--------------|
| **Docker** (recommended) | Docker + `adb` |
| **Android Studio** | Ladybug 2024.2.1+ · SDK 37 · Build-Tools 37.0.0 |

`adb` on macOS: `brew install android-platform-tools`

## Android Studio setup

1. Open **SDK Manager** → install **Android SDK Platform 37** and **Build-Tools 37.0.0**.
2. **File → Open** the repo root (the folder containing `build.gradle.kts`).
3. Copy `local.properties.template` → `local.properties` and set `sdk.dir` if not auto-detected.
4. **Sync Project with Gradle Files**, then **Run ▶** to a device/emulator (API 26+).

## Docker - one-time image build

```bash
docker build --platform linux/amd64 -t android-builder .
```

## Build commands

```bash
./build.sh                  # build debug APK + install   (make install)
./build.sh --build-only     # build only, no install      (make build)
./build.sh --release        # signed release APK          (make release)
./build.sh --bundle         # release AAB                 (make bundle)
docker exec kestrel-builder-dev ./gradlew test   # unit tests  (make test)
make help                   # all targets
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

## How `build.sh` works

Reuses a persistent container (`kestrel-builder-dev`) and two volumes:

- `kestrel-gradle-cache` - `~/.gradle` (Gradle daemon + dependency cache)
- `kestrel-android-home` - `~/.android` (debug keystore)

First build is slow (downloads Gradle + deps). Subsequent builds are fast because the daemon
stays warm across `docker exec` calls.

## Container maintenance

```bash
docker rm -f kestrel-builder-dev          # reset container   (make reset-container)
docker volume rm kestrel-gradle-cache kestrel-android-home   # clear caches (make reset-caches)
```

## Deploy to a physical device

1. Enable **Developer options → USB debugging** on your phone.
2. `adb devices` - confirm it appears.
3. `./build.sh` - builds and installs in one step.

## Iterating

```bash
# edit sources in app/src/main/java/com/sarbikbetal/kestrel/
./build.sh          # rebuild + redeploy (fast after first build)
```

Watch logs while the app is running:

```bash
adb logcat --pid=$(adb shell pidof -s com.sarbikbetal.kestrel)
```

Instrumented tests (`connectedAndroidTest`) require a connected device. Run them from the host
via `adb` once a test suite exists - the Docker container doesn't have USB access.

## Project layout

```
app/src/main/java/com/sarbikbetal/kestrel/
  data/        AdsbApi, AircraftRepository (polling), RouteRepository, TraceRepository,
               Aircraft DTO + domain model, FlightDetails, TrackSample
  location/    LocationProvider (fused location Flow)
  sensors/     OrientationProvider (dual-sensor azimuth/elevation/roll/compassBearing Flow)
  domain/      SkyMath (bearing, elevation, screen projection), PlaneSize, CountryFlags
  settings/    UserPrefs, SettingsRepository (DataStore), Formatters
  ui/          SkyViewModel, Navigation
  ui/sky/      ArSkyScreen (HUD compass, SyntheticSky, CameraPreview), SkyOverlay, PlaneGraphs
  ui/map/      MiniMap, MapScreen, MapMarkers, PlaneMarkerLayer, MapTiles
  ui/settings/ SettingsScreen, SettingsViewModel
  ui/theme/    Theme (Material You / Astro Red / Avionics Green, LocalAppTheme)
  MainActivity.kt
Dockerfile     Android SDK + JDK build image
build.sh       Persistent-container build helper
Makefile       Short make targets for common tasks
```

## Tuning AR alignment

All values below are best calibrated on real hardware by pointing at an actual plane.

### Sensor architecture

`OrientationProvider` runs two sensors in parallel:

| Sensor | Used for |
|--------|----------|
| `TYPE_GAME_ROTATION_VECTOR` (accel + gyro only) | `azimuthDeg`, `elevationDeg`, `rollDeg` - stable AR rendering, immune to magnetic interference |
| `TYPE_ROTATION_VECTOR` (magnetometer-fused) | `compassBearingDeg` - true-north reference for heading display, minimap, and plane azimuth projection |

Never use `azimuthDeg` for directional display or plane math; always use `compassBearingDeg`.

### Key values

- **`pxPerDeg`** in [`domain/SkyMath.kt`](app/src/main/java/com/sarbikbetal/kestrel/domain/SkyMath.kt) -
  controls how many screen pixels correspond to one degree of arc. Increase to zoom in on the
  sky, decrease to show a wider field.
- **Roll rotation sign** in `SkyMath.project` - if overlays rotate the wrong way when you tilt
  the device, negate this.
- **Poll interval / search radius** in [`ui/SkyViewModel.kt`](app/src/main/java/com/sarbikbetal/kestrel/ui/SkyViewModel.kt) -
  trade-off between data freshness and API rate limits.
- **Compass accuracy** - if planes drift sideways, the magnetometer needs calibration. The
  Settings → Sensors screen shows the current accuracy level and calibration instructions
  (figure-8 motion).

## Release signing

Release builds fall back to debug signing when `keystore.properties` is absent (APK is
installable but not production-signed). To use a real keystore, create `keystore.properties`:

```
storeFile=path/to/keystore.jks
storePassword=…
keyAlias=…
keyPassword=…
```

## Contributing

Open an issue first for significant changes so we can discuss the approach before you invest
time in a PR. Bug reports and small fixes are welcome directly.
