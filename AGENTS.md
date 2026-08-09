# AGENTS.md - Kestrel Android Development Guide

This file defines how AI agents should contribute to this Kotlin Android project.
Use it as the default working agreement for code changes.

## Project Context

- App type: Kotlin Android app using Jetpack Compose.
- Toolchain baseline: Java/Kotlin 17, `compileSdk` 37, `build-tools` 37.0.0.
- Key libraries in use: Compose, Navigation Compose, CameraX, Retrofit + Kotlinx Serialization, DataStore, osmdroid.

## Core Development Principles

- Prefer small, focused changes over broad refactors unless explicitly requested.
- Keep behavior stable; do not silently change app flows or default settings.
- Optimize for readability first, then performance.
- Use existing patterns already present in the module before introducing new ones.
- Avoid adding dependencies unless there is a clear, documented need.

## Kotlin Best Practices

- Keep functions short and single-purpose; extract helpers when logic grows.
- Use immutable values (`val`) by default; limit mutable state scope.
- Model domain and UI state with explicit types (sealed classes/data classes where helpful).
- Use nullable types intentionally and handle nullability at boundaries.
- Favor extension functions for reusable, context-specific utilities.
- Use coroutines and suspend APIs for async work; avoid blocking calls on main thread.

## Android + Compose Best Practices

- Keep composables stateless when possible; hoist state to caller/ViewModel layer.
- Pass only required state and callbacks to composables.
- Use `remember`/`rememberSaveable` only for UI-local state.
- Keep side effects explicit using Compose effect APIs (`LaunchedEffect`, etc.).
- Do not perform networking, file IO, or heavy computation directly inside composables.
- Respect lifecycle-aware APIs from AndroidX lifecycle/runtime libraries.
- Keep navigation routes stable and typed consistently with existing project style.

## Architecture and Layering

- UI layer (`ui/*`) should focus on rendering, user events, and presentation-only mapping.
- Data fetching/parsing should remain outside composables and away from navigation wiring.
- Centralize shared business logic; avoid duplicate calculations across screens.
- When introducing a new pattern, update this file and explain why the pattern is needed.

## Build Commands

Use `make` targets for all local build and verification tasks — do not invoke `build.sh` or `gradlew` directly.

| Command | Purpose |
|---|---|
| `make build` | Build debug APK (no install) |
| `make install` | Build + install debug APK to connected device |
| `make release` | Build release APK |
| `make bundle` | Build release AAB |
| `make test` | Run unit tests |
| `make image` | (Re)build the Docker builder image |
| `make up` / `make down` | Start / stop the persistent dev container |

## Testing and Verification Expectations

- For non-trivial logic, add or update tests when the project already has an appropriate test location.
- For UI behavior changes, include manual verification notes in PR/commit message.

## Performance and Reliability

- Avoid unnecessary recomposition triggers in Compose.
- Keep allocations low in hot rendering paths (maps, AR, graph rendering).
- Use structured error handling; avoid swallowing exceptions silently.
- Log operationally useful errors without leaking secrets or sensitive data.

## Dependency and Build Rules

- Keep Android SDK/toolchain changes intentional and documented.
- Match Docker and CI SDK versions when changing `compileSdk`/`build-tools`.
- A Docker container is available for building the app via `make`; prefer it when local toolchain/runtime setup is missing or inconsistent.
- Do not commit secrets, signing keys, or local machine-specific configuration.

## Required Decision Log (Important)

When an AI agent makes an important technical or product decision, it MUST append an entry in this file under `## Decision Log`.

Important decisions include:
- architecture changes,
- dependency additions/removals,
- API contract changes,
- data model/storage changes,
- major UI behavior changes,
- build/release pipeline changes.

Entry format (append newest on top):

`- YYYY-MM-DD - Decision: <what changed>. Rationale: <why>. Impact: <user/dev/build impact>.`

## Decision Log

