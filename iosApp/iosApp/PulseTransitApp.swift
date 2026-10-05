import Shared
import SwiftUI

@main
struct PulseTransitApp: App {
    init() {
        // Location, ambient light and camera motion get real implementations in later phases;
        // until then the board runs with them switched off.
        PulseTransitIos.shared.start(
            clockFormatter: SystemClockFormatter(),
            locationSource: UnavailableLocationSource(),
            lightSource: SilentLightSource(),
            motionSource: SilentMotionSource()
        )
    }

    var body: some Scene {
        WindowGroup {
            StopBoardView()
        }
    }
}
