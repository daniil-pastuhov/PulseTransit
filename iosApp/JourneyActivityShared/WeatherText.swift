import Shared
import SwiftUI

// Weather wording and symbols, shared by the board's weather strip and the widgets.

/// "8°", "−12°" with a true minus sign.
func temperatureText(_ celsius: Double) -> String {
    let rounded = Int(celsius.rounded())
    return rounded < 0 ? "\u{2212}\(-rounded)°" : "\(rounded)°"
}

/// Human-readable condition, e.g. "Light rain and thunder", using the same strings as Android.
func weatherLabel(_ symbol: WeatherSymbol) -> String {
    guard symbol.isKnown else { return L("weather_unknown") }
    let key: String
    switch symbol.precip {
    case .rain: key = ["weather_light_rain", "weather_rain", "weather_heavy_rain"][symbol.intensity.index]
    case .sleet: key = ["weather_light_sleet", "weather_sleet", "weather_heavy_sleet"][symbol.intensity.index]
    case .snow: key = ["weather_light_snow", "weather_snow", "weather_heavy_snow"][symbol.intensity.index]
    default:
        switch symbol.sky {
        case .clear: key = "weather_clear"
        case .fair: key = "weather_fair"
        case .partlyCloudy: key = "weather_partly_cloudy"
        case .cloudy: key = "weather_cloudy"
        case .fog: key = "weather_fog"
        }
    }
    let base = L(key)
    return symbol.thunder ? L("weather_and_thunder", base) : base
}

private extension Intensity {
    var index: Int {
        switch self {
        case .light: return 0
        case .moderate: return 1
        case .heavy: return 2
        }
    }
}

extension WeatherSymbol {
    /// The SF Symbol closest to the Material Symbol Android draws for this condition, outlined.
    var sfSymbolName: String {
        guard isKnown else { return "questionmark.circle" }
        switch precip {
        case .snow:
            return thunder ? "cloud.bolt" : (showers ? (isNight ? "cloud.moon.rain" : "cloud.sun.rain") : "cloud.snow")
        case .sleet:
            return thunder ? "cloud.bolt.rain" : "cloud.sleet"
        case .rain:
            if thunder { return "cloud.bolt.rain" }
            if showers { return isNight ? "cloud.moon.rain" : "cloud.sun.rain" }
            switch intensity {
            case .light: return "cloud.drizzle"
            case .moderate: return "cloud.rain"
            case .heavy: return "cloud.heavyrain"
            }
        default:
            if thunder { return "cloud.bolt" }
            switch sky {
            case .clear: return isNight ? "moon.stars" : "sun.max"
            case .fair, .partlyCloudy: return isNight ? "cloud.moon" : "cloud.sun"
            case .cloudy: return "cloud"
            case .fog: return "cloud.fog"
            }
        }
    }
}