- 2026-08-02 - Decision: App `versionName`/`versionCode` now track GitHub release tags. Release workflow passes `-PVERSION_NAME` from the next `vMAJOR.MINOR.PATCH` tag; local builds fall back to `git describe --tags`. `versionCode` is derived as `major*1e6 + minor*1e3 + patch`. Rationale: About screen and APK metadata were stuck at hardcoded `1.0` while releases were already at `v0.0.x`. Impact: released APKs show the matching tag version; no commit churn on each release.
- 2026-06-28 - Decision: Leg split is now ground-aware, not time-only. A 30 min+ gap splits the path only when an adjacent fix is at/near the ground (`GROUND_ALT_M = 500m`, `touchedGround` in `MapScreen`). Rationale: pure time-gap splitting truncated long-haul paths mid-air — verified live traces show oceanic coverage holes of 200 min+ between two cruise-altitude fixes (e.g. 33000↔35000 ft), while every real turnaround has a ground fix beside the gap. The altitude check keeps the whole flight across coverage holes and still splits at parked turnarounds (re-validated across 6 live flights: all current legs now start at/near ground). Impact: map path traces the full current flight from departure; no API/dependency change.
- 2026-06-28 - Decision: Selecting a plane on the map now draws its flown path and highlights the marker. `TrackSample` gained `lat`/`lon` (parsed from trace-row indices 1/2 in `TraceRepository`, previously discarded), `MapScreen` renders the trace as an osmdroid `Polyline` (theme `primary`, kept at overlay index 1 below the plane markers) and a non-rotating hollow selection ring (`selectionMarker` in `MapMarkers`) that follows the selected plane's live position. Rationale: the adsb.lol globe trace we already fetch contains the real travelled path, and osmdroid's `Polyline`/`Marker` cover both needs with no new dependency; a hollow ring stays legible on top of the icon so z-order doesn't matter. Impact: map selection now shows the route + clear selection affordance; trace data carries positions for any future use. No new deps.
- 2026-06-28 - Decision: Fixed janky horizontal (azimuth) panning in the AR view with a complementary heading filter in `OrientationProvider`. Rendering and the compass display read `compassBearingDeg`, which was previously the raw `TYPE_ROTATION_VECTOR` magnetometer bearing updated only at `SENSOR_DELAY_UI` (~16 Hz) with a heavy EMA — so side-to-side motion stepped/lagged while tilt (game-sensor elevation/roll at ~50 Hz) stayed smooth. `compassBearingDeg` is now the smooth 50 Hz game-sensor azimuth re-anchored to true north via a slowly-tracked offset (`headingOffset`, `offsetAlpha = 0.05`) updated from the compass listener. Rationale: keep planes north-locked (the reason compass was used) while updating heading at the game-sensor rate; slow offset tracking rides fast pans on the smooth gyro signal and only corrects gradual game-azimuth drift. Impact: horizontal panning is now as smooth as vertical; heading display also benefits. Fallback devices (no game sensor) keep using the already-north-aligned rotation vector unchanged.
- 2026-06-28 - Decision: Moved per-frame work out of the AR draw phase. `SkyOverlay` now pre-formats each plane's tag strings (callsign/altitude/speed/distance via `Formatters` + `String.format`) in a `remember(planes, prefs)` block and caches the `take(60)` visible list, instead of recomputing them inside the ~50 Hz orientation-driven canvas redraw. `SyntheticSky` (sky/ground polygons) and `HudCompass` (scoop background) now reuse remembered `Path` objects cleared with `rewind()` instead of allocating a new `Path` every draw. Rationale: the AR canvases redraw at sensor rate (~50 Hz) while their string/Path inputs only change at ~10 Hz; per-frame `String.format` and `Path()` allocations were avoidable GC pressure in the hottest render path (confirmed against current Compose graphics docs recommending `remember`/`drawWithCache` + `Path.rewind`). Impact: lower allocation/GC churn and steadier AR frame times; no visual or behavioral change.
- 2026-06-24 - Decision: AR sky/camera toggle now uses `AutoAwesome` (synthetic sky) and `Videocam` (live camera) icons, and all FABs (AR toggle, map layers, map recenter) use the Material 3 Expressive squared default shapes (`FloatingActionButtonDefaults.shape`/`smallShape`) instead of `shapes.largeIncreased`. Rationale: clearer affordance for the view toggle and consistent expressive squared FAB language. Impact: visual-only.
- 2026-06-24 - Decision: Decoupled high-frequency device orientation from `SkyUiState` and lightened the orientation EMA. `SkyViewModel` now exposes a raw ~50 Hz `orientation: StateFlow<DeviceOrientation>` for the AR canvas while `uiState` is fed a 10 Hz `sample()`-throttled copy; the AR canvases (`SyntheticSky`, `SkyOverlay`, `HudCompass`) take `State<DeviceOrientation>` and read it in the draw phase, and `OrientationProvider` uses `gameAlpha = 0.5` for the render path. Rationale: orientation in the single UI-state object forced a 50 Hz recomposition of the whole tree (incl. the osmdroid mini-map), and the heavy `alpha = 0.15` filter added ~110 ms tracking lag. Impact: AR overlay tracks the phone smoothly with far less jank; mini-map/compass/settings now refresh at 10 Hz instead of 50 Hz. Compass bearing (display only) keeps the heavier `compassAlpha = 0.15`.
- 2026-06-25 - Decision: Added 2 new themes Astro Red (OLED black, deep red, amber accents, expressive rounded shapes) and Avionics Green (dark charcoal, phosphor green, amber alerts, monospace typography, sharp 0dp corners, CRT scanline overlay). Default is Material You. Theme persisted in DataStore. Rationale: Night-sky observers need minimal blue light; aviation enthusiasts wanted a retro CRT HUD aesthetic. Impact: Both themes are always dark; dynamic colour and light mode are removed entirely. Scanlines applied globally as a `Modifier.drawWithContent` overlay when Avionics Green is active.
- 2026-06-24 - Decision: Added a root `Makefile` for local developer ergonomics with Docker-oriented targets (`build`, `install`, `release`, `bundle`, `test`, and container/cache maintenance). Rationale: Provide short, discoverable commands for the local persistent-container workflow without affecting CI workflows. Impact: Faster onboarding and day-to-day local iteration; existing `build.sh` flow remains the underlying implementation.
- 2026-06-24 - Decision: Optimized `build.sh` for local development by reusing a persistent Docker builder container (`kestrel-builder-dev`) instead of one-shot `docker run --rm`. Rationale: Reusing the same container allows Gradle daemon reuse and speeds up iterative local builds while CI remains handled by GitHub workflows. Impact: Faster local build loops; Dockerized local environment remains consistent with existing image/volumes.
- 2026-06-24 - Decision: Documented Docker container availability as a standard app build path for agents. Rationale: Local environments may lack Java/Android toolchain prerequisites, while containerized builds provide a consistent setup. Impact: Agents can reliably validate builds using Docker when host setup is incomplete.
- 2026-06-24 - Decision: Release builds fall back to debug signing when `keystore.properties` is absent so release APKs are installable locally and from CI. Rationale: Unsigned release APKs fail installation with "App not installed". Impact: `./build.sh --release` and GitHub releases produce signed APKs; production signing via `keystore.properties` when ready.
- 2026-06-24 - Decision: Migrated app UI to Material 3 Expressive (MaterialExpressiveTheme, expressive motion/shapes, ModalBottomSheet for plane details and map layers, SegmentedButton settings, bottom navigation). Rationale: User-requested modern expressive design language across all screens. Impact: Visual refresh; HUD monospace styling removed; plane details and map layers use bottom sheets.
- 2026-06-24 - Decision: Added AGENTS.md project guide with Kotlin/Android best practices and mandatory decision logging. Rationale: Improve consistency and preserve context across AI-assisted changes. Impact: Future changes should be easier to review, reason about, and maintain.
