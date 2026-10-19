# Security policy

## Reporting a vulnerability

Please report security issues privately through GitHub's
[private vulnerability reporting](https://github.com/daniil-pastuhov/PulseTransit/security/advisories/new),
not in a public issue. I aim to reply within a week.

Only the latest release of PulseTransit on Google Play and the App Store receives fixes.

## Not a vulnerability

The Firebase API keys in `app/google-services.json` and `iosApp/iosApp/GoogleService-Info.plist` are
client configuration, not secrets: they ship inside every app build and are restricted in Google Cloud
to this app. See [Firebase setup](README.MD#firebase-setup).
