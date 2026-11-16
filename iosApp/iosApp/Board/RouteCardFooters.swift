import Shared
import SwiftUI

// The bands along a route card's bottom (`RouteCardFooter`), the rail that stands in for the
// toggle when they are left out for room, and the next departures that they and wide cards name.
// As Android's RouteCardFooter.kt.

/// How strongly a band is tinted over the card.
let cardBandTint = 0.08
/// A divider's strength on the route colour.
let cardDividerAlpha = 0.15

/// A band the width of the card: `content` at least `metrics.footer` high, at the bottom of
/// `height` when the grid row makes the band taller (`RouteCardLayout.stretch`), so it stays
/// level with its neighbours' bands.
private struct CardBand<Content: View>: View {
    let metrics: RouteCardMetrics
    let height: CGFloat?
    @ViewBuilder let content: () -> Content

    var body: some View {
        content()
            .frame(maxWidth: .infinity, minHeight: metrics.footer)
            .frame(height: height, alignment: .bottom)
            .background(Palette.onRoute.opacity(cardBandTint))
    }
}

/// The card's toggle bar: one target the width of the card that opens or closes its alternatives
/// (a setting; the app never closes a card itself). Collapsed it names the `next` departures, as
/// many as fit (`RouteCardFitting.nextEntries`), and "3 more"; open, "Fewer". A grab handle on
/// top says it can be pulled: besides a tap, a downward drag opens it and an upward one closes it.
/// The drag is the bar's own, so it never reaches a scroll view or pull to refresh around it. To
/// VoiceOver it is one button: its label and the next departures, "Expanded"/"Collapsed" as its
/// value, and "Show more routes to X" as its hint.
struct ToggleBand: View {
    let expanded: Bool
    let count: Int
    let next: [TripInfo]
    let destinationName: String
    let metrics: RouteCardMetrics
    let measurer: CardMeasurer
    /// The band's width.
    let width: CGFloat
    let nowMillis: Int64
    var height: CGFloat?
    let onToggle: () -> Void

    /// How far the bar has been pulled, down positive.
    @State private var pulled: CGFloat = 0

    private static let dragThreshold: CGFloat = 24
    private static let entryGap = FjordTokens.space5

