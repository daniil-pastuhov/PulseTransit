import Foundation
import Shared

extension TimeZone {
    /// Norway's time, which every time the app shows is in, as on Android (`NorwayTimeZone`): the
    /// board is for Norwegian stops, so a departure reads like the station's sign wherever the
    /// device's clock is set.
    static let norway = TimeZone(identifier: NorwayTimeKt.NORWAY_TIME_ZONE_ID) ?? .current
}

extension Calendar {
    /// The current calendar in Norway's time, for reading a picked time of day as Norway's.
    static var norway: Calendar {
        var calendar = Calendar.current
        calendar.timeZone = .norway
        return calendar
    }
}
