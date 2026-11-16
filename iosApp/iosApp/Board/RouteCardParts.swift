import Shared
import SwiftUI

// A route card's journey pieces, as Android's RouteCardParts.kt draws them: the wrapping chain of
// legs, the timeline, the meta line and a next departure. Each is planned from measured widths
// (`CardMeasurer`) by the shared `RouteCardFitting` before it is drawn.

// MARK: Chain

/// How a route's legs wrap as a chain in a width: "[walk] 4 min → [31] Jernbanetorget → …", never
/// cut short, only the first ride naming its boarding stop (`showsFirstFrom`), and the walks
/// without their minutes when that saves a line (`RouteCardFitting.chainShowsWalkMinutes`).
struct ChainPlan {
    let trip: TripInfo
    let legs: [TripLeg]
    let firstRide: Int?
    let showsFirstFrom: Bool
    let bare: Bool
    /// Each line's legs, by index.
    let rows: [[Int]]
    let itemWidths: [CGFloat]
    let lineHeight: CGFloat
    let available: CGFloat

    static let spacing = FjordTokens.space1

    init(trip: TripInfo, metrics: RouteCardMetrics, measurer m: CardMeasurer, available: CGFloat, showsFirstFrom: Bool = true) {
        self.trip = trip
        legs = trip.cardLegs
        let firstRide = legs.firstIndex { $0.lineName != nil }
        self.firstRide = firstRide
        self.showsFirstFrom = showsFirstFrom
        self.available = available
        let legs = legs
        func widths(bare: Bool) -> [CGFloat] {
            legs.enumerated().map { index, leg in
                var width: CGFloat = index > 0 ? metrics.icon * 0.85 + Self.spacing : 0
                if let line = leg.lineName {
                    width += m.pillSize(line, scale: metrics.pillScale).width
                    if showsFirstFrom, index == firstRide, let from = leg.fromName {
                        width += Self.spacing + m.width(from, metrics.body)
                    }
                } else {
                    width += metrics.icon
                    if !bare { width += Self.spacing + m.width(L("leg_walk", Int(leg.durationMinutes)), metrics.body) }
                }
                return width
            }
        }
        func flow(_ widths: [CGFloat]) -> [[Int]] {
            RouteCardFitting.shared.flowRows(widths: widths.kotlin, gap: Float(Self.spacing), available: Float(available))
                .map { $0.map { Int($0.int32Value) } }
        }
        let full = widths(bare: false)
        let fullRows = flow(full)
        let hasWalks = legs.contains { $0.lineName == nil }
        let bareWidths = widths(bare: true)
        let bareRows = flow(bareWidths)
        let showsMinutes = !hasWalks || RouteCardFitting.shared.chainShowsWalkMinutes(fullHeight: Float(fullRows.count), bareHeight: Float(bareRows.count))
        bare = !showsMinutes
        rows = showsMinutes ? fullRows : bareRows
        itemWidths = showsMinutes ? full : bareWidths
        let pillHeight = legs.compactMap(\.lineName).map { m.pillSize($0, scale: metrics.pillScale).height }.max() ?? 0
        lineHeight = max(pillHeight, metrics.icon, m.lineHeight(metrics.body))
    }

    /// As wide as its widest line, at most the width it has.
    var width: CGFloat {
        guard !legs.isEmpty else { return available }
        var widest: CGFloat = 0
        for row in rows {
            let items: CGFloat = row.map { itemWidths[$0] }.reduce(0, +)
            let gaps: CGFloat = Self.spacing * CGFloat(max(0, row.count - 1))
            widest = max(widest, items + gaps)
        }
        return min(widest, available)
    }
}

/// Draws a `ChainPlan`. Decorative for VoiceOver: the card's label reads the route.
struct LegChain: View {
    let plan: ChainPlan
    let metrics: RouteCardMetrics
    var color: Color = Palette.onRoute

    var body: some View {
        Group {
            if plan.legs.isEmpty {
                Text(L("fastest_route_info", plan.trip.lineName, plan.trip.originName))
                    .font(metrics.body.font)
                    .foregroundColor(color)
            } else {
                VStack(alignment: .leading, spacing: ChainPlan.spacing) {
                    ForEach(Array(plan.rows.enumerated()), id: \.offset) { _, row in
                        HStack(spacing: ChainPlan.spacing) {
                            ForEach(row, id: \.self) { index in
                                // Whole, unless wider than a line: then its stop name is cut short.
                                if plan.itemWidths[index] <= plan.available {
                                    item(index).fixedSize()
                                } else {
                                    item(index).frame(maxWidth: plan.available, alignment: .leading)
                                }
                            }
                        }
                        .frame(minHeight: plan.lineHeight)
                    }
                }
            }
        }
        .accessibilityHidden(true)
    }

