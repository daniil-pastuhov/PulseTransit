import Shared
import SwiftUI

// One destination's card on the wall board, as Android's RouteCard.kt draws it: its journey (or
// why it has none), its expanded alternatives and its band, or its rail. What the card shows is
// decided by the board (`BoardRouteCards`) from the shared rules; this draws it.

/// What the route cards can do, wired by the board to the ViewModel.
struct RouteCardActions {
    /// Opens a route's details: the destination's id and the tapped route.
    var onOpenTrip: (_ destinationId: String, _ trip: TripInfo) -> Void = { _, _ in }
    /// Opens or closes a saved destination's alternatives (a setting, by destination id).
    var onToggleAlternatives: (_ destinationId: String) -> Void = { _ in }
    /// "+N in details" and the rail: opens the route search on that destination.
    var onMoreInDetails: (_ destination: StopPlace) -> Void = { _ in }
    /// The searched-for destination's time ("leave at", "arrive by").
    var onTimeChange: (TripTime) -> Void = { _ in }
    var onSave: () -> Void = {}
    var onDismissTemporary: () -> Void = {}
}

/// What the board decides about one route card before drawing it, the same at every density.
struct RouteCardSpec {
    let destination: StopPlace
    let size: RouteCardSize
    /// A compact card under `RouteCardLayout.NARROW_CARD_MAX_WIDTH_DP`: its title on a line of its own.
    let narrow: Bool
    /// Its band at `RouteCardDensity.full`, from `RouteCardBands.footers`.
    let footer: RouteCardFooter
    /// Its alternatives still to leave, after the first route (none for the searched-for one).
    let alternatives: [TripInfo]
    /// Whether its alternatives are open (the setting).
    let expanded: Bool
    let width: CGFloat

    var metrics: RouteCardMetrics { RouteCardMetrics.of(size) }
}

/// How a drawn card fills its grid row (`RouteCardLayout.stretch`): its height, its journey's
/// band, each shown row's height (its own plus its share of what is left) and its band's height.
struct CardFill {
    let height: CGFloat
    let headBand: CGFloat
    let rowHeights: [CGFloat]
    let footerBand: CGFloat
}

/// One destination's card at `density`. `fill` is nil for the copies the board only measures:
/// they take their natural height and report their journey's, their band's and their own height
/// as `probeKey`, and leave out their animations.
struct RouteCardView: View {
    let spec: RouteCardSpec
    let state: StopBoardViewModel.UiState
    let density: RouteCardDensity
    let visible: VisibleAlternatives
    let nowMillis: Int64
    let measurer: CardMeasurer
    let actions: RouteCardActions
    /// What a wide card's next departures block may take of the height.
    let nextBlockMax: CGFloat
    var fill: CardFill?
    var probeKey: String?

    private var trip: TripInfo? { state.fastestTrips[spec.destination.id]?.first }
    private var metrics: RouteCardMetrics { spec.metrics }
    private var isTemporary: Bool { spec.footer == .actions }

