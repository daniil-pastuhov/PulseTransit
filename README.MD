# PulseTransit

[![CI](https://github.com/daniil-pastuhov/PulseTransit/actions/workflows/ci.yml/badge.svg?event=pull_request)](https://github.com/daniil-pastuhov/PulseTransit/actions/workflows/ci.yml)

PulseTransit is an Android application designed to show public transport stop boards.

## Features

- Display real-time transport information.
- Jetpack Compose based UI.
- Dependency injection with Koin.
- Networking with Ktor.
- Camera support using CameraX.
- Location services.

## Tech Stack

- **Shared code**: [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) — the `shared` module (data, offline cache, settings, ViewModels) builds for Android and iOS
- **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
- **Offline cache**: [Room](https://developer.android.com/kotlin/multiplatform/room) (multiplatform)
- **Dependency Injection**: [Koin](https://insert-koin.io/)
- **Networking**: [Ktor](https://ktor.io/)
- **Serialization**: [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)
- **Navigation**: [Navigation 3](https://developer.android.com/guide/navigation/navigation-getting-started)
- **Camera**: [CameraX](https://developer.android.com/training/camerax)

## Getting Started

1. Clone the repository.
2. Add `app/google-services.json` — see [Firebase setup](#firebase-setup) below. **The build fails without it.**
3. Open the project in Android Studio.
4. Build and run the `app` module.

### iOS

Requires Xcode (iOS 15 deployment target) and a JDK on the machine; the Xcode build calls Gradle to
compile the shared Kotlin framework. Open `iosApp/iosApp.xcodeproj` and run the `iosApp` scheme, or
from the command line:

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 17' build
```

The iOS app is in progress: the board, settings and location work; camera motion wake and the ambient-light estimate for the auto theme are still to come.

## Firebase setup

Crash reporting uses [Firebase Crashlytics](https://firebase.google.com/docs/crashlytics), which needs a
config describing the Firebase project to report into: `app/google-services.json` for Android and
`iosApp/iosApp/GoogleService-Info.plist` for iOS. Both are committed, because CI needs them to build. They
hold no secret — the API keys in them ship inside every APK and IPA anyway, and Google
[documents them as safe to check in](https://firebase.google.com/docs/projects/learn-more#config-files-objects).
The keys are restricted in Google Cloud to this app's package and bundle id, so they cannot be used from
anywhere else.

**In a fork**, point the app at your own Firebase project instead of sending crashes into this one:

1. Change `applicationId` in `app/build.gradle.kts` and `PRODUCT_BUNDLE_IDENTIFIER` in
   `iosApp/Configuration/Config.xcconfig` to ids of your own.
2. Create a project in the [Firebase console](https://console.firebase.google.com/) and add an **Android**
   app with exactly that `applicationId` (Firebase rejects the config at build time otherwise) and an
   **iOS** app with that bundle id.
3. Download the generated files over `app/google-services.json` and `iosApp/iosApp/GoogleService-Info.plist`.

Without `google-services.json` every Android build fails with
`Execution failed for task ':app:processDebugGoogleServices'. > File google-services.json is missing.`
The iOS app builds without its plist and simply skips Crashlytics.

Crash collection is **disabled in debug builds** and enabled in release, so day-to-day development does not
report into the dashboard. That switch is the `crashlyticsCollectionEnabled` manifest placeholder set per
build type in `app/build.gradle.kts`; release builds also upload their ProGuard mapping file, without which
minified stack traces are unreadable.

To confirm the pipeline works end to end, force a crash from a **release** build — a debug build reports
nothing by design — and it should appear in the console within a couple of minutes.

## Publishing to Google Play

The app is published as `com.homieapps.pulsetransit`, in Norway only, with English and Norwegian
store listings.

- **Store texts** live in `app/src/main/play/listings/` (`en-US`, `no-NO`), and the "What's new"
  text of each release in `release-notes/vX.Y.Z/`. CI pushes them with every release, so edit them
  here rather than in Play Console, or the next release overwrites your Console edits. Graphics, country
  availability, the content rating, Data safety and other policy declarations are managed in Play
  Console.
- **Releases**: see [Releasing](#releasing). CI uploads a signed AAB, with the CI run number plus
  `BUILD_NUMBER_BASE` (`.github/workflows/ci.yml`) as its `versionCode`, via
  [Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher).
- **Secrets** (GitHub `Release` environment): `PLAY_SERVICE_ACCOUNT_JSON` holds the service account
  key. The upload step is skipped while the secret is missing.
- **Release status**: releases go out to testers as `completed`. Set the repository variable
  `PLAY_RELEASE_STATUS=draft` to leave the rollout to Play Console instead.

To upload from your machine instead:

```bash
./gradlew publishReleaseBundle -PpulsePlayCredentials=/path/to/service-account.json -PpulseVersionCode=<n>
```

## Releasing

Merging to `main` publishes nothing; CI only checks and builds the PRs into it (only the app a PR
can affect, by path). A release goes out like this:

1. **Start it**: `scripts/release` (add `--dry-run` to try it without changing anything). It
   lists the commits on `origin/main` since the last `vX.Y.Z` tag and asks for a patch, minor or
   major bump. Then it has the Claude CLI write the "What's new" notes in English and Norwegian
   for you to accept, edit or regenerate. It creates `release/vX.Y.Z` with the new `versionName`
   (and the iOS `MARKETING_VERSION`) and `release-notes/vX.Y.Z/<locale>.txt`, pushes it and opens
   a PR.
2. **Internal testing**: CI publishes every push to `release/v*` to the Play internal track, with
   those notes and the store listing, and uploads an iOS build to TestFlight for internal testers
   (`.github/workflows/ci.yml`, fastlane lane `beta`). Fixes for the release go on the branch;
   each push becomes a new internal build on both.
3. **Closed testing**: merging the PR promotes the internal release to closed testing (`alpha`) and
   tags `vX.Y.Z`, with a GitHub release; on iOS it submits the tested TestFlight build for App
   Store review (lane `release`), and the version goes live as soon as Apple approves it. Its
   "What's New" is the en-US / no-NO notes, from the app's second App Store version on (App Store
   Connect has no such field on the first). To submit a version whose merge did not, run CI by
   hand: `gh workflow run ci.yml -f app_store_version=X.Y.Z`; that run does nothing else.

Needs `gh` (logged in) and `claude` on your PATH. Play allows 500 characters of notes per
language, and the script checks that.

### TestFlight setup (once)

The iOS jobs need, in the repository's `Release` environment:

| Secret | Value |
|---|---|
| `ASC_KEY_ID` | The App Store Connect API key's Key ID |
| `ASC_ISSUER_ID` | The Issuer ID shown above the keys list |
| `ASC_KEY_P8` | The contents of the key's `.p8` file |

Create the key under App Store Connect → Users and Access → Integrations → App Store Connect API as
a **team key with the Admin role**: Admin is what lets Xcode create the distribution certificate and
profile on the CI machine (cloud-managed signing), so no certificates are kept in the repository.
Without `ASC_KEY_P8` the TestFlight upload is skipped, as the Play upload is without its key.

App Store Connect already has the app record (`com.homieapps.pulsetransit`), the TestFlight test
information, the age rating and an internal group, "Internal testers", that receives every
processed build automatically; add people to it there. The app declares only exempt encryption
(HTTPS), so builds skip the export compliance question.

The `release` lane submits the version as App Store Connect has it: the listing, screenshots,
App Review notes and attachment, pricing, App Privacy answers and content rights declaration must
already be filled in there (the first time by hand or with the lanes and script below). It does
not upload the listing texts, because deliver's metadata upload also deletes the App Review
attachment. A version still in review blocks the submission of the next one.

The App Store listing (name, subtitle, description, keywords, promotional text, URLs, categories,
copyright) lives in `iosApp/fastlane/metadata`, in English (UK, the app's primary language) and
Norwegian, adapted from the Play listing. `scripts/update-ios-store-text.rb` (with the `ASC_*`
variables set) pushes the texts and App Review notes to the editable version field by field, which
leaves the App Review attachment in place. `bundle exec fastlane metadata` (from `iosApp/`) pushes
everything, URLs and categories too, but deletes that attachment. Builds are numbered
like Android's `versionCode`: the CI run number plus `BUILD_NUMBER_BASE`.

Crash reports use `iosApp/iosApp/GoogleService-Info.plist` (the Firebase project's iOS app), and the
`beta` lane uploads the dSYM to Crashlytics. Collection is off in Debug builds, as on Android.

### App Store screenshots and previews

`iosApp/fastlane/screenshots` and `iosApp/fastlane/app_previews` hold the store media for the 6.9"
iPhone and 13" iPad, in English (UK) and Norwegian, plus iPhone Duo screenshots (`duo_*`, the
inner display in portrait, 2007×2853). They are made from the running app with live data, so
regenerate them when the UI changes:

1. Build the Debug app for a simulator and boot an iPhone 17 Pro Max and an iPad Pro 13-inch (and
   an iPhone Duo, unfolded in portrait in Device Hub; `<device-name>` is then `duo`).
2. `scripts/capture-ios-screenshots.sh <udid> <PulseTransit.app> <raw-dir> <en-GB|no> <iphone|ipad>`
   seeds a set of Oslo stops and captures On the spot, the board (light and dark) and Settings.
3. `swift scripts/frame-ios-screenshots.swift <raw-dir> iosApp/fastlane/screenshots` adds the
   captions (in the script) on the icon's gradient.
4. `scripts/record-ios-preview.sh <udid> <out.mp4> <locale> <886x1920|1200x1600>` records the
   scripted tour (`-PTDemo YES`), from On the spot to the wall board, as a 29-second preview.
5. `scripts/upload-ios-store-media.rb` (with the `ASC_*` variables) replaces the media on the
   editable App Store version, the iPhone Duo set included. Duo screenshots are optional until
   April 2027, when apps built with the iOS 27.1 SDK must include them; until then they earn the
   "optimized for iPhone Duo" badge.
6. `swift scripts/render-ios-store-header.swift <raw-dir> iosApp/fastlane/creative_assets` renders
   the "Header and Search Results" image (one universal 16:9 PNG per locale, iOS 27 and later).
   It is git-ignored for its size, and uploaded by hand on the version's Header and Search
   Results tab, with "Use header asset in search results" ticked.


## Forking

The code is MIT-licensed (see [LICENSE](LICENSE)), so you are welcome to build on it. A few things belong
to this app rather than to the code, and a fork should replace them:

- **API identification.** Entur [requires every client to identify itself](https://developer.entur.org/pages-intro-authentication)
  with the `ET-Client-Name` header, and MET Norway's [terms of service](https://api.met.no/doc/TermsOfService)
  require a `User-Agent` naming the app and a way to contact its author. Set your own in
  `EnturRepositoryImpl` (`clientNameValue`) and `WeatherRepositoryImpl` (`USER_AGENT`), so that your
  traffic is not attributed to PulseTransit and a misbehaving fork cannot get this app blocked.
- **Firebase**: see [Firebase setup](#firebase-setup).
- **App ids, name and icon.** Publishing a copy under the PulseTransit name or icon, or with this app's
  `applicationId`/bundle id, is not covered by the license. Use your own if you ship to a store.
- **Store metadata** under `app/src/main/play/` and `iosApp/fastlane/` (descriptions, contact email,
  screenshots) describes this app.

## License

[MIT](LICENSE) © 2026 Daniil Pastuhov
