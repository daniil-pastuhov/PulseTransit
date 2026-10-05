# PulseTransit

[![Android CI](https://github.com/daniil-pastuhov/PulseTransit/actions/workflows/android.yml/badge.svg?branch=main&event=push)](https://github.com/daniil-pastuhov/PulseTransit/actions/workflows/android.yml)

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
`google-services.json` describing the Firebase project to report into. It is not in the repository yet, so
until it is added every build fails with:

```
Execution failed for task ':app:processDebugGoogleServices'.
> File google-services.json is missing.
```

To produce it:

1. Create a project in the [Firebase console](https://console.firebase.google.com/) (or open an existing one).
2. Add an **Android** app to it. The package name must be exactly `com.example.pulsetransit`, matching
   `applicationId` in `app/build.gradle.kts` — Firebase rejects the config at build time otherwise.
3. Download the generated `google-services.json` and put it at `app/google-services.json`.
4. Commit it. It holds no secret: the API key in it is restricted to this app, and Google
   [documents it as safe to check in](https://firebase.google.com/docs/projects/learn-more#config-files-objects).
   CI needs it in the repository to build.

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
- **Releases**: see [Releasing](#releasing). CI uploads a signed AAB, with the workflow run number
  as its `versionCode`, via [Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher).
- **Secrets** (GitHub `Release` environment): `PLAY_SERVICE_ACCOUNT_JSON` holds the service account
  key. The upload step is skipped while the secret is missing.
- **Release status**: releases go out to testers as `completed`. Set the repository variable
  `PLAY_RELEASE_STATUS=draft` to leave the rollout to Play Console instead.

To upload from your machine instead:

```bash
./gradlew publishReleaseBundle -PpulsePlayCredentials=/path/to/service-account.json -PpulseVersionCode=<n>
```

## Releasing

Merging to `main` publishes nothing; CI only checks and builds it. A release goes out like this:

1. **Start it**: `scripts/release` (add `--dry-run` to try it without changing anything). It
   lists the commits on `origin/main` since the last `vX.Y.Z` tag and asks for a patch, minor or
   major bump. Then it has the Claude CLI write the "What's new" notes in English and Norwegian
   for you to accept, edit or regenerate. It creates `release/vX.Y.Z` with the new `versionName`
   (and the iOS `MARKETING_VERSION`) and `release-notes/vX.Y.Z/<locale>.txt`, pushes it and opens
   a PR.
2. **Internal testing**: CI publishes every push to `release/v*` to the Play internal track, with
   those notes and the store listing, and uploads an iOS build to TestFlight for internal testers
   (`.github/workflows/ios.yml`, fastlane lane `beta`). Fixes for the release go on the branch;
   each push becomes a new internal build on both.
3. **Closed testing**: merging the PR promotes the internal release to closed testing (`alpha`) and
   tags `vX.Y.Z`, with a GitHub release; on iOS it sends the tested TestFlight build to the
   external testers group (lane `promote`).

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

External testing is off until you want it. To turn it on, create an external group, add a phone
number to the Beta App Review contact (Apple requires one), and set the repository variable
`TESTFLIGHT_EXTERNAL_GROUP` to the group's name: merging a release PR then sends its build there.

The App Store listing (name, subtitle, description, keywords, promotional text, URLs, categories,
copyright) lives in `iosApp/fastlane/metadata`, in English (UK, the app's primary language) and
Norwegian, adapted from the Play listing. `bundle exec fastlane metadata` (from `iosApp/`, with the
`ASC_*` variables set) pushes it. Until the app's first App Store release, fastlane ends that lane
with a "No data" error (fastlane#20538) after the texts have been saved. Builds are numbered
by the iOS workflow's run number.

Crash reports use `iosApp/iosApp/GoogleService-Info.plist` (the Firebase project's iOS app), and the
`beta` lane uploads the dSYM to Crashlytics. Collection is off in Debug builds, as on Android.

### App Store screenshots and previews

`iosApp/fastlane/screenshots` and `iosApp/fastlane/app_previews` hold the store media for the 6.9"
iPhone and 13" iPad, in English (UK) and Norwegian. They are made from the running app with live
data, so regenerate them when the UI changes:

1. Build the Debug app for a simulator and boot an iPhone 17 Pro Max and an iPad Pro 13-inch.
2. `scripts/capture-ios-screenshots.sh <udid> <PulseTransit.app> <raw-dir> <en-GB|no> <iphone|ipad>`
   seeds a set of Oslo stops and captures the board (light and dark) and Settings.
3. `swift scripts/frame-ios-screenshots.swift <raw-dir> iosApp/fastlane/screenshots` adds the
   captions (in the script) on the icon's gradient.
4. `scripts/record-ios-preview.sh <udid> <out.mp4> <locale> <886x1920|1200x1600>` records the
   scripted tour (`-PTDemo YES`) as a 29-second preview.
5. `scripts/upload-ios-store-media.rb` (with the `ASC_*` variables) replaces the media on the
   editable App Store version.