    private func item(_ index: Int) -> some View {
        let leg = plan.legs[index]
        return HStack(spacing: ChainPlan.spacing) {
            if index > 0 { CardIcon(name: "arrow.right", size: metrics.icon * 0.85, color: color.opacity(0.7)) }
            if let line = leg.lineName {
                LinePill(line: line, mode: leg.mode, small: true, scale: metrics.pillScale)
                if plan.showsFirstFrom, index == plan.firstRide, let from = leg.fromName {
                    Text(from).font(metrics.body.font).foregroundColor(color).lineLimit(1)
                }
            } else {
                CardIcon(name: "figure.walk", size: metrics.icon, color: color)
                if !plan.bare {
                    Text(L("leg_walk", Int(leg.durationMinutes))).font(metrics.body.font).foregroundColor(color).lineLimit(1).fixedSize()
                }
            }
        }
    }
}

// MARK: Timeline

/// How a route is drawn across a width (`RouteCardFitting.timeline`): each ride a bar in its
/// line's colour from its pill, each walk a dotted stretch after a walker, as long as its share of
/// the minutes (at most `perMinute` points a minute when `capped`), the rides' boarding stops
/// under them whole or not at all; or, short of width, the chain.
struct TimelinePlan {
    let trip: TripInfo
    let legs: [TripLeg]
    let style: TimelineStyle
    let widths: [CGFloat]
    let captions: [CaptionChoice]
    let fullCaptions: [String]
    let shortCaptions: [String]
    let topHeight: CGFloat
    let captionHeight: CGFloat
    /// Set when the route wraps as a chain instead.
    let chain: ChainPlan?

    static let gap = FjordTokens.space1
    static let captionGap: CGFloat = 2
    /// The shortest a segment's bar or dots get.
    static let minBar: CGFloat = 12

    init(trip: TripInfo, metrics: RouteCardMetrics, measurer m: CardMeasurer, available: CGFloat, showsCaptions: Bool = true, capped: Bool = true) {
        self.trip = trip
        let legs = trip.cardLegs
        self.legs = legs
        let firstRide = legs.firstIndex { $0.lineName != nil }
        let platform = firstRide.flatMap { legs[$0].platform }.map { L("platform_label", $0) }
        fullCaptions = legs.enumerated().map { index, leg in
            leg.lineName == nil ? "" : [leg.fromName, index == firstRide ? platform : nil].compactMap { $0 }.joined(separator: " · ")
        }
        shortCaptions = legs.map { $0.lineName == nil ? "" : ($0.fromName ?? "") }
        let walkTexts = legs.map { L("leg_walk", Int($0.durationMinutes)) }
        func minWidths(bare: Bool) -> [CGFloat] {
            legs.enumerated().map { index, leg in
                if let line = leg.lineName { return m.pillSize(line, scale: metrics.pillScale).width + Self.minBar }
                return metrics.icon + (bare ? 0 : 2 + m.width(walkTexts[index], metrics.caption) + FjordTokens.space1) + Self.minBar
            }
        }
        if legs.isEmpty {
            style = .chain
            widths = []
            captions = []
            chain = ChainPlan(trip: trip, metrics: metrics, measurer: m, available: available)
        } else {
            let fit = RouteCardFitting.shared.timeline(
                fullMinWidths: minWidths(bare: false).kotlin,
                bareMinWidths: minWidths(bare: true).kotlin,
                weights: legs.map { KotlinFloat(value: Float($0.durationMinutes)) },
                isRide: legs.map { KotlinBoolean(value: $0.lineName != nil) },
                fullCaptionWidths: (showsCaptions ? fullCaptions.map { m.width($0, metrics.caption) } : legs.map { _ in 0 }).kotlin,
                shortCaptionWidths: (showsCaptions ? shortCaptions.map { m.width($0, metrics.caption) } : legs.map { _ in 0 }).kotlin,
                available: Float(available),
                gap: Float(Self.gap),
                maxPerWeight: capped ? Float(metrics.perMinute) : .infinity,
                withCaptions: showsCaptions,
                captionMinWidth: RouteCardFitting.shared.CAPTION_MIN_WIDTH_DP
            )
            style = fit.style
            widths = fit.widths.points.map { floor($0) }
            captions = fit.captions
            chain = fit.style == .chain ? ChainPlan(trip: trip, metrics: metrics, measurer: m, available: available) : nil
        }
        let captionLine = m.lineHeight(metrics.caption)
        topHeight = legs.map { leg in
            if let line = leg.lineName { return m.pillSize(line, scale: metrics.pillScale).height }
            return max(metrics.icon, captionLine, metrics.track)
        }.max() ?? 0
        captionHeight = captions.contains { $0 != .none } ? captionLine : 0
    }

