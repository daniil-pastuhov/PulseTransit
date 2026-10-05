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
   and `release-notes/vX.Y.Z/<locale>.txt`, pushes it and opens a PR.
2. **Internal testing**: CI publishes every push to `release/v*` to the internal track, with those
   notes and the store listing. Fixes for the release go on the branch; each push becomes a new
   internal build.
3. **Closed testing**: merging the PR promotes the internal release to closed testing (`alpha`) and
   tags `vX.Y.Z`, with a GitHub release.

Needs `gh` (logged in) and `claude` on your PATH. Play allows 500 characters of notes per
language, and the script checks that.
