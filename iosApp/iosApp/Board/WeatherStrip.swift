import Shared
import SwiftUI

/// The hourly forecast in the header, as many columns as fit.
struct WeatherStrip: View {
    let weather: [WeatherInfo]

    var body: some View {
        GeometryReader { geometry in
            // Fit only as many columns as the width allows, sampling evenly when there are more
            // hours than columns, like the Android header.
            let maxItems = max(1, min(8, Int(geometry.size.width / 56)))
            HStack(alignment: .top, spacing: 0) {
                ForEach(sampledHours(weather, maxItems: maxItems), id: \.time) { hour in
                    WeatherItem(weather: hour)
                        .frame(maxWidth: .infinity)
                }
            }
            .frame(maxHeight: .infinity)
        }
        .frame(height: 84)
    }
}

/// The hourly forecast for iPhone Duo's bar, `count` hours sampled like the header's, one view
/// each: stacked for the side column, or `compact` (the icon beside the temperature) for the top
/// row, which is a bar's height. The condition is left out, too long for either; the icon still
/// gives it to VoiceOver.
enum WeatherColumn {
    static func hours(_ weather: [WeatherInfo], count: Int, compact: Bool) -> [some View] {
        sampledHours(weather, maxItems: count).map { WeatherItem(weather: $0, showsCondition: false, isCompact: compact).fixedSize() }
    }
}

/// At most `maxItems` hours, sampled evenly when there are more.
private func sampledHours(_ weather: [WeatherInfo], maxItems: Int) -> [WeatherInfo] {
    let step = weather.count > maxItems ? weather.count / maxItems : 1
    return weather.enumerated().filter { $0.offset % step == 0 }.prefix(maxItems).map(\.element)
}

private struct WeatherItem: View {
    let weather: WeatherInfo
    var showsCondition = true
    var isCompact = false

    var body: some View {
        let symbol = WeatherSymbolKt.parseWeatherSymbol(symbolCode: weather.symbolCode)
        let label = weatherLabel(symbol)
        // Palette rather than multicolor: multicolor clouds are white and vanish on a light
        // header. Clouds follow the theme, like Android's icon; sun, rain and snow keep colour.
        let colors = symbol.sfSymbolColors
        let icon = Image(systemName: symbol.sfSymbolName)
            .symbolRenderingMode(.palette)
            .foregroundStyle(colors.primary, colors.secondary)
            .accessibilityLabel(label)
        let temperature = Text("\(Int(weather.temperature.rounded()))°")
            .foregroundColor(Palette.temperature(weather.temperature))
            .lineLimit(1)
        if isCompact {
            VStack(spacing: 0) {
                Text(hourText)
                    .font(.system(size: 9))
                    .foregroundColor(Palette.textPrimary.opacity(0.8))
                    .lineLimit(1)
                HStack(spacing: 3) {
                    icon.font(.system(size: 15))
                    temperature.font(.subheadline.bold())
                }
            }
        } else {
            VStack(spacing: 2) {
                Text(hourText)
                    .font(.caption2)
                    .foregroundColor(Palette.textPrimary.opacity(0.8))
                    .lineLimit(1)
                icon
                    .font(.system(size: 22))
                    .frame(height: 26)
                HStack(spacing: 3) {
                    temperature.font(.body.bold())
                    PrecipitationBar(millimetres: weather.precipitation)
                        .frame(width: 4, height: 16)
                        .accessibilityLabel(L("weather_precipitation", String(weather.precipitation)))
                }
                if showsCondition {
                    Text(label)
                        .font(.system(size: 9))
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                        .foregroundColor(Palette.textPrimary.opacity(0.6))
                }
            }
        }
    }

    /// MET times are UTC; the column shows the local hour.
    private var hourText: String {
        guard let date = Self.isoParser.date(from: weather.time) else { return "??:00" }
        return Self.hourFormatter.string(from: date) + ":00"
    }

    private static let isoParser = ISO8601DateFormatter()
    private static let hourFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH"
        return formatter
    }()
}

/// A vertical bar filled by the hour's precipitation; 4 mm/h is a downpour and fills it.
private struct PrecipitationBar: View {
    let millimetres: Double

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .bottom) {
                Capsule().fill(Palette.textPrimary.opacity(0.12))
                if millimetres > 0 {
                    Capsule()
                        .fill(Palette.rain)
                        .frame(height: geometry.size.height * min(max(millimetres / 4, 0.05), 1))
                }
            }
        }
    }
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
    /// The SF Symbol closest to what the Android icon draws for this condition.
    var sfSymbolName: String {
        guard isKnown else { return "questionmark.circle" }
        switch precip {
        case .snow:
            return thunder ? "cloud.bolt.fill" : (showers ? (isNight ? "cloud.moon.rain.fill" : "cloud.sun.rain.fill") : "cloud.snow.fill")
        case .sleet:
            return thunder ? "cloud.bolt.rain.fill" : "cloud.sleet.fill"
        case .rain:
            if thunder { return "cloud.bolt.rain.fill" }
            if showers { return isNight ? "cloud.moon.rain.fill" : "cloud.sun.rain.fill" }
            switch intensity {
            case .light: return "cloud.drizzle.fill"
            case .moderate: return "cloud.rain.fill"
            case .heavy: return "cloud.heavyrain.fill"
            }
        default:
            if thunder { return "cloud.bolt.fill" }
            switch sky {
            case .clear: return isNight ? "moon.stars.fill" : "sun.max.fill"
            case .fair, .partlyCloudy: return isNight ? "cloud.moon.fill" : "cloud.sun.fill"
            case .cloudy: return "cloud.fill"
            case .fog: return "cloud.fog.fill"
            }
        }
    }
}

private enum WeatherColors {
    static let sun = Color(hex: 0xFFB300)
    static let moon = Color(hex: 0x9FA8DA)
    static let snow = Color(hex: 0x90CAF9)
    static let bolt = Color(hex: 0xFFCA28)
}

extension WeatherSymbol {
    /// Colours for the palette layers of [sfSymbolName]: the first layer is the cloud where there is
    /// one, the second the sun, moon, rain, snow or bolt.
    var sfSymbolColors: (primary: Color, secondary: Color) {
        let cloud = Palette.textSecondary
        guard isKnown else { return (cloud, cloud) }
        if thunder { return (cloud, WeatherColors.bolt) }
        switch precip {
        case .rain, .sleet:
            return (cloud, showers ? (isNight ? WeatherColors.moon : WeatherColors.sun) : Palette.rain)
        case .snow:
            return (cloud, showers ? (isNight ? WeatherColors.moon : WeatherColors.sun) : WeatherColors.snow)
        default:
            switch sky {
            // No cloud in these symbols, so their only layer is the sun or moon.
            case .clear: return isNight ? (WeatherColors.moon, WeatherColors.moon) : (WeatherColors.sun, WeatherColors.sun)
            case .fair, .partlyCloudy: return (cloud, isNight ? WeatherColors.moon : WeatherColors.sun)
            case .cloudy, .fog: return (cloud, cloud)
            }
        }
    }
}
