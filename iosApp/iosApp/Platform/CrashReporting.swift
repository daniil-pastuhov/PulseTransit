import FirebaseCore
import FirebaseCrashlytics
import Foundation

/// Firebase Crashlytics, the iOS counterpart of the Android app's crash reporting.
enum CrashReporting {
    /// Configures Firebase when the app has a GoogleService-Info.plist and returns whether it did.
    /// Without the file (a fresh checkout, a fork) the app runs as before, just without reports.
    static func start() -> Bool {
        guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else { return false }
        FirebaseApp.configure()
        // Crashes from development builds would drown out real ones, as on Android, where debug
        // builds turn collection off.
        #if DEBUG
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(false)
        #else
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(true)
        #endif
        return true
    }
}
