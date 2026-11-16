import CoreText
import SwiftUI
import UIKit

/// Public Sans, bundled (Fonts/, registered by UIAppFonts) so the app matches Android and the
/// widgets. Each system text style keeps its size and Dynamic Type scaling; digits are tabular so
/// times and countdowns don't shift as they change.
extension Font {
    static func pulse(_ style: Font.TextStyle, weight: Font.Weight? = nil) -> Font {
        let face = PulseFace(weight ?? style.defaultWeight)
        return Font.custom(face.postScriptName, size: style.pointSize, relativeTo: style).monospacedDigit()
    }

    /// A fixed size that still scales with Dynamic Type, relative to [style].
    static func pulse(size: CGFloat, weight: Font.Weight = .regular, relativeTo style: Font.TextStyle = .body) -> Font {
        Font.custom(PulseFace(weight).postScriptName, size: size, relativeTo: style).monospacedDigit()
    }

    /// A size that does not scale with Dynamic Type, for text sized to fit its space (the clock).
    static func pulse(fixedSize size: CGFloat, weight: Font.Weight = .regular) -> Font {
        Font.custom(PulseFace(weight).postScriptName, fixedSize: size).monospacedDigit()
    }

    /// A Fjord type role (FjordTokens.type*), e.g. `.pulse(role: FjordTokens.typeCountdown)`.
    static func pulse(
        role: (size: CGFloat, line: CGFloat, weight: Int, tracking: CGFloat),
        relativeTo style: Font.TextStyle = .body
    ) -> Font {
        pulse(size: role.size, weight: PulseFace.weight(role.weight), relativeTo: style)
    }
}

/// The UIFont that `.pulse(size:weight:relativeTo:)` draws with at the Dynamic Type size
/// `category`, tabular figures included, for measuring text before it is laid out (the route cards
/// decide what fits from such widths).
func pulseUIFont(size: CGFloat, weight: Font.Weight, relativeTo style: Font.TextStyle, category: UIContentSizeCategory) -> UIFont {
    let base = UIFont(name: PulseFace(weight).postScriptName, size: size) ?? .systemFont(ofSize: size)
    let tabular = base.fontDescriptor.addingAttributes([
        .featureSettings: [[
            UIFontDescriptor.FeatureKey.type: kNumberSpacingType,
            UIFontDescriptor.FeatureKey.selector: kMonospacedNumbersSelector,
        ]],
    ])
    return UIFontMetrics(forTextStyle: style.uiTextStyle)
        .scaledFont(for: UIFont(descriptor: tabular, size: size), compatibleWith: UITraitCollection(preferredContentSizeCategory: category))
}

extension DynamicTypeSize {
    /// UIKit's name for this size, for `UIFontMetrics`.
    var contentSizeCategory: UIContentSizeCategory {
        switch self {
        case .xSmall: return .extraSmall
        case .small: return .small
        case .medium: return .medium
        case .large: return .large
        case .xLarge: return .extraLarge
        case .xxLarge: return .extraExtraLarge
        case .xxxLarge: return .extraExtraExtraLarge
        case .accessibility1: return .accessibilityMedium
        case .accessibility2: return .accessibilityLarge
        case .accessibility3: return .accessibilityExtraLarge
        case .accessibility4: return .accessibilityExtraExtraLarge
        case .accessibility5: return .accessibilityExtraExtraExtraLarge
        @unknown default: return .large
        }
    }
}

/// Registers the bundled faces for this process. The app has them through UIAppFonts; the widget
/// extension reads them from the app's bundle (`appBundle`) and calls this once at start.
func registerPulseFonts() {
    for name in ["PublicSans-Regular", "PublicSans-Medium", "PublicSans-SemiBold", "PublicSans-Bold"] {
        guard let url = appBundle.url(forResource: name, withExtension: "ttf") else { continue }
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
    }
}

/// The four bundled faces; other weights round to the nearest one.
private enum PulseFace {
    case regular, medium, semibold, bold

    init(_ weight: Font.Weight) {
        switch weight {
        case .ultraLight, .thin, .light, .regular: self = .regular
        case .medium: self = .medium
        case .semibold: self = .semibold
        default: self = .bold
        }
    }

    static func weight(_ value: Int) -> Font.Weight {
        switch value {
        case ..<500: return .regular
        case 500: return .medium
        case 600: return .semibold
        default: return .bold
        }
    }

    var postScriptName: String {
        switch self {
        case .regular: return "PublicSans-Regular"
        case .medium: return "PublicSans-Medium"
        case .semibold: return "PublicSans-SemiBold"
        case .bold: return "PublicSans-Bold"
        }
    }
}

private extension Font.TextStyle {
    /// The default (large) Dynamic Type sizes, so Public Sans sits where the system font did.
    var pointSize: CGFloat {
        switch self {
        case .largeTitle: return 34
        case .title: return 28
        case .title2: return 22
        case .title3: return 20
        case .headline: return 17
        case .body: return 17
        case .callout: return 16
        case .subheadline: return 15
        case .footnote: return 13
        case .caption: return 12
        case .caption2: return 11
        @unknown default: return 17
        }
    }

    var defaultWeight: Font.Weight { self == .headline ? .semibold : .regular }

    var uiTextStyle: UIFont.TextStyle {
        switch self {
        case .largeTitle: return .largeTitle
        case .title: return .title1
        case .title2: return .title2
        case .title3: return .title3
        case .headline: return .headline
        case .body: return .body
        case .callout: return .callout
        case .subheadline: return .subheadline
        case .footnote: return .footnote
        case .caption: return .caption1
        case .caption2: return .caption2
        @unknown default: return .body
        }
    }
}
