import Shared
import SwiftUI

@main
struct PulseTransitApp: App {
    init() {
        PulseTransitIos.shared.start(
            crashReporting: CrashReporting.start(),
            clockFormatter: SystemClockFormatter(),
            locationSource: CoreLocationSource(),
            lightSource: EstimatedLightSource(),
            cameraFrameSource: FrontCamera.shared
        )
    }

    var body: some Scene {
        WindowGroup {
            StopBoardView()
        }
    }
}