    var body: some View {
        let label = expanded ? L("route_fewer") : L("route_more", count)
        let thenLabel = L("route_then")
        let chevron = metrics.icon + 6
        let available = width - metrics.padH - (metrics.padH - FjordTokens.space1)
            - measurer.width(thenLabel, metrics.caption) - measurer.width(label, metrics.label) - chevron - 3 * FjordTokens.space3
        let fit = RouteCardFitting.shared.nextEntries(
            widthsByDetail: NextDetail.allCases.map { detail in
                next.map { detail.width($0, metrics: metrics, measurer: measurer, nowMillis: nowMillis) }.kotlin
            },
            gap: Float(Self.entryGap),
            available: Float(available)
        )
        let detail = NextDetail.allCases[Int(fit.detail)]
        let shown = Array(next.prefix(Int(fit.count)))
        let spoken = next.map { nextDepartureDescription($0, nowMillis: nowMillis) }
        CardBand(metrics: metrics, height: height) {
            HStack(spacing: FjordTokens.space3) {
                if next.isEmpty {
                    // Open: nothing to name, so "Fewer" sits in the middle of the bar.
                    Spacer(minLength: 0)
                } else {
                    Text(thenLabel).font(metrics.caption.font).lineLimit(1).fixedSize()
                    HStack(spacing: Self.entryGap) {
                        ForEach(Array(shown.enumerated()), id: \.offset) { _, trip in
                            NextEntry(trip: trip, detail: detail, metrics: metrics, nowMillis: nowMillis)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                Text(label).font(metrics.label.font).lineLimit(1).fixedSize()
                CardIcon(name: expanded ? "chevron.up" : "chevron.down", size: chevron)
                if next.isEmpty { Spacer(minLength: 0) }
            }
            .foregroundColor(Palette.onRoute)
            .padding(.leading, metrics.padH)
            .padding(.trailing, metrics.padH - FjordTokens.space1)
            .padding(.top, FjordTokens.space3)
            .padding(.bottom, FjordTokens.space2)
            .frame(maxWidth: .infinity, minHeight: metrics.footer)
            .overlay(alignment: .top) {
                // The grab handle follows the pull a little, so the drag is felt before it is let go.
                Capsule()
                    .fill(Palette.onRoute.opacity(0.5))
                    .frame(width: 36, height: 4)
                    .padding(.top, 6)
                    .offset(y: min(max(pulled / 2, -8), 8))
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onToggle)
        .gesture(
            DragGesture(minimumDistance: 8)
                .onChanged { pulled = $0.translation.height }
                .onEnded { value in
                    let distance = value.translation.height
                    // A flick counts however short it was.
                    let flung = value.predictedEndTranslation.height
                    let opens = !expanded && (distance > Self.dragThreshold || flung > 4 * Self.dragThreshold)
                    let closes = expanded && (distance < -Self.dragThreshold || flung < -4 * Self.dragThreshold)
                    withAnimation(.easeOut(duration: 0.2)) { pulled = 0 }
                    if opens || closes { onToggle() }
                }
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(([label] + (spoken.isEmpty ? [] : [thenLabel + " " + spoken.joined(separator: "; ")])).joined(separator: ". "))
        .accessibilityValue(L(expanded ? "route_alternatives_expanded" : "route_alternatives_collapsed"))
        .accessibilityHint(L(expanded ? "route_fewer_description" : "route_more_description", destinationName))
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { onToggle() }
        .accessibilityIdentifier("routeAlternatives.toggle")
    }
}

/// The band of a card without alternatives beside cards with a band: "No later routes", so the
/// row's bands line up and the card says why it has no toggle.
struct NoteBand: View {
    let metrics: RouteCardMetrics
    var height: CGFloat?

    var body: some View {
        CardBand(metrics: metrics, height: height) {
            Text(L("route_no_later"))
                .font(metrics.caption.font)
                .foregroundColor(Palette.onRoute.opacity(0.78))
                .lineLimit(1)
                .padding(.horizontal, metrics.padH)
                .frame(maxWidth: .infinity, minHeight: metrics.footer, alignment: .leading)
        }
    }
}

/// The searched-for destination's band: its buttons (time, save, close) at the end.
struct ActionsBand<Actions: View>: View {
    let metrics: RouteCardMetrics
    var height: CGFloat?
    let actions: Actions

    var body: some View {
        CardBand(metrics: metrics, height: height) {
            actions
                .padding(.horizontal, metrics.padH - FjordTokens.space2)
                .frame(maxWidth: .infinity, minHeight: metrics.footer, alignment: .trailing)
        }
    }
}

/// The rail at a card's trailing edge when there is no room for its band (`RouteCardDensity.rail`):
/// as tall as the card, `RouteCardBands.RAIL_WIDTH_DP` wide, "+3" over a chevron. There is no room
/// to open the alternatives in the card then, so it opens the route search on the destination,
/// which lists them all, and leaves the setting as it is.
struct RouteRail: View {
    let count: Int
    let destinationName: String
    let metrics: RouteCardMetrics
    let onTap: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Text("+\(count)").font(metrics.label.font).lineLimit(1).fixedSize()
            CardIcon(name: "chevron.right", size: metrics.icon + 4)
        }
        .foregroundColor(Palette.onRoute)
        .frame(width: CGFloat(RouteCardBands.shared.RAIL_WIDTH_DP))
        .frame(minHeight: FjordTokens.touch, maxHeight: .infinity)
        .background(Palette.onRoute.opacity(cardBandTint))
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L("route_show_all_description", destinationName))
        .accessibilityAddTraits(.isButton)
        .accessibilityIdentifier("routeAlternatives.rail")
    }
}

/// A wide card's next departures beside its journey: "Then" and a row per departure, each a touch
/// target opening that route and saying as much as fits its width. As many rows as fit
/// `maxHeight` (`RouteCardFitting.nextBlockRows`); when that is not all of them, the "Then" line
/// becomes a target too, "+N in details", opening the route search on the destination.
struct NextBlock: View {
    let trips: [TripInfo]
    let metrics: RouteCardMetrics
    let measurer: CardMeasurer
    let width: CGFloat
    let maxHeight: CGFloat
    let nowMillis: Int64
    let onOpen: (TripInfo) -> Void
    let onMoreInDetails: () -> Void

    private static let padV = FjordTokens.space2

    var body: some View {
        let caption = measurer.lineHeight(metrics.caption)
        let headerHeights = (0 ... trips.count).map { hidden in hidden == 0 ? caption : max(FjordTokens.touch, caption, metrics.icon) }
        let rowHeight = max(FjordTokens.touch, NextDetail.height(metrics: metrics, measurer: measurer))
        let shown = Int(RouteCardFitting.shared.nextBlockRows(
            headerHeights: headerHeights.kotlin,
            rowHeights: trips.map { _ in rowHeight }.kotlin,
            maxHeight: Float(maxHeight - 2 * Self.padV)
        ))
        let hidden = trips.count - shown
        let available = width - 2 * metrics.padH
        VStack(alignment: .leading, spacing: 0) {
            header(hidden: hidden)
            ForEach(Array(trips.prefix(shown).enumerated()), id: \.offset) { _, trip in
                let detail = NextDetail.allCases.first { $0.width(trip, metrics: metrics, measurer: measurer, nowMillis: nowMillis) <= available } ?? .firstRide
                NextEntry(trip: trip, detail: detail, metrics: metrics, nowMillis: nowMillis)
                    .padding(.horizontal, metrics.padH)
                    .frame(width: width, alignment: .leading)
                    .frame(minHeight: FjordTokens.touch)
                    .contentShape(Rectangle())
                    .onTapGesture { onOpen(trip) }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(L("route_then") + " " + nextDepartureDescription(trip, nowMillis: nowMillis))
                    .accessibilityAddTraits(.isButton)
                    .accessibilityIdentifier("routeNext")
            }
        }
        .padding(.vertical, Self.padV)
        .frame(width: width)
    }

    @ViewBuilder
    private func header(hidden: Int) -> some View {
        let then = Text(L("route_then")).font(metrics.caption.font).foregroundColor(Palette.onRoute).frame(maxWidth: .infinity, alignment: .leading)
        if hidden > 0 {
            HStack(spacing: 0) {
                then.accessibilityHidden(true)
                Text(L("route_more_in_details", hidden)).font(metrics.caption.font).foregroundColor(Palette.onRoute).lineLimit(1).fixedSize()
                CardIcon(name: "chevron.right", size: metrics.icon)
            }
            .padding(.horizontal, metrics.padH)
            .frame(minHeight: FjordTokens.touch)
            .contentShape(Rectangle())
            .onTapGesture(perform: onMoreInDetails)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(L("route_more_in_details", hidden))
            .accessibilityAddTraits(.isButton)
        } else {
            then.padding(.horizontal, metrics.padH).accessibilityHidden(true)
        }
    }
}

/// The last row of an expanded card short of room: "+2 in details", which opens the route search
/// on that destination's routes, every one of them.
struct RouteMoreInDetailsRow: View {
    let hidden: Int
    let onTap: () -> Void

    var body: some View {
        HStack {
            Text(L("route_more_in_details", hidden))
                .font(.pulse(size: 14, weight: .semibold, relativeTo: .subheadline))
                .frame(maxWidth: .infinity, alignment: .leading)
            CardIcon(name: "chevron.right", size: 24)
        }
        .foregroundColor(Palette.onRoute)
        .padding(.horizontal, FjordTokens.space4)
        .padding(.vertical, FjordTokens.space2)
        .frame(minHeight: FjordTokens.touch)
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L("route_more_in_details", hidden))
        .accessibilityAddTraits(.isButton)
        .accessibilityIdentifier("routeAlternatives.more")
    }
}
