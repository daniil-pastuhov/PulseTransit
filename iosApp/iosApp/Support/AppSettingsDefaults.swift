import Foundation
import Shared

/// The defaults the shared code keeps settings in (`AppGroup.kt`): the App Group's, shared with the
/// widgets, when the build has the group; else the app's own.
let appSettingsDefaults: UserDefaults = {
    guard FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: AppGroupKt.APP_GROUP_ID) != nil else { return .standard }
    return UserDefaults(suiteName: AppGroupKt.APP_GROUP_ID) ?? .standard
}()

#if DEBUG
/// `-PTSeedSettings YES`: the screenshot and preview scripts seed settings with
/// `simctl spawn defaults write <bundle>`, which writes the app's own defaults, and as launch
/// arguments (`-favorites …`); a process outside the app cannot reach the App Group's. With the flag,
/// both replace the stored values at launch, so the widgets, which see neither, read them too. On a
/// fresh simulator `defaults write` may not reach the app's defaults, so the scripts pass every
/// setting as an argument as well.
func seedSettingsFromOwnDefaults() {
    guard UserDefaults.standard.bool(forKey: "PTSeedSettings"), appSettingsDefaults !== UserDefaults.standard else { return }
    if let bundleId = Bundle.main.bundleIdentifier, let own = UserDefaults.standard.persistentDomain(forName: bundleId) {
        for (key, value) in own { appSettingsDefaults.set(value, forKey: key) }
    }
    // The launch arguments' own keys, minus the system's (-AppleLanguages) and the debug flags (-PT…).
    let arguments = UserDefaults.standard.volatileDomain(forName: UserDefaults.argumentDomain)
    for (key, value) in arguments where !key.hasPrefix("Apple") && !key.hasPrefix("NS") && !key.hasPrefix("PT") {
        appSettingsDefaults.set(value, forKey: key)
    }
}
#endif
