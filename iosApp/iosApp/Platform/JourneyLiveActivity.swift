import ActivityKit
import Foundation
import Shared

/// The followed journey's Live Activity: started with its first progress, updated as it moves on
/// (only when what it shows changes), and ended with the journey. Updates are local, from the app,
/// so while iOS has the app suspended the countdown still runs but the texts wait for it.
@available(iOS 16.2, *)
final class JourneyLiveActivity {
    static let shared = JourneyLiveActivity()

    /// One a previous run of the app started, picked up again rather than started twice.
    private var activity: Activity<JourneyActivityAttributes>? = Activity<JourneyActivityAttributes>.activities.first
    private var journeyId: String?

    func update(progress: JourneyProgress, clock: ClockFormatter) {
        let state = contentState(progress, clock: clock)
        if let activity, journeyId == nil || journeyId == progress.journeyId {
            journeyId = progress.journeyId
            guard activity.content.state != state else { return }
            Task { await activity.update(ActivityContent(state: state, staleDate: nil)) }
            return
        }
        end()
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        let attributes = JourneyActivityAttributes(title: L("journey_progress_title", progress.destinationName))
        activity = try? Activity.request(attributes: attributes, content: ActivityContent(state: state, staleDate: nil))
        journeyId = progress.journeyId
    }

    func end() {
        if let activity {
            Task { await activity.end(nil, dismissalPolicy: .default) }
        }
        activity = nil
        journeyId = nil
    }

    private func contentState(_ progress: JourneyProgress, clock: ClockFormatter) -> JourneyActivityAttributes.ContentState {
        let headline = JourneyProgressText.headline(progress)
        let symbol =
            switch progress.phase {
            case .waiting: "clock"
            case .riding: "tram.fill"
            case .walking: "figure.walk"
            case .arrived: "flag.checkered"
            }
        let detail = progress.details.map { JourneyProgressText.detail($0, clock: clock) }.joined(separator: " · ")
        let nextAt = progress.phase == .arrived ? nil : progress.nextAtMillis.map { Self.date($0.int64Value) }
        // The bar fills by itself between these, like the countdown.
        let startAt = progress.startAtMillis.map { Self.date($0.int64Value) }
        let endAt = progress.timeline.last.map { Self.date($0.endMillis) }
        let segments = progress.timeline.map { segment in
            JourneyActivityAttributes.Segment(
                startAt: Self.date(segment.startMillis),
                endAt: Self.date(segment.endMillis),
                isRide: segment.kind == .ride
            )
        }
        return JourneyActivityAttributes.ContentState(
            headline: headline, detail: detail, nextAt: nextAt, symbol: symbol, startAt: startAt, endAt: endAt,
            segments: segments.isEmpty ? nil : segments
        )
    }

    private static func date(_ millis: Int64) -> Date {
        Date(timeIntervalSince1970: Double(millis) / 1000)
    }
}
