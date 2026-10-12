<p align="center">
  <img src="docs/logo.png" alt="Kestrel logo" width="128" height="128">
</p>

<h1 align="center">Kestrel</h1>

AR flight tracker for Android. Point your phone at the sky - planes appear where they are.
No account, no API key, no subscription.

## Features

- **AR sky view** - synthetic sky (default) or live camera, with gyro-locked plane overlays,
  horizon, stars, and a scrolling HUD compass
- **Stable placement** - dual-sensor fusion (gyro for rendering + magnetometer for true north),
  same approach as astronomy sky-map apps
- **Off-screen hints** - arrows point toward planes outside the field of view
- **Map** - mini-map in the AR view, full-screen map with tap-to-select; selected plane
  outlined in black. Four tile sources (OSM, Terrain, Transport,
  Humanitarian)
- **Flight details** - route, progress bar with km flown / ETA, speed & altitude history,
  aircraft info
- **Themes** - Material You, Astro Red (night), Avionics Green (phosphor HUD)
- **Dual data sources** - positions from `adsb.lol` or `airplanes.live`; routes from
  `hexdb.io` or `adsbdb.com`, with automatic fallback

## Stack

Kotlin · Jetpack Compose (Material 3 Expressive) · CameraX · Dual rotation-vector sensors ·
FusedLocationProvider · Retrofit · DataStore · osmdroid

## Build

> Full setup instructions, project layout, AR tuning, and release signing are in [DEVELOPMENT.md](DEVELOPMENT.md).


**Docker (recommended):**

```bash
# one-time image build
docker build --platform linux/amd64 -t android-builder .

./build.sh          # build + install  (make install)
./build.sh --release  # signed release APK
make help           # all targets
```

**Android Studio:** SDK 37, Build-Tools 37.0.0, AGP 9.2.1 (requires Ladybug 2024.2.1+).

The first build is slow (downloads Gradle + deps). Subsequent builds reuse the persistent
`kestrel-builder-dev` container and warm Gradle daemon.

## Permissions

Location - on launch. Camera - only when switching to live camera AR view.

## Notes

All APIs and tile servers are free and keyless. Please respect their usage policies.
Release builds fall back to debug signing when `keystore.properties` is absent.

## License

MIT
