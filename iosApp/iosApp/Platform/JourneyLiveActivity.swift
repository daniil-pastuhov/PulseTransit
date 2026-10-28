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
        let line = progress.lineName ?? ""
        let target = progress.targetName ?? ""
        let (headline, symbol): (String, String) =
            switch progress.phase {
            case .waiting: (L("journey_progress_waiting", line, target), "clock")
            case .riding: (L("journey_progress_riding", line, target), "tram.fill")
            case .walking: (L("journey_progress_walking", target), "figure.walk")
            case .arrived: (L("journey_progress_arrived", progress.destinationName), "flag.checkered")
            }
        let detail = [
            progress.stopsLeft.map { L("journey_progress_stops_left", Int($0.int32Value)) },
            progress.arrivalAtMillis.map { L("journey_progress_arrival", clock.clockText(epochMillis: $0.int64Value).time) },
            progress.locationLost ? L("journey_progress_location_lost") : nil,
            progress.muted ? L("journey_progress_muted") : nil,
        ].compactMap { $0 }.joined(separator: " · ")
        let nextAt = progress.phase == .arrived ? nil : progress.nextAtMillis.map { Date(timeIntervalSince1970: Double($0.int64Value) / 1000) }
        return JourneyActivityAttributes.ContentState(headline: headline, detail: detail, nextAt: nextAt, symbol: symbol)
    }
}
