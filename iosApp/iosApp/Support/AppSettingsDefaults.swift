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
/// `simctl spawn defaults write <bundle>`, which writes the app's own defaults; a process outside the
/// app cannot reach the App Group's. With the flag, those values replace the stored ones at launch.
func seedSettingsFromOwnDefaults() {
    guard UserDefaults.standard.bool(forKey: "PTSeedSettings"), appSettingsDefaults !== UserDefaults.standard,
          let bundleId = Bundle.main.bundleIdentifier,
          let own = UserDefaults.standard.persistentDomain(forName: bundleId) else { return }
    for (key, value) in own { appSettingsDefaults.set(value, forKey: key) }
}
#endif