    var body: some View {
        let trip = trip
        let rail = trip != nil && RouteCardBands.shared.hasRail(
            footer: spec.footer, size: spec.size, hasAlternatives: !spec.alternatives.isEmpty, density: density
        )
        let showsBand = RouteCardBands.shared.showsBand(footer: spec.footer, density: density)
        let trailingActions = isTemporary && !showsBand
        let railWidth = rail ? CGFloat(RouteCardBands.shared.RAIL_WIDTH_DP) : 0
        let contentWidth = max(0, spec.width - railWidth - (trailingActions ? temporaryActionsWidth : 0))
        let showsRows = density == .full && spec.footer == .toggle && spec.expanded && visible.showsRows
        let shownRows = showsRows ? min(Int(visible.shown), spec.alternatives.count) : 0
        let isRefreshing = fill != nil && state.loadingTripDestinationIds.contains(spec.destination.id)
            && (trip != nil || state.failedTripDestinationIds.contains(spec.destination.id))

        HStack(spacing: 0) {
            VStack(spacing: 0) {
                head(trip: trip, width: contentWidth)
                    .frame(width: contentWidth)
                    .frame(height: fill?.headBand)
                    .overlay(alignment: .bottom) {
                        if isRefreshing {
                            RefreshBar(color: (trip != nil ? Palette.onRoute : Palette.textPrimary).opacity(0.6))
                                .padding(.horizontal, FjordTokens.radiusLg)
                        }
                    }
                    .background(probe("head"))
                ForEach(Array(spec.alternatives.prefix(shownRows).enumerated()), id: \.offset) { index, alternative in
                    rowFrame(index) {
                        AlternativeRow(trip: alternative, size: spec.size, measurer: measurer, width: contentWidth, nowMillis: nowMillis) {
                            actions.onOpenTrip(spec.destination.id, alternative)
                        }
                    }
                }
                if showsRows && visible.showsMoreRow {
                    rowFrame(shownRows) {
                        RouteMoreInDetailsRow(hidden: Int(visible.hidden)) { actions.onMoreInDetails(spec.destination) }
                    }
                }
                if fill != nil { Spacer(minLength: 0) }
                if showsBand && (trip != nil || isTemporary) {
                    band(trip: trip, width: contentWidth).background(probe("footer"))
                }
            }
            .frame(width: contentWidth)
            if trailingActions {
                temporaryActions.fixedSize()
            }
            if rail {
                RouteRail(count: spec.alternatives.count, destinationName: spec.destination.name, metrics: metrics) {
                    actions.onMoreInDetails(spec.destination)
                }
            }
        }
        .frame(width: spec.width, height: fill?.height)
        .background(trip != nil ? Palette.route : Palette.surfaceContainer)
        .clipShape(RoundedRectangle(cornerRadius: FjordTokens.radiusLg))
        .background(probe("total"))
        .accessibilityHidden(probeKey != nil)
    }

    /// Reports `part`'s height when this card is a measuring copy.
    @ViewBuilder
    private func probe(_ part: String) -> some View {
        if let probeKey {
            GeometryReader { geometry in
                Color.clear.preference(key: CardProbeKey.self, value: ["\(probeKey)/\(part)": geometry.size.height])
            }
        }
    }

    /// An alternative row with its divider, `fill`'s height for it when drawn.
    private func rowFrame<Row: View>(_ index: Int, @ViewBuilder row: () -> Row) -> some View {
        VStack(spacing: 0) {
            Rectangle().fill(Palette.onRoute.opacity(cardDividerAlpha)).frame(height: 1).padding(.horizontal, metrics.padH)
            row()
        }
        .frame(height: fill.flatMap { index < $0.rowHeights.count ? $0.rowHeights[index] : nil })
    }

    // MARK: Head

    @ViewBuilder
    private func head(trip: TripInfo?, width: CGFloat) -> some View {
        if let trip {
            CardHead(
                trip: trip,
                spec: spec,
                density: density,
                measurer: measurer,
                width: width,
                nowMillis: nowMillis,
                nextBlockMax: nextBlockMax,
                onOpen: { actions.onOpenTrip(spec.destination.id, trip) },
                onOpenNext: { actions.onOpenTrip(spec.destination.id, $0) },
                onMoreInDetails: { actions.onMoreInDetails(spec.destination) }
            )
        } else {
            CardMessage(destination: spec.destination, state: state, metrics: metrics, measurer: measurer, width: width, density: density, live: fill != nil)
        }
    }

    // MARK: Bands

    @ViewBuilder
    private func band(trip: TripInfo?, width: CGFloat) -> some View {
        switch spec.footer {
        case .toggle:
            ToggleBand(
                expanded: spec.expanded,
                count: spec.alternatives.count,
                // Collapsed, or expanded with no room for a row: the bar names what comes next.
                next: !spec.expanded || visible.showsThenLine ? spec.alternatives : [],
                destinationName: spec.destination.name,
                metrics: metrics,
                measurer: measurer,
                width: width,
                nowMillis: nowMillis,
                height: fill?.footerBand,
                onToggle: { actions.onToggleAlternatives(spec.destination.id) }
            )
        case .note:
            NoteBand(metrics: metrics, height: fill?.footerBand)
        case .actions:
            ActionsBand(metrics: metrics, height: fill?.footerBand, actions: temporaryActions)
        default:
            EmptyView()
        }
    }

    private var temporaryActions: TemporaryRouteActions {
        TemporaryRouteActions(
            time: state.temporaryTripTime,
            onTimeChange: actions.onTimeChange,
            canSave: state.canSaveTemporaryDestination,
            onSave: actions.onSave,
            onDismiss: actions.onDismissTemporary,
            tint: trip != nil ? Palette.onRoute : Palette.textPrimary
        )
    }

