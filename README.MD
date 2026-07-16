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

- **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
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
