# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

PulseTransit — an Android app (Kotlin, Jetpack Compose), with an iOS app (SwiftUI) on the way, that shows a live public-transport departure board for Norwegian stops using the Entur APIs, plus weather from MET Norway. It is designed to run as an always-on wall display, so a lot of the code is about burn-in protection, sensor-driven screen wake/dim, and adaptive refresh rates.

## Commands

Requires JDK 17 (Gradle toolchain resolves it automatically via foojay). Two modules:

- `:shared` — Kotlin Multiplatform library (Android + `iosArm64`/`iosSimulatorArm64`, framework
  `Shared`): models, repositories, the Room cache, settings, both ViewModels and Koin's
  `sharedModule`. `commonMain` holds everything platform-neutral; `androidMain`/`iosMain` hold the
  platform implementations and each platform's Koin module.
- `:app` — the Android app: Compose UI, theme, navigation, `MainActivity`. It depends on `:shared`.
- `iosApp/` — the iOS app (SwiftUI, iOS 15+), an Xcode project, not a Gradle module. Its
  "Compile Kotlin Framework" build phase runs `scripts/build-shared-framework.sh`, which builds
  `Shared` with `:shared:embedAndSignAppleFrameworkForXcode`.

Every build needs `app/google-services.json` (Firebase Crashlytics); without it the
`processDebugGoogleServices` task fails before anything compiles. See "Firebase setup" in README.MD.
Crash collection is off in debug builds by design, via the `crashlyticsCollectionEnabled` manifest
placeholder set per build type — a debug build reporting nothing is expected, not a broken integration.

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected device/emulator
./gradlew :shared:testAndroidHostTest    # unit tests (commonTest) on the JVM
./gradlew :shared:iosSimulatorArm64Test   # the same tests on the iOS simulator (macOS + Xcode)
./gradlew :shared:allTests                # both of the above
./gradlew :shared:testAndroidHostTest --tests "*WeatherRepositoryImplTest*"  # single test class
./gradlew connectedDebugAndroidTest :shared:connectedAndroidDeviceTest  # instrumented tests (needs device/emulator)
./gradlew lintDebug :shared:lint          # Android lint
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 17' build  # iOS app (also builds Shared)
```

**Releases** are cut with `scripts/release`, not by pushing to main: it bumps `versionName` on a
`release/vX.Y.Z` branch and writes `release-notes/vX.Y.Z/<play-locale>.txt`. CI publishes pushes to
`release/v*` to Play internal (copying the notes into GPP's git-ignored
`app/src/main/play/release-notes/` via `scripts/stage-release-notes.sh`), and on the PR's merge
promotes that release to closed testing and tags `vX.Y.Z`. Main publishes nothing. See "Releasing"
in README.MD.

**Tests.** The unit tests live in `shared/src/commonTest` and use `kotlin.test` (no JUnit), so they
run on both the JVM and the iOS simulator. Kotlin/Native rejects some characters in function names,
so backticked test names must not contain commas. They cover the offline cache and the MET caching
contract with Ktor's `MockEngine` and in-memory fake DAOs (`FakeCacheDaos.kt`); time helpers that
format like Entur and MET are in `TestTime.kt`. Those fakes reproduce the DAO queries by hand, so the
real SQL is pinned separately by `shared/src/androidDeviceTest` (`CacheDatabaseTest`) against an
in-memory Room database. Its body is `CacheDatabaseContract` in `shared/src/cacheContractTest`, a
source dir compiled into both `androidDeviceTest` and `iosTest`, so the same queries are checked on
Android's framework SQLite and on the bundled driver iOS uses (`CacheDatabaseIosTest`). It is kept out
of `commonTest` because the device test APK cannot dex sentence-style test names below API 30. If
you change a `@Query`, those tests are what catch it. The settings migration is pinned in
`androidDeviceTest` too (`SettingsMigrationTest`).

`StopBoardViewModelTest` covers the auto-theme hysteresis, offline staleness, grouping and
auto-dim against fakes (`ui/viewmodel/Fakes.kt`). Two things about it are load-bearing: the
ViewModel's clock and auto-refresh are `while (true)` loops on the test dispatcher, so
`advanceUntilIdle()` never returns — use `runCurrent()`/`advanceTimeBy()` — and every test runs
through the `boardTest` helper, which cancels `viewModelScope` afterwards and gives `runTest` a 10 s
timeout. Without that cancellation runTest cannot drain the scheduler and the whole suite stalls
until it times out.

## Architecture

MVVM with Koin for DI. Wiring is split in two: `sharedModule` (`shared/src/commonMain/.../di/SharedModule.kt`) registers the HTTP client, DAOs, repositories and ViewModels; each platform supplies what they need — the HTTP engine, `Settings`, `CacheDatabase` and the `platform/`/`ui/sensors/` implementations — in `androidPlatformModule` (`androidMain`) or `iosPlatformModule` (`iosMain`). `PulseTransitApplication` starts `sharedModule + androidPlatformModule`. ViewModels are obtained in composables via `koinViewModel()`. A new repository or ViewModel goes in `sharedModule`; a new platform service gets an interface in `commonMain` and an implementation in each platform module.

**Navigation** uses androidx **Navigation 3** (`NavDisplay` + `rememberNavBackStack`), not the classic Navigation component. `Navigation.kt` defines the entry provider; route keys (`Main`, `Settings`) are `NavKey` objects in `NavigationKeys.kt`. Navigation is done by mutating the back stack (`backStack.add(...)` / `removeLastOrNull()`).

**Data layer** (`data/`) follows an interface + `*Impl` pair convention:

- `EnturRepositoryImpl` talks to two Entur services via a shared Ktor `HttpClient`: the Geocoder v3 REST API (stop search / nearby stops) and the Journey Planner v3 GraphQL API (departures, quays, trips). GraphQL queries are raw strings POSTed with kotlinx.serialization — there is no GraphQL client library. Every Entur request must send the `ET-Client-Name` header.
- `WeatherRepositoryImpl` calls `api.met.no` locationforecast, which requires a `User-Agent` header.
- `SettingsRepositoryImpl` persists all *settings* through a synchronous multiplatform-settings `Settings` (SharedPreferences file `app_settings` on Android, built by `createAppSettings` in `data/AndroidSettings.kt`). Complex objects (favorites, destination stations) and string sets (filtered lines, selected quays) are stored as kotlinx.serialization JSON strings, because NSUserDefaults has no string-set type; `migrateLegacyStringSets` converts sets written by older builds once at startup. There is no DataStore.
- `data/cache/` is a Room KMP database (`CacheDatabase`, built per platform by `cacheDatabaseBuilder` and finished by `buildCacheDatabase()`; schema history in `shared/schemas/`) holding **only** the offline cache of network data — departures, fastest-route trips, and the MET forecast. It is disposable derived data, never a source of truth, so schema changes drop the tables rather than migrate. Settings do not live here.
- No JVM or Android APIs in `commonMain` — the compiler enforces this, since it also builds for iOS: time is `kotlin.time.Clock`/`Instant` plus kotlinx-datetime (core library desugaring covers API < 26), HTTP dates use Ktor's `fromHttpToGmtDate`, and the `HttpClient` comes from `createHttpClient(engine)`. Anything that genuinely needs the platform sits behind an interface in `platform/` — `ClockFormatter` (localized clock strings), `LocationProvider` — with the Android implementation in `androidMain`, registered in `androidPlatformModule`. UI state carries no `R.string` ids; e.g. `SettingsViewModel.LocationStatus` is a sealed type the screen maps to strings.
- Shared domain models live in `data/Models.kt`; API response DTOs are private nested classes inside each repository impl and never leak out.
- Repositories still swallow exceptions rather than throwing, but the cached calls (`getDepartures`, `getFastestTrip`, `getWeather`) no longer return bare empty lists on failure: they return `Sourced<T>`, falling back to the last good cached response and reporting when it was fetched via `Sourced.fetchedAtMillis`. Only when nothing is cached is the result empty — keep this in mind when debugging "no data" symptoms. Un-cached calls (stop search, nearby, quays) do still fail silently to empty.
- `WeatherRepositoryImpl` must honour MET's terms: coordinates truncated to 4 decimals, no repeat request before the `Expires` time, and revalidation with `If-Modified-Since`/`304`. The cache is what implements this, so don't bypass it.

**iOS app** (`iosApp/iosApp/`). The Xcode project uses synchronized folders, so new Swift files are
picked up without editing `project.pbxproj`; versions and the bundle id live in
`iosApp/Configuration/Config.xcconfig`. The deployment target is iOS 15, so no `@Observable` and no
`NavigationStack`: screen models are `ObservableObject`s, and newer APIs go behind `#available`.
The Swift side reaches Kotlin through SKIE, which turns a `StateFlow` into an `AsyncSequence` and
sealed or enum types into exhaustive Swift enums (`onEnum(of:)`).
- `PulseTransitIos.start(...)` (`shared/src/iosMain/.../di/PulseTransitIos.kt`) starts Koin, once at
  launch, with the services Swift implements. Swift cannot implement a `suspend fun` or produce a
  `Flow`, so those services are callback-style ports (`LocationSource`, `LightSource`,
  `MotionSource` in `iosMain/.../platform/IosPorts.kt`) that small adapters turn into the shared
  interfaces. `ClockFormatter` has neither, so Swift implements it directly.
