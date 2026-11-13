import Shared
import SwiftUI
import UIKit

/// The board's colours: the Fjord roles from docs/design/tokens.json (generated into FjordTokens.swift),
/// the same as the Android theme (theme/Color.kt), in light and dark.
enum Palette {
    static let background = dynamic(FjordTokens.background)
    /// Cards and panels.
    static let surface = dynamic(FjordTokens.surface)
    /// Raised cards, sheets and dialogs.
    static let card = dynamic(FjordTokens.surfaceContainerHigh)
    /// Fills: rows, skeletons, tracks.
    static let surfaceContainer = dynamic(FjordTokens.surfaceContainer)
    static let textPrimary = dynamic(FjordTokens.onSurface)
    static let textSecondary = dynamic(FjordTokens.onSurfaceVariant)
    static let outline = dynamic(FjordTokens.outline)
    static let outlineVariant = dynamic(FjordTokens.outlineVariant)
    static let primary = dynamic(FjordTokens.primary)
    static let onPrimary = dynamic(FjordTokens.onPrimary)
    static let primaryContainer = dynamic(FjordTokens.primaryContainer)
    static let onPrimaryContainer = dynamic(FjordTokens.onPrimaryContainer)
    static let error = dynamic(FjordTokens.error)
    static let errorContainer = dynamic(FjordTokens.errorContainer)
    static let onErrorContainer = dynamic(FjordTokens.onErrorContainer)
    static let warningContainer = dynamic(FjordTokens.warningContainer)
    static let onWarningContainer = dynamic(FjordTokens.onWarningContainer)
    static let successContainer = dynamic(FjordTokens.successContainer)
    static let onSuccessContainer = dynamic(FjordTokens.onSuccessContainer)
    static let infoContainer = dynamic(FjordTokens.infoContainer)
    static let onInfoContainer = dynamic(FjordTokens.onInfoContainer)
    static let inverseSurface = dynamic(FjordTokens.inverseSurface)
    static let onInverseSurface = dynamic(FjordTokens.onInverseSurface)
    static let inversePrimary = dynamic(FjordTokens.inversePrimary)
    /// Delayed times and late countdowns.
    static let late = dynamic(FjordTokens.late)
    /// "Now" and realtime times.
    static let now = dynamic(FjordTokens.now)
    /// The fastest-route banner, which stays dark on the wall at night.
    static let route = dynamic(FjordTokens.route)
    static let onRoute = dynamic(FjordTokens.onRoute)
    static let routeAccent = dynamic(FjordTokens.routeAccent)
    static let onLine = dynamic(FjordTokens.onLineBus)
    static let rain = Color(hex: 0x42A5F5)

    /// Line pill colour by Entur transport mode, tuned per theme so white text passes AA.
    static func transport(_ mode: String?) -> Color {
        switch mode?.lowercased() {
        case "bus", "coach": return lineBus
        case "tram": return lineTram
        case "metro": return lineMetro
        case "rail": return lineTrain
        case "airportrail": return lineExpress
        case "water": return lineFerry
        default: return lineOther
        }
    }

    private static let lineBus = dynamic(FjordTokens.lineBus)
    private static let lineTram = dynamic(FjordTokens.lineTram)
    private static let lineTrain = dynamic(FjordTokens.lineTrain)
    private static let lineExpress = dynamic(FjordTokens.lineExpress)
    private static let lineMetro = dynamic(FjordTokens.lineMetro)
    private static let lineFerry = dynamic(FjordTokens.lineFerry)
    private static let lineOther = dynamic(FjordTokens.lineOther)

    /// The SF Symbol for an Entur transport mode, the counterpart of Android's Material Symbols.
    /// Newer symbols fall back to ones every supported iOS has.
    static func transportSymbol(_ mode: String?) -> String {
        switch mode?.lowercased() {
        case "tram": return firstAvailable("lightrail.fill", "tram.fill")
        case "metro": return firstAvailable("tram.tunnel.fill", "tram.fill")
        // The front-view train, like Material's "train"; the side view reads as a blob at text size.
        case "rail", "airportrail": return "tram.fill"
        case "water": return firstAvailable("ferry.fill", "drop.fill")
        case "air": return "airplane"
        case "foot": return "figure.walk"
        default: return "bus.fill"
        }
    }

    private static func firstAvailable(_ names: String...) -> String {
        names.first { UIImage(systemName: $0) != nil } ?? names[names.count - 1]
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

    private static func dynamic(_ pair: (light: UInt32, dark: UInt32)) -> Color {
        Color(UIColor { traits in
            UIColor(hex: traits.userInterfaceStyle == .dark ? pair.dark : pair.light)
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
