import ActivityKit
import Foundation

/// A followed journey's Live Activity, shared by the app, which starts and updates it, and the
/// JourneyActivity widget extension, which draws it. The app sends texts already localized, so the
/// extension needs no strings and no Kotlin.
@available(iOS 16.1, *)
struct JourneyActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        /// What is happening now: "On 5 to Majorstuen".
        var headline: String
        /// The step's facts, most important first: "Next stop: Stortinget · 2 stops left · Then 20 at 09:18".
        var detail: String
        /// The current step's next moment (departure, or arrival), counted down to.
        var nextAt: Date?
        /// An SF Symbol for the step: walking, waiting, riding or arrived.
        var symbol: String
        /// When the journey starts and arrives, for a progress bar that fills by itself. Optional, so
        /// an activity started by an older build still decodes.
        var startAt: Date?
        var endAt: Date?
        /// The journey leg by leg, for a bar in segments as Android 16 draws it: rides in the accent,
        /// walks and waits muted. Optional, so an activity started by an older build still decodes.
        var segments: [Segment]?
    }

    /// One walk, wait or ride on the bar, filling by itself between its times.
    struct Segment: Codable, Hashable {
        var startAt: Date
        var endAt: Date
        var isRide: Bool
    }

    /// "Journey to Home".
    var title: String
}
