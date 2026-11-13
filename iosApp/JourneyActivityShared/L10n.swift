import Foundation
import Shared

/// The bundle with the strings and fonts: the app's, also in the widget extension, which lives inside
/// it (PulseTransit.app/PlugIns/JourneyActivity.appex) and has none of its own.
let appBundle: Bundle = {
    let main = Bundle.main
    guard main.bundleURL.pathExtension == "appex" else { return main }
    return Bundle(url: main.bundleURL.deletingLastPathComponent().deletingLastPathComponent()) ?? main
}()

/// Looks up a string from Localizable.xcstrings, which scripts/sync-ios-strings.py generates from the
/// Android string resources; keys are the Android resource names. Arguments fill the format
/// specifiers, and an integer argument also picks the plural form where the string has one.
func L(_ key: String, _ arguments: CVarArg...) -> String {
    let format = appBundle.localizedString(forKey: key, value: nil, table: nil)
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