    /// About how wide the searched-for destination's buttons are at the end of its line: its
    /// time, then save and close, as `TemporaryRouteActions` lays them out.
    private var temporaryActionsWidth: CGFloat {
        let buttons: CGFloat = state.canSaveTemporaryDestination ? 2 : 1
        let time: CGFloat
        if let atMillis = tripTimeMillis(state.temporaryTripTime) {
            let label = CardFont(size: 11, weight: .regular, style: .caption2)
            let clock = CardFont(size: 15, weight: .bold, style: .subheadline)
            time = max(
                measurer.width(L(state.temporaryTripTime is TripTimeArriveBy ? "trip_time_arrive" : "trip_time_depart"), label),
                measurer.width(TripTimeKt.clockTimeText(atMillis: atMillis), clock) + 16
            ) + 8
        } else {
            time = FjordTokens.touch
        }
        return time + buttons * FjordTokens.touch + buttons * 4 + 8
    }
}

/// Each measured part's height, by "<probe key>/<part>".
struct CardProbeKey: PreferenceKey {
    static var defaultValue: [String: CGFloat] = [:]
    static func reduce(value: inout [String: CGFloat], nextValue: () -> [String: CGFloat]) {
        value.merge(nextValue()) { $1 }
    }
}

// MARK: Journey

/// The card's journey at `density`. Compact cards (and every card at `RouteCardDensity.line`)
/// are one target opening the route; a timeline card's target is its leave, journey and arrival
/// blocks only, its next departures block having targets of its own. VoiceOver reads the target as
/// one sentence: "To Majorstuen, Leave 19:43, in 5 min, 15 min, arr. 19:58".
private struct CardHead: View {
    let trip: TripInfo
    let spec: RouteCardSpec
    let density: RouteCardDensity
    let measurer: CardMeasurer
    let width: CGFloat
    let nowMillis: Int64
    let nextBlockMax: CGFloat
    let onOpen: () -> Void
    let onOpenNext: (TripInfo) -> Void
    let onMoreInDetails: () -> Void

    private var metrics: RouteCardMetrics { spec.metrics }
    private var title: String { L("route_to", trip.destinationName) }
    private var leave: String { isoClockTime(trip.expectedStartTime) }
    private var leavesIn: String? { routeLeavesIn(trip.expectedStartTime, nowMillis: nowMillis) }
    private var duration: String { tripDuration(trip.durationMinutes) }
    private var arrivalText: String? { trip.arrivalClock.map { L("route_arrival", $0) } }

    private var spoken: String {
        [title, "\(L("route_leave")) \(leave)", leavesIn, duration, arrivalText].compactMap { $0 }.joined(separator: ", ")
    }

    var body: some View {
        if density == .line {
            target(lineHead)
        } else if spec.size == .compact {
            target(compactHead)
        } else {
            journeyRow
        }
    }

    private func target<Content: View>(_ content: Content) -> some View {
        content
            .contentShape(Rectangle())
            .onTapGesture(perform: onOpen)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(spoken)
            .accessibilityAddTraits(.isButton)
            .accessibilityIdentifier("fastestTrip")
    }

    /// One line: the clock, the title ellipsised, and how soon it leaves only when the whole title
    /// fits beside it (`RouteCardFitting.lineShowsSoon`).
    private var lineHead: some View {
        let available = width - 2 * metrics.padH
        let gap = FjordTokens.space3
        let showsSoon = leavesIn.map {
            RouteCardFitting.shared.lineShowsSoon(
                clockWidth: Float(measurer.width(leave, metrics.lineClock)),
                titleWidth: Float(measurer.width(title, metrics.title)),
                soonWidth: Float(measurer.width($0, metrics.body)),
                gap: Float(gap),
                available: Float(available)
            )
        } ?? false
        return HStack(spacing: gap) {
            Text(leave).font(metrics.lineClock.font).foregroundColor(Palette.routeAccent).lineLimit(1).fixedSize()
            Text(title).font(metrics.title.font).foregroundColor(Palette.onRoute).lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            if showsSoon, let leavesIn {
                Text(leavesIn).font(metrics.body.font).foregroundColor(Palette.onRoute).lineLimit(1).fixedSize()
            }
        }
        .padding(.horizontal, metrics.padH)
        .padding(.vertical, FjordTokens.space2)
        .frame(width: width)
        .frame(minHeight: FjordTokens.touch)
    }

