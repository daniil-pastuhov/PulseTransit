import Shared
import SwiftUI
import UIKit

// The wall board's route cards: their type and sizes per size class, and the text measuring the
// cards decide what fits by. The counterpart of RouteCardParts.kt's RouteCardMetrics. The rules
// themselves are the shared RouteCardLayout, RouteCardBands and RouteCardFitting; the views measure
// their pieces here, before drawing them, and pass the widths in, so nothing needs iOS 16's Layout.

/// One of a route card's text styles: Public Sans with tabular figures, `size` points at the
/// default text size, growing with Dynamic Type as `style` does.
struct CardFont: Hashable {
    let size: CGFloat
    let weight: Font.Weight
    let style: Font.TextStyle

    var font: Font { .pulse(size: size, weight: weight, relativeTo: style) }
}

/// Measures text as the route cards draw it, at the Dynamic Type size they are drawn at.
struct CardMeasurer {
    let category: UIContentSizeCategory

    init(_ typeSize: DynamicTypeSize) {
        category = typeSize.contentSizeCategory
    }

    /// The text scale the shared rules take ("font scale"): how much larger body text is than at
    /// the default size, so 1 there and above 3 at the largest accessibility size.
    var fontScale: CGFloat {
        UIFontMetrics(forTextStyle: .body).scaledValue(for: 100, compatibleWith: UITraitCollection(preferredContentSizeCategory: category)) / 100
    }

    func uiFont(_ font: CardFont) -> UIFont {
        let key = FontKey(font: font, category: category)
        if let cached = Self.fonts[key] { return cached }
        let made = pulseUIFont(size: font.size, weight: font.weight, relativeTo: font.style, category: category)
        Self.fonts[key] = made
        return made
    }

    /// How wide `text` is on one line, rounded up.
    func width(_ text: String, _ font: CardFont) -> CGFloat {
        guard !text.isEmpty else { return 0 }
        return ceil((text as NSString).size(withAttributes: [.font: uiFont(font)]).width)
    }

    /// How tall one line of `font` is, rounded up.
    func lineHeight(_ font: CardFont) -> CGFloat {
        ceil(uiFont(font).lineHeight)
    }

    /// The font of a `LinePill(small: true, scale:)`.
    func pillFont(_ scale: CGFloat) -> CardFont {
        CardFont(size: LinePill.smallTextSize * scale, weight: .bold, style: .footnote)
    }

    /// The size of a small line pill at `scale`, as `LinePill` draws it.
    func pillSize(_ line: String, scale: CGFloat) -> CGSize {
        let font = pillFont(scale)
        return CGSize(
            width: width(line, font) + 2 * LinePill.smallPadding * scale,
            height: max(LinePill.smallHeight * scale, lineHeight(font))
        )
    }

    private struct FontKey: Hashable {
        let font: CardFont
        let category: UIContentSizeCategory
    }

    /// Fonts made so far; only read and written on the main thread, from views' bodies.
    private static var fonts: [FontKey: UIFont] = [:]
}

/// The type and sizes of one `RouteCardSize`: the card's text steps up with its width, and so do
/// its line pills (`pillScale`), so a wall card is read across a room while a phone card stays
/// tight. As Android's RouteCardMetrics: COMPACT clock 34, title 16, body 14; REGULAR clock 44,
/// title 19, body 15; WIDE clock 56, title 22, body 17, caption 13, second clock 24; WALL clock 88,
/// title 30, body 22, caption 17, second clock 34.
struct RouteCardMetrics {
    var padH: CGFloat
    var padV: CGFloat
    var gap: CGFloat
    /// The leave clock, the largest thing on the card.
    let clock: CardFont
    let title: CardFont
    let body: CardFont
    let caption: CardFont
    /// The arrival clock and the next departures' clocks.
    let secondClock: CardFont
    /// The leave clock on a one-line card (`RouteCardDensity.line`).
    let lineClock: CardFont
    /// Buttons' labels: "3 more", "+3", "+2 in details", a row's duration.
    let label: CardFont
    let pillScale: CGFloat
    let icon: CGFloat
    /// How thick a ride's bar on the timeline is.
    let track: CGFloat
    /// At most this many points of timeline per minute of the route.
    let perMinute: CGFloat
    /// The band along the card's bottom: a touch target and more.
    let footer: CGFloat

