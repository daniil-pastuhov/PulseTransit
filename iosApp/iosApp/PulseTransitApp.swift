import Shared
import SwiftUI

@main
struct PulseTransitApp: App {
    init() {
        // Before launch finishes, so a notification action that launched the app reaches it.
        JourneyNotifications.shared.register()
        #if DEBUG
        seedSettingsFromOwnDefaults()
        #endif
        let clockFormatter = SystemClockFormatter()
        PulseTransitIos.shared.start(
            crashReporting: CrashReporting.start(),
            clockFormatter: clockFormatter,
            locationSource: CoreLocationSource(),
            lightSource: EstimatedLightSource(),
            cameraFrameSource: FrontCamera.shared,
            journeyLocationSource: CoreLocationSource(backgroundUpdates: true),
            journeyNotifier: JourneyNotifications.shared,
            journeyPermissions: JourneyPermissionsSource.shared,
            watchSource: GarminWatch.shared,
            watchWording: GarminWatchWording(clock: clockFormatter),
            uiTest: Self.launchFlag("PTUITest"),
            uiTestReset: Self.launchFlag("PTUITestReset")
        )
        // The UI tests have no watch, and must not be asked for Bluetooth.
        if !Self.launchFlag("PTUITest") { GarminWatch.shared.activate() }
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
                // Garmin Connect's answer to "Connect Garmin watch".
                .onOpenURL { GarminWatch.shared.handle(url: $0) }
        }
    }
}
