import Shared
import SwiftUI

@main
struct PulseTransitApp: App {
    init() {
        // Before launch finishes, so a notification action that launched the app reaches it.
        JourneyNotifications.shared.register()
        PulseTransitIos.shared.start(
            crashReporting: CrashReporting.start(),
            clockFormatter: SystemClockFormatter(),
            locationSource: CoreLocationSource(),
            lightSource: EstimatedLightSource(),
            cameraFrameSource: FrontCamera.shared,
            journeyLocationSource: CoreLocationSource(backgroundUpdates: true),
            journeyNotifier: JourneyNotifications.shared,
            journeyPermissions: JourneyPermissionsSource.shared,
            uiTest: Self.launchFlag("PTUITest"),
            uiTestReset: Self.launchFlag("PTUITestReset")
        )
    }

    /// A debug-only launch argument (`-PTUITest YES`); always false in release builds.
    private static func launchFlag(_ name: String) -> Bool {
        #if DEBUG
        UserDefaults.standard.bool(forKey: name)
        #else
        false
        #endif
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}