    /// As wide as its segments (or its chain).
    var width: CGFloat {
        if let chain { return chain.width }
        return widths.reduce(0, +) + Self.gap * CGFloat(max(0, widths.count - 1))
    }
}

/// Draws a `TimelinePlan`. Decorative for VoiceOver: the card's label reads the route.
struct RouteTimeline: View {
    let plan: TimelinePlan
    let metrics: RouteCardMetrics
    var color: Color = Palette.onRoute

    var body: some View {
        Group {
            if let chain = plan.chain {
                LegChain(plan: chain, metrics: metrics, color: color)
            } else {
                VStack(alignment: .leading, spacing: TimelinePlan.captionGap) {
                    HStack(spacing: TimelinePlan.gap) {
                        ForEach(Array(plan.legs.enumerated()), id: \.offset) { index, leg in
                            segment(leg)
                                .frame(width: plan.widths[index], height: plan.topHeight)
                        }
                    }
                    if plan.captionHeight > 0 {
                        ZStack(alignment: .topLeading) {
                            ForEach(Array(plan.legs.indices), id: \.self) { index in
                                if let text = caption(index) {
                                    Text(text)
                                        .font(metrics.caption.font)
                                        .foregroundColor(color)
                                        .lineLimit(1)
                                        .fixedSize()
                                        .padding(.leading, start(index))
                                }
                            }
                        }
                        .frame(width: plan.width, height: plan.captionHeight, alignment: .topLeading)
                    }
                }
                .frame(width: plan.width, alignment: .leading)
            }
        }
        .accessibilityHidden(true)
    }

    private func start(_ index: Int) -> CGFloat {
        plan.widths.prefix(index).reduce(0, +) + TimelinePlan.gap * CGFloat(index)
    }

    private func caption(_ index: Int) -> String? {
        switch plan.captions[index] {
        case .full: return plan.fullCaptions[index]
        case .short: return plan.shortCaptions[index]
        default: return nil
        }
    }

    @ViewBuilder
    private func segment(_ leg: TripLeg) -> some View {
        if let line = leg.lineName {
            HStack(spacing: 0) {
                LinePill(line: line, mode: leg.mode, small: true, scale: metrics.pillScale)
                TrailingRoundedBar()
                    .fill(Palette.transport(leg.mode))
                    .frame(minWidth: TimelinePlan.minBar, maxWidth: .infinity)
                    .frame(height: metrics.track)
            }
        } else {
            HStack(spacing: 0) {
                CardIcon(name: "figure.walk", size: metrics.icon, color: color)
                if plan.style == .full {
                    Text(L("leg_walk", Int(leg.durationMinutes)))
                        .font(metrics.caption.font)
                        .foregroundColor(color)
                        .lineLimit(1)
                        .fixedSize()
                        .padding(.leading, 2)
                        .padding(.trailing, FjordTokens.space1)
                }
                WalkDots()
                    .fill(color.opacity(0.7))
                    .frame(minWidth: TimelinePlan.minBar, maxWidth: .infinity)
                    .frame(height: metrics.track)
            }
        }
    }
}

// MARK: Meta line

/// "in 6 min · 15 min · arr. 19:32", wrapping only between its parts onto at most `maxLines`
/// lines; when that is not enough the duration goes, then the arrival (`RouteCardFitting.metaParts`).
struct MetaLine: View {
    let parts: [String]
    let metrics: RouteCardMetrics
    let rows: [[Int]]

    static let gap = FjordTokens.space1

