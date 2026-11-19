import Foundation
import Shared

/// The words for a measured walking pace and a proposal to change the walking speed, as on Android.
/// The shared WalkingPace decides whether there is a proposal and for which level; this only picks
/// the string for a level.
enum WalkingPaceText {
    /// The pace in km/h with one decimal in the locale's style: "5.4", or "5,4" in Norwegian.
    static func kmh(_ tenths: Int32) -> String {
        String(format: "%.1f", locale: Locale.current, Double(tenths) / 10)
    }

    /// "You walk faster than the app assumes", or slower.
    static func title(_ proposal: WalkingSpeedProposal) -> String {
        L(proposal.isFaster ? "walking_pace_proposal_title_faster" : "walking_pace_proposal_title_slower")
    }

    /// "Measured on this device: about 5.6 km/h. Use Fast walking speed for routes?"
    static func body(_ proposal: WalkingSpeedProposal) -> String {
        L("walking_pace_proposal_body_\(suffix(proposal.speed))", kmh(proposal.kmhTenths))
    }

    /// "Use Fast", the action that accepts the proposal.
    static func use(_ speed: WalkingSpeed) -> String {
        L("walking_pace_use_\(suffix(speed))")
    }

    /// "Measured on this device: about 5.6 km/h", or that nothing has been measured yet.
    static func measured(_ pace: MeasuredWalkingPace?) -> String {
        guard let pace else { return L("walking_pace_not_measured") }
        return L("walking_pace_measured", kmh(pace.kmhTenths))
    }

    private static func suffix(_ speed: WalkingSpeed) -> String {
        switch speed {
        case .slow: return "slow"
        case .normal: return "normal"
        case .fast: return "fast"
        }
    }
}
