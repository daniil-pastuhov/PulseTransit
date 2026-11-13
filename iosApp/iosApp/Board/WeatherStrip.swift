import Shared
import SwiftUI

// The header's weather, as on Android (BoardHeader.kt): the weather now (icon, temperature,
// condition), then the next hours. On a phone it sits in a card under the clock; wide, it is bare
// between the clock and the buttons. Only whole hours that fit are shown, so a label is never cut.

struct WeatherStrip: View {
    let weather: [WeatherInfo]
    var wide = false

    var body: some View {
        if let now = weather.first {
            let symbol = WeatherSymbolKt.parseWeatherSymbol(symbolCode: now.symbolCode)
            let label = weatherLabel(symbol)
            HStack(spacing: wide ? FjordTokens.space6 : FjordTokens.space3) {
                HStack(spacing: FjordTokens.space2) {
                    WeatherSymbolImage(symbol: symbol).font(.system(size: 28))
                    VStack(alignment: .leading, spacing: 0) {
                        Text(temperatureText(now.temperature))
                            .font(.pulse(role: FjordTokens.typeTitle, relativeTo: .title3))
                            .foregroundColor(Palette.textPrimary)
                        Text(label)
                            .font(.pulse(.caption))
                            .foregroundColor(Palette.textSecondary)
                            .lineLimit(1)
                    }
                }
                .fixedSize()
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(L("weather_now", temperatureText(now.temperature), label))
                Rectangle()
                    .fill(Palette.outlineVariant)
                    .frame(width: 1, height: 36)
                GeometryReader { geometry in
                    let itemWidth: CGFloat = wide ? 56 : 40
                    let count = max(0, min(weather.count - 1, Int(geometry.size.width / itemWidth)))
                    HStack(spacing: wide ? FjordTokens.space6 : 0) {
                        ForEach(Array(weather.dropFirst().prefix(count)), id: \.time) { hour in
                            HourWeather(weather: hour, wide: wide)
                            if !wide && hour.time != weather.dropFirst().prefix(count).last?.time { Spacer(minLength: 0) }
                        }
                    }
                    .frame(maxHeight: .infinity)
                }
                .frame(height: 56)
            }
            .padding(.horizontal, wide ? 0 : FjordTokens.space3)
            .padding(.vertical, wide ? 0 : FjordTokens.space2)
            .background(
                RoundedRectangle(cornerRadius: FjordTokens.radiusMd)
                    .fill(wide ? Color.clear : Palette.surfaceContainer)
            )
        }
    }
}

/// The hourly forecast for iPhone Duo's bar, `count` hours from the next one, one view each:
/// stacked for the side column, or `compact` (the icon beside the temperature) for the top row,
/// which is a bar's height.
enum WeatherColumn {
    static func hours(_ weather: [WeatherInfo], count: Int, compact: Bool) -> [some View] {
        Array(weather.dropFirst().prefix(count)).map { HourWeather(weather: $0, wide: false, isCompact: compact).fixedSize() }
    }
}

/// One hour: "20", its icon and temperature; "20:00" wide.
private struct HourWeather: View {
    let weather: WeatherInfo
    var wide = false
    var isCompact = false

    var body: some View {
        let symbol = WeatherSymbolKt.parseWeatherSymbol(symbolCode: weather.symbolCode)
        let hour = Self.hour(weather.time)
        let temperature = Text(temperatureText(weather.temperature))
            .font(.pulse(.subheadline, weight: .semibold))
            .foregroundColor(Palette.textPrimary)
            .lineLimit(1)
        Group {
            if isCompact {
                VStack(spacing: 0) {
                    Text(hour).font(.pulse(fixedSize: 9)).foregroundColor(Palette.textSecondary)
                    HStack(spacing: 3) {
                        WeatherSymbolImage(symbol: symbol).font(.system(size: 15))
                        temperature
                    }
                }
            } else {
                VStack(spacing: 2) {
                    Text(wide ? "\(hour):00" : hour)
                        .font(.pulse(.caption))
                        .foregroundColor(Palette.textSecondary)
                        .lineLimit(1)
                    WeatherSymbolImage(symbol: symbol).font(.system(size: 18)).frame(height: 20)
                    temperature
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(hour):00, \(temperatureText(weather.temperature)), \(weatherLabel(symbol))")
    }

    /// MET times are UTC; the column shows the local hour.
    private static func hour(_ time: String) -> String {
        guard let date = isoParser.date(from: time) else { return "??" }
        return hourFormatter.string(from: date)
    }

    private static let isoParser = ISO8601DateFormatter()
    private static let hourFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH"
        formatter.timeZone = .norway
        return formatter
    }()
}

/// A condition's SF Symbol in the muted text colour, outlined like the Material Symbols on Android.
private struct WeatherSymbolImage: View {
    let symbol: WeatherSymbol

    var body: some View {
        Image(systemName: symbol.sfSymbolName)
            .foregroundColor(Palette.textSecondary)
            .accessibilityHidden(true)
    }
}

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