- Each screen gets its ViewModel through a `ViewModelOwner`, and **must call `owner.clear()` when the
  screen goes away** (`ScreenModel` in `iosApp/iosApp/Support/ScreenModel.swift` does it in `deinit`). Only that cancels `viewModelScope`; without it
  the board's clock and refresh loops keep running for the life of the app.
- **Strings:** the Android `values*/strings.xml` files are the source of truth for both apps. After
  editing them, run `scripts/sync-ios-strings.py` and commit the regenerated
  `iosApp/iosApp/Localizable.xcstrings` (CI fails if it is out of date). Swift looks strings up by the
  Android resource name: `L("last_updated", time)`. Info.plist texts live in `InfoPlist.xcstrings`.
- Screens: `Board/` (the board, the counterpart of `MainScreen`/`StopBoardPanel`) and `Settings/`
  (a sheet; the board calls `loadSettings()` when it closes). Colours are Android's tokens in
  `Theme/Palette.swift`. In debug builds, launching with `-PTOpenSettings YES` opens Settings
  directly, for simulator checks without tapping.
- Logic a SwiftUI view would otherwise reimplement belongs in `commonMain` (e.g.
  `departureCountdown`, `PixelShiftSchedule`, `parseWeatherSymbol`), so both apps share one tested
  version. Kotlin `Duration` values and default arguments do not reach Swift usefully, so such APIs
  also offer millisecond properties and explicit overloads (see `PixelShiftSchedule`).

