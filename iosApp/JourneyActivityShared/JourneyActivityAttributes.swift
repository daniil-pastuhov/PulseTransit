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
        /// Stops left, arrival time and any notes: "2 stops left · Arrives 09:31".
        var detail: String
        /// The current step's next moment (departure, or arrival), counted down to.
        var nextAt: Date?
        /// An SF Symbol for the step: walking, waiting, riding or arrived.
        var symbol: String
    }

    /// "Journey to Home".
    var title: String
}
