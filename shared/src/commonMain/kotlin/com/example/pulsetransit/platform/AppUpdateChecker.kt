package com.example.pulsetransit.platform

/** A version of the app in the store that is newer than the one running. */
data class AppUpdate(
    /** Identifies the offered version, so an update the user dismissed stays dismissed. */
    val version: String,
    /**
     * The store page to open to install it. Null on Android, where the app starts Play's own
     * update flow instead of sending the user to the store.
     */
    val storeUrl: String?,
)

/**
 * Asks the app store whether a newer version of the app can be installed.
 *
 * Implemented per platform: `PlayAppUpdateChecker` on Play's In-App Updates, and
 * `AppStoreUpdateChecker` on the App Store's lookup API for iOS.
 */
interface AppUpdateChecker {
    /**
     * The update the store offers, or null when the app is up to date or the store can't tell
     * (offline, or a build that was not installed from the store). Never throws.
     */
    suspend fun availableUpdate(): AppUpdate?
}
