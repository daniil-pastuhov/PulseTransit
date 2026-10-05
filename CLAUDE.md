# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

PulseTransit — an Android app (Kotlin, Jetpack Compose) that shows a live public-transport departure board for Norwegian stops using the Entur APIs, plus weather from MET Norway. It is designed to run as an always-on wall display, so a lot of the code is about burn-in protection, sensor-driven screen wake/dim, and adaptive refresh rates.

## Commands

Requires JDK 17 (Gradle toolchain resolves it automatically via foojay). Single module: `:app`.

Every build needs `app/google-services.json` (Firebase Crashlytics); without it the
`processDebugGoogleServices` task fails before anything compiles. See "Firebase setup" in README.MD.
Crash collection is off in debug builds by design, via the `crashlyticsCollectionEnabled` manifest
placeholder set per build type — a debug build reporting nothing is expected, not a broken integration.

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected device/emulator
./gradlew test                   # local unit tests
./gradlew testDebugUnitTest --tests "*WeatherRepositoryImplTest*"  # single test class (the
                                 # `test` lifecycle task does not accept --tests; use the variant task)
./gradlew connectedAndroidTest   # instrumented tests (needs device/emulator)
./gradlew lint                   # Android lint
```

**Releases** are cut with `scripts/release`, not by pushing to main: it bumps `versionName` on a
`release/vX.Y.Z` branch and writes `release-notes/vX.Y.Z/<play-locale>.txt`. CI publishes pushes to
`release/v*` to Play internal (copying the notes into GPP's git-ignored
`app/src/main/play/release-notes/` via `scripts/stage-release-notes.sh`), and on the PR's merge
promotes that release to closed testing and tags `vX.Y.Z`. Main publishes nothing. See "Releasing"
in README.MD.

**Tests.** `app/src/test` covers the offline cache and the MET caching contract with Ktor's
`MockEngine` and in-memory fake DAOs (`FakeCacheDaos.kt`). Those fakes reproduce the DAO queries by
hand, so the real SQL is pinned separately by `app/src/androidTest` (`CacheDatabaseTest`) against an
in-memory Room database — if you change a `@Query`, that instrumented test is what catches it.

`StopBoardViewModelTest` covers the auto-theme hysteresis, offline staleness, grouping and
auto-dim against fakes (`ui/viewmodel/Fakes.kt`). Two things about it are load-bearing: the
ViewModel's clock and auto-refresh are `while (true)` loops on the test dispatcher, so
`advanceUntilIdle()` never returns — use `runCurrent()`/`advanceTimeBy()` — and every test runs
through the `boardTest` helper, which cancels `viewModelScope` afterwards. Without that
cancellation runTest cannot drain the scheduler and the whole suite stalls until it times out.

## Architecture

MVVM with Koin for DI. All dependency wiring lives in `di/AppModule.kt`; ViewModels are obtained in composables via `koinViewModel()`. When adding a repository or ViewModel, register it there.

**Navigation** uses androidx **Navigation 3** (`NavDisplay` + `rememberNavBackStack`), not the classic Navigation component. `Navigation.kt` defines the entry provider; route keys (`Main`, `Settings`) are `NavKey` objects in `NavigationKeys.kt`. Navigation is done by mutating the back stack (`backStack.add(...)` / `removeLastOrNull()`).

**Data layer** (`data/`) follows an interface + `*Impl` pair convention:

- `EnturRepositoryImpl` talks to two Entur services via a shared Ktor `HttpClient`: the Geocoder v3 REST API (stop search / nearby stops) and the Journey Planner v3 GraphQL API (departures, quays, trips). GraphQL queries are raw strings POSTed with kotlinx.serialization — there is no GraphQL client library. Every Entur request must send the `ET-Client-Name` header.
- `WeatherRepositoryImpl` calls `api.met.no` locationforecast, which requires a `User-Agent` header.
- `SettingsRepositoryImpl` persists all *settings* through a synchronous multiplatform-settings `Settings` (SharedPreferences file `app_settings` on Android, built by `createAppSettings` in `data/AndroidSettings.kt`). Complex objects (favorites, destination stations) and string sets (filtered lines, selected quays) are stored as kotlinx.serialization JSON strings, because NSUserDefaults has no string-set type; `migrateLegacyStringSets` converts sets written by older builds once at startup. There is no DataStore.
- `data/cache/` is a Room database (`CacheDatabase`, wired in `AppModule`) holding **only** the offline cache of network data — departures, fastest-route trips, and the MET forecast. It is disposable derived data, never a source of truth, so schema changes drop the tables rather than migrate. Settings do not live here.
- No JVM or Android APIs in `data/` or `ui/viewmodel/` (they move to a KMP `commonMain` next): time is `kotlin.time.Clock`/`Instant` plus kotlinx-datetime (core library desugaring covers API < 26), HTTP dates use Ktor's `fromHttpToGmtDate`, and the `HttpClient` comes from `createHttpClient(engine)`. Anything that genuinely needs the platform sits behind an interface in `platform/` — `ClockFormatter` (localized clock strings), `LocationProvider` — with the Android implementation beside it, registered in `AppModule`. UI state carries no `R.string` ids; e.g. `SettingsViewModel.LocationStatus` is a sealed type the screen maps to strings.
- Shared domain models live in `data/Models.kt`; API response DTOs are private nested classes inside each repository impl and never leak out.
- Repositories still swallow exceptions rather than throwing, but the cached calls (`getDepartures`, `getFastestTrip`, `getWeather`) no longer return bare empty lists on failure: they return `Sourced<T>`, falling back to the last good cached response and reporting when it was fetched via `Sourced.fetchedAtMillis`. Only when nothing is cached is the result empty — keep this in mind when debugging "no data" symptoms. Un-cached calls (stop search, nearby, quays) do still fail silently to empty.
- `WeatherRepositoryImpl` must honour MET's terms: coordinates truncated to 4 decimals, no repeat request before the `Expires` time, and revalidation with `If-Modified-Since`/`304`. The cache is what implements this, so don't bypass it.

**State**: each ViewModel exposes a single `UiState` data class through one `MutableStateFlow`. Settings are not reactive — screens call `viewModel.loadSettings()` from a `LaunchedEffect` on navigation entry to re-read SharedPreferences, which is how changes made on the Settings screen reach the main board.

**Always-on display machinery** (mostly wired through `StopBoardViewModel`):

- Auto-refresh loop with a dynamic interval: base interval multiplied ×5 during 00–05h and ×2 while the screen is dimmed (`calculateNextRefreshInterval`). Weather refreshes on its own 30-minute cadence inside the same loop.
- Screen wake/dim driven by sensors: ambient light via `ui/sensors/SystemSensorManager` (which also drives the `AUTO` theme mode), camera-based motion detection via CameraX in `ui/sensors/CameraMotionDetector`. `motionEvents()` is cold — the camera is open exactly while it is collected — and `MainScreen` drives it through `startCameraMotion()`/`stopCameraMotion()` from a `LifecycleStartEffect`. Both follow the same interface + `*Impl` convention as the repositories — the interfaces exist so `StopBoardViewModel` can be built on the JVM, since both implementations need a `Context`. Register the `*Impl` in `AppModule` and depend on the interface.
- OLED burn-in protection via `ui/pixelshift/PixelShift.kt`, configured by `BurnInProtectionLevel`. The offsets and timing come from the UI-free `PixelShiftSchedule`, so another platform can shift by the same rules.