**State**: each ViewModel exposes a single `UiState` data class through one `MutableStateFlow`. Settings are not reactive — screens call `viewModel.loadSettings()` from a `LaunchedEffect` on navigation entry to re-read SharedPreferences, which is how changes made on the Settings screen reach the main board.

**Always-on display machinery** (mostly wired through `StopBoardViewModel`):

- Auto-refresh loop with a dynamic interval: base interval multiplied ×5 during 00–05h and ×2 while the screen is dimmed (`calculateNextRefreshInterval`). Weather refreshes on its own 30-minute cadence inside the same loop.
- Screen wake/dim driven by sensors: ambient light via `ui/sensors/SystemSensorManager` (which also drives the `AUTO` theme mode), camera-based motion detection via CameraX in `ui/sensors/CameraMotionDetector`. `motionEvents()` is cold — the camera is open exactly while it is collected — and `MainScreen` drives it through `startCameraMotion()`/`stopCameraMotion()` from a `LifecycleStartEffect`. Both follow the same interface + `*Impl` convention as the repositories — the interfaces exist so `StopBoardViewModel` can be built on the JVM, since both implementations need a `Context`. The interfaces are in `commonMain`; register the `*Impl` in `androidPlatformModule` and depend on the interface.
- OLED burn-in protection via `ui/pixelshift/PixelShift.kt` (in `:app`), configured by `BurnInProtectionLevel`. The offsets and timing come from the UI-free `PixelShiftSchedule`, so another platform can shift by the same rules.
