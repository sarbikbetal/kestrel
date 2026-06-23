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

## Testing and Verification Expectations

- Validate build behavior for touched paths before finishing (`assembleDebug` minimum when feasible).
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

- 2026-06-24 - Decision: Release builds fall back to debug signing when `keystore.properties` is absent so release APKs are installable locally and from CI. Rationale: Unsigned release APKs fail installation with "App not installed". Impact: `./build.sh --release` and GitHub releases produce signed APKs; production signing via `keystore.properties` when ready.
- 2026-06-24 - Decision: Migrated app UI to Material 3 Expressive (MaterialExpressiveTheme, expressive motion/shapes, ModalBottomSheet for plane details and map layers, SegmentedButton settings, bottom navigation). Rationale: User-requested modern expressive design language across all screens. Impact: Visual refresh; HUD monospace styling removed; plane details and map layers use bottom sheets; nav bar hidden while a plane is selected.
- 2026-06-24 - Decision: Added AGENTS.md project guide with Kotlin/Android best practices and mandatory decision logging. Rationale: Improve consistency and preserve context across AI-assisted changes. Impact: Future changes should be easier to review, reason about, and maintain.
