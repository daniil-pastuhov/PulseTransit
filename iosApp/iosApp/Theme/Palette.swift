import Shared
import SwiftUI
import UIKit

/// The board's colours, the same tokens as the Android theme (theme/Color.kt), in light and dark.
enum Palette {
    static let background = dynamic(light: 0xF4F6F9, dark: 0x0F1016)
    static let surface = dynamic(light: 0xFFFFFF, dark: 0x161824)
    static let card = dynamic(light: 0xFFFFFF, dark: 0x1E2132)
    static let textPrimary = dynamic(light: 0x1E2132, dark: 0xFFFFFF)
    static let textSecondary = dynamic(light: 0x6B7280, dark: 0xAEB4C6)
    static let primary = dynamic(light: 0x1A73E8, dark: 0x5CA1FF)
    static let error = dynamic(light: 0xBA1A1A, dark: 0xFFB4AB)
    static let rain = Color(hex: 0x42A5F5)

    /// Line pill colour by Entur transport mode.
    static func transport(_ mode: String?) -> Color {
        switch mode?.lowercased() {
        case "bus": return Color(hex: 0xE74C3C)
        case "tram": return Color(hex: 0x7E48A2)
        case "metro": return Color(hex: 0xC45B25)
        case "rail": return Color(hex: 0x003D82)
        case "airportrail": return Color(hex: 0x86005B)
        case "water": return Color(hex: 0x16A085)
        default: return Color(hex: 0x7F8C8D)
        }
    }

    static func transportEmoji(_ mode: String?) -> String {
        switch mode?.lowercased() {
        case "tram": return "🚃"
        case "metro": return "🚇"
        case "rail": return "🚆"
        case "water": return "⛴️"
        default: return "🚌"
        }
    }

    /// Temperature ramp: indigo when freezing through red when hot, as on Android.
    static func temperature(_ celsius: Double) -> Color {
        let stops: [(Double, UInt32)] = [
            (-20, 0x5C6BC0), (-5, 0x42A5F5), (2, 0x26C6DA), (8, 0x9CCC65), (18, 0xFFA726), (28, 0xEF5350),
        ]
        guard let first = stops.first, let last = stops.last else { return .primary }
        if celsius <= first.0 { return Color(hex: first.1) }
        if celsius >= last.0 { return Color(hex: last.1) }
        let upper = stops.firstIndex { celsius < $0.0 } ?? stops.count - 1
        let (lowT, lowC) = stops[upper - 1]
        let (highT, highC) = stops[upper]
        return Color(hex: lowC).mix(with: Color(hex: highC), fraction: (celsius - lowT) / (highT - lowT))
    }

    private static func dynamic(light: UInt32, dark: UInt32) -> Color {
        Color(UIColor { traits in
            UIColor(hex: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }
}

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(UIColor(hex: hex))
    }

    /// Linear blend in RGB, like Compose's `lerp` for colours.
    func mix(with other: Color, fraction: Double) -> Color {
        var (r1, g1, b1, a1): (CGFloat, CGFloat, CGFloat, CGFloat) = (0, 0, 0, 0)
        var (r2, g2, b2, a2): (CGFloat, CGFloat, CGFloat, CGFloat) = (0, 0, 0, 0)
        UIColor(self).getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
        UIColor(other).getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
        let t = CGFloat(min(max(fraction, 0), 1))
        return Color(red: r1 + (r2 - r1) * t, green: g1 + (g2 - g1) * t, blue: b1 + (b2 - b1) * t)
    }
}

extension ThemeMode {
    /// The colour scheme to force, or nil to follow the system. AUTO without a sensor reading
    /// arrives here as AUTO and also follows the system, matching Android.
    var colorScheme: ColorScheme? {
        switch self {
        case .light: return .light
        case .dark: return .dark
        case .system, .auto: return nil
        }
    }
}
