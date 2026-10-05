import Foundation
import Shared

/// The header clock's strings in the user's locale, using the same patterns as Android.
final class SystemClockFormatter: NSObject, ClockFormatter {
    private var formatters: [String: DateFormatter] = [:]
    private var localeIdentifier = ""

    func clockText(epochMillis: Int64) -> ClockText {
        let date = Self.date(epochMillis)
        return ClockText(
            time: format(date, "HH:mm"),
            dayOfMonth: format(date, "d"),
            weekdayShort: format(date, "EEE"),
            monthShort: format(date, "LLL"),
            weekdayLong: format(date, "EEEE"),
            monthLong: format(date, "LLLL")
        )
    }

    func timeWithSeconds(epochMillis: Int64) -> String {
        format(Self.date(epochMillis), "HH:mm:ss")
    }

    /// Formatters are cached per pattern, since the clock ticks every second, and dropped when the
    /// locale changes so a language switch shows up without a restart.
    private func format(_ date: Date, _ pattern: String) -> String {
        let locale = Locale.current
        if locale.identifier != localeIdentifier {
            formatters.removeAll()
            localeIdentifier = locale.identifier
        }
        let formatter = formatters[pattern] ?? {
            let formatter = DateFormatter()
            formatter.locale = locale
            formatter.dateFormat = pattern
            formatters[pattern] = formatter
            return formatter
        }()
        return formatter.string(from: date)
    }

    private static func date(_ epochMillis: Int64) -> Date {
        Date(timeIntervalSince1970: TimeInterval(epochMillis) / 1000)
    }
}
