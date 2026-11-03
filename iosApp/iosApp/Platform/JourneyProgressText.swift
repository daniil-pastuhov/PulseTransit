import Foundation
import Shared

/// A journey's progress in words, as the Android notification has it: the Live Activity and the
/// trip details' "Now" card both say it this way.
enum JourneyProgressText {
    /// What to do now: "5 towards Vestli", "On 5 to Majorstuen", "Walking to Jernbanetorget".
    static func headline(_ progress: JourneyProgress) -> String {
        let line = progress.lineName ?? ""
        let target = progress.targetName ?? ""
        switch progress.phase {
        case .waiting: return progress.towards.map { L("journey_progress_towards", line, $0) } ?? L("journey_progress_waiting", line, target)
        case .riding: return L("journey_progress_riding", line, target)
        case .walking: return L("journey_progress_walking", target)
        case .arrived: return L("journey_progress_arrived", progress.destinationName)
        }
    }

    /// One of the progress' facts.
    static func detail(_ detail: JourneyDetail, clock: ClockFormatter) -> String {
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
