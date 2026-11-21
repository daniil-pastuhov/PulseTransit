import SwiftUI

/// PulseTransit on Apple Watch: the journey followed on the iPhone, live. The counterpart of the
/// Garmin watch app (garminApp/), from the same messages.
@main
struct PulseTransitWatchApp: App {
    @StateObject private var journey = WatchJourney()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(journey)
                .onAppear { journey.activate() }
        }
    }
}
