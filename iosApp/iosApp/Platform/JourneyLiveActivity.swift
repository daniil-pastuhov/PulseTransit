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
            case .waiting: (progress.towards.map { L("journey_progress_towards", line, $0) } ?? L("journey_progress_waiting", line, target), "clock")
            case .riding: (L("journey_progress_riding", line, target), "tram.fill")
            case .walking: (L("journey_progress_walking", target), "figure.walk")
            case .arrived: (L("journey_progress_arrived", progress.destinationName), "flag.checkered")
            }
        let detail = progress.details.map { Self.text($0, clock: clock) }.joined(separator: " · ")
        let nextAt = progress.phase == .arrived ? nil : progress.nextAtMillis.map { Self.date($0.int64Value) }
        // The bar fills by itself between these, like the countdown.
        let startAt = progress.startAtMillis.map { Self.date($0.int64Value) }
        let endAt = progress.timeline.last.map { Self.date($0.endMillis) }
        return JourneyActivityAttributes.ContentState(
            headline: headline, detail: detail, nextAt: nextAt, symbol: symbol, startAt: startAt, endAt: endAt
        )
    }

    private static func date(_ millis: Int64) -> Date {
        Date(timeIntervalSince1970: Double(millis) / 1000)
    }

    /// One of the progress' facts, worded like the Android notification's.
    private static func text(_ detail: JourneyDetail, clock: ClockFormatter) -> String {
        func time(_ millis: Int64) -> String { clock.clockText(epochMillis: millis).time }
        switch onEnum(of: detail) {
        case let .departs(d):
            return d.delayMinutes > 0
                ? L("journey_progress_leaves_late", time(d.atMillis), Int(d.delayMinutes))
                : L("journey_progress_leaves", time(d.atMillis))
        case let .platform(d): return L("journey_alert_platform", d.platform)
        case let .from(d): return L("journey_alert_from", d.stopName)
        case let .nextStop(d): return L("journey_progress_next_stop", d.stopName)
        case let .stopsLeft(d): return L("journey_progress_stops_left", Int(d.count))
        case let .change(d):
            return d.platform.map { L("journey_progress_change_platform", d.lineName, time(d.atMillis), $0) }
                ?? L("journey_progress_change", d.lineName, time(d.atMillis))
        case let .arrival(d): return L("journey_progress_arrival", time(d.atMillis))
        case let .arrived(d): return L("journey_progress_arrived_time", time(d.atMillis))
        case .locationLost: return L("journey_progress_location_lost")
        case .muted: return L("journey_progress_muted")
        }
    }
}
