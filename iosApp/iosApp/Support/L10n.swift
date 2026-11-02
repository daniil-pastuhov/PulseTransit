import Foundation
import Shared

/// Looks up a string from Localizable.xcstrings, which scripts/sync-ios-strings.py generates from the
/// Android string resources; keys are the Android resource names. Arguments fill the format
/// specifiers, and an integer argument also picks the plural form where the string has one.
func L(_ key: String, _ arguments: CVarArg...) -> String {
    let format = NSLocalizedString(key, comment: "")
    return arguments.isEmpty ? format : String(format: format, locale: Locale.current, arguments: arguments)
}

/// A trip or leg duration in the largest units that fit, e.g. "17 h 23 min"; the shared
/// `tripDurationParts` picks the units.
func tripDuration(_ minutes: Int32) -> String {
    TripDurationKt.tripDurationParts(minutes: minutes).map { part in
        let key: String
        switch part.unit {
        case .years: key = "duration_years"
        case .months: key = "duration_months"
        case .days: key = "duration_days"
        case .hours: key = "duration_hours"
        case .minutes: key = "duration_min"
        }
        return L(key, Int(part.value))
    }.joined(separator: " ")
}