    static func of(_ size: RouteCardSize) -> RouteCardMetrics {
        func bold(_ points: CGFloat, _ style: Font.TextStyle) -> CardFont { CardFont(size: points, weight: .bold, style: style) }
        func semibold(_ points: CGFloat, _ style: Font.TextStyle) -> CardFont { CardFont(size: points, weight: .semibold, style: style) }
        func regular(_ points: CGFloat) -> CardFont { CardFont(size: points, weight: .regular, style: .subheadline) }
        func caption(_ points: CGFloat) -> CardFont { CardFont(size: points, weight: .medium, style: .caption) }
        let label = semibold(14, .subheadline)
        switch size {
        case .compact:
            return RouteCardMetrics(
                padH: FjordTokens.space4, padV: FjordTokens.space3, gap: FjordTokens.space3,
                clock: bold(34, .largeTitle), title: semibold(16, .headline), body: regular(14), caption: caption(12),
                secondClock: label, lineClock: bold(24, .title), label: label,
                pillScale: 1, icon: 16, track: 4, perMinute: .infinity, footer: 52
            )
        case .regular:
            return RouteCardMetrics(
                padH: FjordTokens.space5, padV: FjordTokens.space4, gap: FjordTokens.space5,
                clock: bold(44, .largeTitle), title: semibold(19, .headline), body: regular(15), caption: caption(12),
                secondClock: bold(20, .title3), lineClock: bold(28, .title), label: label,
                pillScale: 1.1, icon: 18, track: 6, perMinute: .infinity, footer: 56
            )
        case .wide:
            return RouteCardMetrics(
                padH: FjordTokens.space6, padV: FjordTokens.space4, gap: FjordTokens.space6,
                clock: bold(56, .largeTitle), title: semibold(22, .headline), body: regular(17), caption: caption(13),
                secondClock: bold(24, .title3), lineClock: bold(32, .title), label: label,
                pillScale: 1.25, icon: 20, track: 6, perMinute: 34, footer: 56
            )
        case .wall:
            return RouteCardMetrics(
                padH: FjordTokens.space8, padV: FjordTokens.space5, gap: FjordTokens.space8,
                clock: bold(88, .largeTitle), title: semibold(30, .headline), body: regular(22), caption: caption(17),
                secondClock: bold(34, .title3), lineClock: bold(44, .title), label: label,
                pillScale: 1.7, icon: 28, track: 10, perMinute: 44, footer: 64
            )
        }
    }
}

// MARK: Texts

extension TripInfo {
    /// The legs a route shows: rides, and walks that take any time (a zero-minute walk is a quay change).
    var cardLegs: [TripLeg] { legs.filter { $0.lineName != nil || $0.durationMinutes > 0 } }

    /// "19:00", or "19:00+1" on a later day; nil when Entur gave no arrival.
    var arrivalClock: String? {
        TripArrivalKt.tripArrival(trip: self).map { $0.dayOffset > 0 ? "\($0.time)+\($0.dayOffset)" : $0.time }
    }
}

/// "Now" when it is leaving, else "in 14 min"; nil when its time cannot be read.
func routeLeavesIn(_ time: String, nowMillis: Int64) -> String? {
    if DepartureCountdownKt.departureCountdown(expectedTime: time, nowMillis: nowMillis) is DepartureCountdownNow { return L("now") }
    return DepartureDetailsKt.minutesUntil(time: time, nowMillis: nowMillis).map { inTimeText($0.int64Value) }
}

/// What VoiceOver says for a next departure: "18:12, in 17 min, 37, 4, arr. 19:08".
func nextDepartureDescription(_ trip: TripInfo, nowMillis: Int64) -> String {
    let lines = trip.cardLegs.compactMap(\.lineName).joined(separator: ", ")
    return [
        isoClockTime(trip.expectedStartTime),
        routeLeavesIn(trip.expectedStartTime, nowMillis: nowMillis),
        lines.isEmpty ? nil : lines,
        trip.arrivalClock.map { L("route_arrival", $0) },
    ].compactMap { $0 }.joined(separator: ", ")
}

// MARK: Pieces

/// An SF Symbol in a square of `size` points, decorative.
struct CardIcon: View {
    let name: String
    let size: CGFloat
    var color: Color = Palette.onRoute

    var body: some View {
        Image(systemName: name)
            .font(.system(size: size * 0.8, weight: .semibold))
            .foregroundColor(color)
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

/// A ride's bar on a timeline: rounded at its end only, the pill covering its start.
struct TrailingRoundedBar: Shape {
    func path(in rect: CGRect) -> Path {
        let radius = min(rect.height / 2, rect.width / 2)
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX - radius, y: rect.minY))
        path.addArc(center: CGPoint(x: rect.maxX - radius, y: rect.midY), radius: radius, startAngle: .degrees(-90), endAngle: .degrees(90), clockwise: false)
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

/// A walk's dotted stretch on a timeline: dots a third of its height in radius, spaced four radii.
struct WalkDots: Shape {
    func path(in rect: CGRect) -> Path {
        let radius = rect.height / 3
        let step = radius * 4
        var path = Path()
        guard radius > 0 else { return path }
        var x = rect.minX + radius + step / 2
        while x < rect.maxX - radius {
            path.addEllipse(in: CGRect(x: x - radius, y: rect.midY - radius, width: 2 * radius, height: 2 * radius))
            x += step
        }
        return path
    }
}

extension Array where Element == CGFloat {
    /// For the shared rules, which take Kotlin floats.
    var kotlin: [KotlinFloat] { map { KotlinFloat(value: Float($0)) } }
}

extension Array where Element == KotlinFloat {
    var points: [CGFloat] { map { CGFloat($0.floatValue) } }
}
