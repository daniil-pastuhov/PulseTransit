import Shared
import SwiftUI

@main
struct PulseTransitApp: App {
    init() {
        // Ambient light and camera motion get real implementations in a later phase; until then
        // the board runs with them switched off.
        PulseTransitIos.shared.start(
            clockFormatter: SystemClockFormatter(),
            locationSource: CoreLocationSource(),
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