    /// The clock with the title and "in 6 min · 15 min · arr. 19:32" beside it, over the route as a
    /// wrapping chain; on a narrow card the title gets a line of its own over the clock.
    private var compactHead: some View {
        let lines = density == .short ? 1 : 2
        let inner = width - 2 * metrics.padH
        let besideClock = inner - measurer.width(leave, metrics.clock) - FjordTokens.space3
        let titleText = Text(title).font(metrics.title.font).foregroundColor(Palette.onRoute).lineLimit(lines)
        return VStack(alignment: .leading, spacing: FjordTokens.space2) {
            if spec.narrow { titleText.fixedSize(horizontal: false, vertical: true) }
            HStack(alignment: .center, spacing: FjordTokens.space3) {
                Text(leave).font(metrics.clock.font).foregroundColor(Palette.routeAccent).lineLimit(1).fixedSize()
                VStack(alignment: .leading, spacing: 0) {
                    if !spec.narrow { titleText.fixedSize(horizontal: false, vertical: true) }
                    MetaLine(
                        parts: [leavesIn, duration, arrivalText].compactMap { $0 },
                        metrics: metrics,
                        measurer: measurer,
                        available: besideClock,
                        maxLines: lines
                    )
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            if density != .short {
                LegChain(
                    plan: ChainPlan(trip: trip, metrics: metrics, measurer: measurer, available: inner, showsFirstFrom: !spec.narrow),
                    metrics: metrics
                )
            }
        }
        .padding(.horizontal, metrics.padH)
        .padding(.vertical, metrics.padV)
        .frame(width: width, alignment: .leading)
    }

    /// A timeline card's row: "Leave 19:17 in 6 min", the title over the timeline, "Arrive 19:32
    /// 15 min", then on a wide card a divider and its next departures block, which gets what the
    /// capped timeline leaves (at least `NEXT_BLOCK_MIN_WIDTH_DP` times the pill scale).
    private var journeyRow: some View {
        let labelled = density != .short
        let next = spec.size.nextInHead ? spec.alternatives : []
        let hasNext = !next.isEmpty
        let gap = metrics.gap
        let leaveLabel = L("route_leave")
        let arriveLabel = L("route_arrive")
        let arrival = trip.arrivalClock ?? "--:--"
        let leaveWidth = metrics.padH + [
            labelled ? measurer.width(leaveLabel, metrics.caption) : 0,
            measurer.width(leave, metrics.clock),
            leavesIn.map { measurer.width($0, metrics.body) } ?? 0,
        ].max()!
        let arriveWidth = metrics.padH + [
            labelled ? measurer.width(arriveLabel, metrics.caption) : 0,
            measurer.width(arrival, metrics.secondClock),
            measurer.width(duration, metrics.body),
        ].max()!
        let nextMin = hasNext ? CGFloat(RouteCardFitting.shared.NEXT_BLOCK_MIN_WIDTH_DP) * metrics.pillScale + 1 : 0
        let journeyMax = max(0, width - leaveWidth - arriveWidth - 2 * gap - nextMin)
        let timeline = TimelinePlan(trip: trip, metrics: metrics, measurer: measurer, available: journeyMax, showsCaptions: labelled, capped: hasNext)
        let journeyWidth = hasNext ? min(journeyMax, max(measurer.width(title, metrics.title), timeline.width)) : journeyMax
        let journeyTotal = leaveWidth + gap + journeyWidth + gap + arriveWidth
        let nextWidth = max(0, width - journeyTotal - 1)
        // The next block may be as tall as the journey, or as the card's share of the board allows.
        let leaveHeight = 2 * metrics.padV + (labelled ? measurer.lineHeight(metrics.caption) : 0)
            + measurer.lineHeight(metrics.clock) + measurer.lineHeight(metrics.body)
        let row = HStack(alignment: .center, spacing: 0) {
            HStack(alignment: .center, spacing: gap) {
                VStack(alignment: .leading, spacing: 0) {
                    if labelled { Text(leaveLabel).font(metrics.caption.font).foregroundColor(Palette.onRoute) }
                    Text(leave).font(metrics.clock.font).foregroundColor(Palette.routeAccent).fixedSize()
                    if let leavesIn { Text(leavesIn).font(metrics.body.font).foregroundColor(Palette.onRoute) }
                }
                .lineLimit(1)
                .padding(.leading, metrics.padH)
                .padding(.vertical, metrics.padV)
                .frame(width: leaveWidth, alignment: .leading)
                VStack(alignment: .leading, spacing: FjordTokens.space2) {
                    Text(title).font(metrics.title.font).foregroundColor(Palette.onRoute).lineLimit(1)
                    RouteTimeline(plan: timeline, metrics: metrics)
                }
                .padding(.vertical, metrics.padV)
                .frame(width: journeyWidth, alignment: .leading)
                VStack(alignment: .trailing, spacing: 0) {
                    if labelled { Text(arriveLabel).font(metrics.caption.font) }
                    Text(arrival).font(metrics.secondClock.font).fixedSize()
                    Text(duration).font(metrics.body.font).fixedSize()
                }
                .foregroundColor(Palette.onRoute)
                .lineLimit(1)
                .padding(.trailing, metrics.padH)
                .padding(.vertical, metrics.padV)
                .frame(width: arriveWidth, alignment: .trailing)
            }
            .frame(width: hasNext ? journeyTotal : width, alignment: .leading)
            .frame(maxHeight: .infinity)
            .contentShape(Rectangle())
            .onTapGesture(perform: onOpen)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(spoken)
            .accessibilityAddTraits(.isButton)
            .accessibilityIdentifier("fastestTrip")
            if hasNext {
                // The divider is drawn over this gap, as tall as the row.
                Color.clear.frame(width: 1, height: 1)
                NextBlock(
                    trips: next,
                    metrics: metrics,
                    measurer: measurer,
                    width: nextWidth,
                    maxHeight: max(leaveHeight, nextBlockMax),
                    nowMillis: nowMillis,
                    onOpen: onOpenNext,
                    onMoreInDetails: onMoreInDetails
                )
            }
        }
        .fixedSize(horizontal: false, vertical: true)
        return row
            .overlay(alignment: .topLeading) {
                if hasNext {
                    Rectangle()
                        .fill(Palette.onRoute.opacity(cardDividerAlpha))
                        .frame(width: 1)
                        .padding(.vertical, metrics.padV)
                        .offset(x: journeyTotal)
                }
            }
            .frame(width: width)
    }
}

// MARK: No route

/// A card without a route, muted: still loading (skeletons), failed (retrying by itself), or none
/// found. At `RouteCardDensity.line` only its title, on one line.
private struct CardMessage: View {
    let destination: StopPlace
    let state: StopBoardViewModel.UiState
    let metrics: RouteCardMetrics
    let measurer: CardMeasurer
    let width: CGFloat
    let density: RouteCardDensity
    let live: Bool

    @State private var pulse = false

    var body: some View {
        let noRouteTime = state.noRouteTime(destinationId: destination.id)
        let failed = state.failedTripDestinationIds.contains(destination.id)
        let symbol: String?
        let title: String
        let detail: String?
        if failed {
            symbol = loadFailureSymbol(isOnline: state.isOnline)
            title = L("fastest_route_unavailable", destination.name)
            detail = L("error_retrying_automatically")
        } else if let noRouteTime {
            let arriveBy = noRouteTime as? TripTimeArriveBy
            symbol = arriveBy != nil ? "clock" : "magnifyingglass"
            title = arriveBy.map { L("no_route_to_arriving", destination.name, tripClockText($0.atMillis)) } ?? L("no_route_to", destination.name)
            detail = nil
        } else {
            symbol = nil
            title = L("loading_fastest_route", destination.name)
            detail = nil
        }
        let line = density == .line
        let iconSize = line ? metrics.icon + 4 : metrics.icon * 1.6
        let iconWidth = symbol == nil ? iconSize * 1.5 : iconSize
        let column = width - 2 * metrics.padH - iconWidth - FjordTokens.space3
        let skeleton = Palette.textPrimary.opacity(0.12)
        return HStack(spacing: FjordTokens.space3) {
            if let symbol {
                CardIcon(name: symbol, size: iconSize, color: Palette.textSecondary)
            } else {
                PlaceholderBlock(width: iconWidth, height: iconSize, color: skeleton, pulse: pulse)
            }
            VStack(alignment: .leading, spacing: FjordTokens.space1) {
                Text(title)
                    .font(metrics.title.font)
                    .foregroundColor(Palette.textPrimary)
                    .lineLimit(line ? 1 : 2)
                    .fixedSize(horizontal: false, vertical: true)
                if !line && density != .short {
                    if let detail {
                        Text(detail).font(metrics.body.font).foregroundColor(Palette.textSecondary).lineLimit(2)
                    }
                    if symbol == nil {
                        PlaceholderBlock(width: max(0, column * 0.6), height: 12, color: skeleton, pulse: pulse)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, metrics.padH)
        .padding(.vertical, line ? FjordTokens.space2 : metrics.padV)
        .frame(width: width)
        .frame(minHeight: FjordTokens.touch)
        .modifier(LivePulse(live: live && symbol == nil, pulse: $pulse))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([title, detail].compactMap { $0 }.joined(separator: ". "))
    }
}

/// Starts a skeleton's pulse only on a card that is drawn, not on the board's measuring copies.
private struct LivePulse: ViewModifier {
    let live: Bool
    @Binding var pulse: Bool

    @ViewBuilder
    func body(content: Content) -> some View {
        if live { content.modifier(PlaceholderPulse(pulse: $pulse)) } else { content }
    }
}

// MARK: Alternatives

/// An expanded card's alternative: its clock and how soon, the route (a smaller timeline on a
/// timeline card, the wrapping chain on a compact one), the duration and arrival. Opens the route.
struct AlternativeRow: View {
    let trip: TripInfo
    let size: RouteCardSize
    let measurer: CardMeasurer
    let width: CGFloat
    let nowMillis: Int64
    let onTap: () -> Void

    var body: some View {
        let metrics = RouteCardMetrics.of(size)
        var rowMetrics = RouteCardMetrics.of(.compact)
        rowMetrics.padH = metrics.padH
        rowMetrics.padV = metrics.padV
        rowMetrics.gap = metrics.gap
        let clock = isoClockTime(trip.expectedStartTime)
        let leavesIn = routeLeavesIn(trip.expectedStartTime, nowMillis: nowMillis)
        let duration = tripDuration(trip.durationMinutes)
        let arrival = trip.arrivalClock.map { L("route_arrival", $0) }
        let leftWidth = max(measurer.width(clock, metrics.secondClock), leavesIn.map { measurer.width($0, metrics.caption) } ?? 0)
        let rightWidth = max(measurer.width(duration, metrics.label), arrival.map { measurer.width($0, metrics.caption) } ?? 0)
        let middle = max(0, width - 2 * metrics.padH - leftWidth - rightWidth - 2 * FjordTokens.space3)
        return HStack(spacing: FjordTokens.space3) {
            VStack(alignment: .leading, spacing: 0) {
                Text(clock).font(metrics.secondClock.font)
                if let leavesIn { Text(leavesIn).font(metrics.caption.font) }
            }
            .lineLimit(1)
            .fixedSize()
            Group {
                if size == .compact {
                    LegChain(plan: ChainPlan(trip: trip, metrics: rowMetrics, measurer: measurer, available: middle, showsFirstFrom: false), metrics: rowMetrics)
                } else {
                    RouteTimeline(plan: TimelinePlan(trip: trip, metrics: rowMetrics, measurer: measurer, available: middle), metrics: rowMetrics)
                }
            }
            .frame(width: middle, alignment: .leading)
            VStack(alignment: .trailing, spacing: 0) {
                Text(duration).font(metrics.label.font)
                if let arrival { Text(arrival).font(metrics.caption.font) }
            }
            .lineLimit(1)
            .fixedSize()
        }
        .foregroundColor(Palette.onRoute)
        .padding(.horizontal, metrics.padH)
        .padding(.vertical, FjordTokens.space2)
        .frame(width: width)
        .frame(minHeight: FjordTokens.touch)
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([clock, leavesIn, duration, arrival].compactMap { $0 }.joined(separator: ", "))
        .accessibilityAddTraits(.isButton)
        .accessibilityIdentifier("routeAlternative")
    }
}
