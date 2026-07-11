# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

PulseTransit — an Android app (Kotlin, Jetpack Compose) that shows a live public-transport departure board for Norwegian stops using the Entur APIs, plus weather from MET Norway. It is designed to run as an always-on wall display, so a lot of the code is about burn-in protection, sensor-driven screen wake/dim, and adaptive refresh rates.

## Commands

Requires JDK 17 (Gradle toolchain resolves it automatically via foojay). Single module: `:app`.

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected device/emulator
./gradlew test                   # local unit tests
./gradlew test --tests "com.example.pulsetransit.ui.main.MainScreenViewModelTest"  # single test class
./gradlew connectedAndroidTest   # instrumented tests (needs device/emulator)
./gradlew lint                   # Android lint
```

**Known issue:** the tests in `app/src/test` and `app/src/androidTest` are stale template leftovers referencing deleted classes (`DataRepository`, `MainScreenViewModel`), so `./gradlew test` fails to compile until they are rewritten against the real ViewModels.

## Architecture

MVVM with Koin for DI. All dependency wiring lives in `di/AppModule.kt`; ViewModels are obtained in composables via `koinViewModel()`. When adding a repository or ViewModel, register it there.

**Navigation** uses androidx **Navigation 3** (`NavDisplay` + `rememberNavBackStack`), not the classic Navigation component. `Navigation.kt` defines the entry provider; route keys (`Main`, `Settings`) are `NavKey` objects in `NavigationKeys.kt`. Navigation is done by mutating the back stack (`backStack.add(...)` / `removeLastOrNull()`).

**Data layer** (`data/`) follows an interface + `*Impl` pair convention:

- `EnturRepositoryImpl` talks to two Entur services via a shared Ktor `HttpClient`: the Geocoder v3 REST API (stop search / nearby stops) and the Journey Planner v3 GraphQL API (departures, quays, trips). GraphQL queries are raw strings POSTed with kotlinx.serialization — there is no GraphQL client library. Every Entur request must send the `ET-Client-Name` header.
- `WeatherRepositoryImpl` calls `api.met.no` locationforecast, which requires a `User-Agent` header.
- `SettingsRepositoryImpl` is the single persistence mechanism: synchronous SharedPreferences, with complex objects (favorites, destination station) stored as kotlinx.serialization JSON strings. There is no database or DataStore.
- Shared domain models live in `data/Models.kt`; API response DTOs are private nested classes inside each repository impl and never leak out.
- Repositories swallow exceptions and return empty lists / null, so API failures are silent — keep this in mind when debugging "no data" symptoms.

**State**: each ViewModel exposes a single `UiState` data class through one `MutableStateFlow`. Settings are not reactive — screens call `viewModel.loadSettings()` from a `LaunchedEffect` on navigation entry to re-read SharedPreferences, which is how changes made on the Settings screen reach the main board.

**Always-on display machinery** (mostly wired through `StopBoardViewModel`):

- Auto-refresh loop with a dynamic interval: base interval multiplied ×5 during 00–05h and ×2 while the screen is dimmed (`calculateNextRefreshInterval`). Weather refreshes on its own 30-minute cadence inside the same loop.
- Screen wake/dim driven by sensors: proximity and ambient light via `ui/sensors/SystemSensorManager` (light sensor also drives the `AUTO` theme mode), camera-based motion detection via CameraX in `ui/sensors/CameraMotionDetector`.
- OLED burn-in protection via `ui/pixelshift/PixelShift.kt`, configured by `BurnInProtectionLevel`.