    init(parts: [String], metrics: RouteCardMetrics, measurer m: CardMeasurer, available: CGFloat, maxLines: Int) {
        self.parts = parts
        self.metrics = metrics
        let widths = parts.map { m.width("\($0) ·", metrics.body) }
        let kept = RouteCardFitting.shared.metaParts(
            widths: widths.kotlin,
            gap: Float(Self.gap),
            available: Float(available),
            maxLines: Int32(maxLines),
            dropOrder: RouteCardFitting.shared.META_DROP_ORDER.filter { Int($0.int32Value) < parts.count }
        ).map { Int($0.int32Value) }
        rows = RouteCardFitting.shared.flowRows(widths: kept.map { widths[$0] }.kotlin, gap: Float(Self.gap), available: Float(available))
            .map { row in row.map { kept[Int($0.int32Value)] } }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                HStack(spacing: Self.gap) {
                    ForEach(Array(row.enumerated()), id: \.offset) { position, index in
                        // A part ending a line or the row drops its separator.
                        Text(position == row.count - 1 ? parts[index] : "\(parts[index]) ·")
                            .lineLimit(1)
                            .fixedSize()
                    }
                }
            }
        }
        .font(metrics.body.font)
        .foregroundColor(Palette.onRoute)
    }
}

// MARK: Next departures

/// How much a next departure shows, richest first.
enum NextDetail: CaseIterable {
    /// "18:12 in 17 min [37] › [4] › [11] arr. 19:08".
    case full
    /// "18:12 [37] › [4] › [11]": every ride's pill.
    case rides
    /// "18:12 [37]": the clock and the first ride's pill.
    case firstRide

    static let spacing = FjordTokens.space2
    static let pillSpacing: CGFloat = 2

    /// How wide `trip` is drawn at this detail.
    func width(_ trip: TripInfo, metrics: RouteCardMetrics, measurer m: CardMeasurer, nowMillis: Int64) -> CGFloat {
        let rides = trip.cardLegs.compactMap(\.lineName)
        let shown = self == .firstRide ? Array(rides.prefix(1)) : rides
        var parts: [CGFloat] = [m.width(isoClockTime(trip.expectedStartTime), metrics.secondClock)]
        if self == .full, let soon = routeLeavesIn(trip.expectedStartTime, nowMillis: nowMillis) { parts.append(m.width(soon, metrics.caption)) }
        if !shown.isEmpty {
            parts.append(
                shown.map { m.pillSize($0, scale: metrics.pillScale).width }.reduce(0, +)
                    + CGFloat(shown.count - 1) * (metrics.icon * 0.8 + 2 * Self.pillSpacing)
            )
        }
        if self == .full, let arrival = trip.arrivalClock { parts.append(m.width(L("route_arrival", arrival), metrics.caption)) }
        return parts.reduce(0, +) + Self.spacing * CGFloat(parts.count - 1)
    }

    /// How tall a next departure is drawn.
    static func height(metrics: RouteCardMetrics, measurer m: CardMeasurer) -> CGFloat {
        max(m.lineHeight(metrics.secondClock), m.pillSize("0", scale: metrics.pillScale).height, m.lineHeight(metrics.caption))
    }
}

/// One next departure at `detail`. Decorative for VoiceOver: the row or bar around it reads it.
struct NextEntry: View {
    let trip: TripInfo
    let detail: NextDetail
    let metrics: RouteCardMetrics
    let nowMillis: Int64
    var color: Color = Palette.onRoute

    var body: some View {
        let rides = trip.cardLegs.filter { $0.lineName != nil }
        let shown = detail == .firstRide ? Array(rides.prefix(1)) : rides
        HStack(spacing: NextDetail.spacing) {
            Text(isoClockTime(trip.expectedStartTime)).font(metrics.secondClock.font)
            if detail == .full, let soon = routeLeavesIn(trip.expectedStartTime, nowMillis: nowMillis) {
                Text(soon).font(metrics.caption.font)
            }
            if !shown.isEmpty {
                HStack(spacing: NextDetail.pillSpacing) {
                    ForEach(Array(shown.enumerated()), id: \.offset) { index, leg in
                        if index > 0 { CardIcon(name: "chevron.right", size: metrics.icon * 0.8, color: color.opacity(0.78)) }
                        LinePill(line: leg.lineName ?? "", mode: leg.mode, small: true, scale: metrics.pillScale)
                    }
                }
            }
            if detail == .full, let arrival = trip.arrivalClock {
                Text(L("route_arrival", arrival)).font(metrics.caption.font)
            }
        }
        .foregroundColor(color)
        .lineLimit(1)
        .fixedSize()
        .accessibilityHidden(true)
    }
}
