import Shared
import SwiftUI

/// The wall board's route cards above the stop panels, as on Android (routecards/BoardBody.kt): a
/// card per destination in `routes` (the searched-for one first), as a grid laid out from the
/// `width` the board's body has, never the device's. Every rule is the shared
/// `RouteCardLayout`'s: the columns, each card's size class from its own width (at the text size
/// Dynamic Type sets), its band (`RouteCardBands`), one density for all cards, the richest whose
/// collapsed cards fit `maxHeight` (what leaves the panels `BoardLayout.MIN_PANEL_HEIGHT_DP`),
/// the expanded cards' rows that fit (`BoardRouteAlternatives.visibleRows`), and equal heights
/// per grid row (`stretch`). Only when not even one line a card fits does the area scroll.
///
/// The cards' heights are measured as drawn: each card is laid out out of sight at every density
/// (and an expanded one's rows), and reports its heights; until they are all in, no card shows, so
/// the first frame never squeezes the panels. Nothing here collapses a card.
struct BoardRouteCards: View {
    let state: StopBoardViewModel.UiState
    let routes: [StopPlace]
    let width: CGFloat
    /// The height the cards may take: the body's, less the gap above the panels and their minimum.
    let maxHeight: CGFloat
    let nowMillis: Int64
    let actions: RouteCardActions

    @Environment(\.dynamicTypeSize) private var typeSize
    /// The measuring copies' heights, by "<density>/<destination id>/<part>" and "rows/<id>/<row>".
    @State private var measured: [String: CGFloat] = [:]
    /// Where the bottom of the cards is while they scroll, for the fade.
    @State private var scrolledBottom: CGFloat = 0

    static let cardGap = FjordTokens.space3

    var body: some View {
        let measurer = CardMeasurer(typeSize)
        let specs = makeSpecs(measurer: measurer)
        let columns = max(1, Int(RouteCardLayout.shared.columns(
            count: Int32(routes.count), widthDp: Float(width), gapDp: Float(Self.cardGap), fontScale: Float(measurer.fontScale)
        )))
        let gridRows = stride(from: 0, to: specs.count, by: columns).map { Array($0 ..< min($0 + columns, specs.count)) }
        let nextBlockMax = max(0, (maxHeight - Self.cardGap * CGFloat(max(0, gridRows.count - 1))) / CGFloat(max(1, gridRows.count)))
        let densities = RouteCardBands.shared.densities(footers: specs.map(\.footer))
        let plan = GridPlan(specs: specs, densities: densities, measured: measured, columns: columns, maxHeight: maxHeight)

        Group {
            if let plan {
                let grid = VStack(alignment: .leading, spacing: Self.cardGap) {
                    ForEach(Array(gridRows.enumerated()), id: \.offset) { _, row in
                        HStack(alignment: .top, spacing: Self.cardGap) {
                            ForEach(row, id: \.self) { index in
                                RouteCardView(
                                    spec: specs[index],
                                    state: state,
                                    density: plan.density,
                                    visible: plan.visible[index],
                                    nowMillis: nowMillis,
                                    measurer: measurer,
                                    actions: actions,
                                    nextBlockMax: nextBlockMax,
                                    fill: plan.fills[index]
                                )
                                .id(specs[index].destination.id)
                            }
                        }
                    }
                }
                .frame(width: width, alignment: .leading)
                if plan.gridHeight <= maxHeight + 0.5 {
                    grid.frame(height: plan.gridHeight, alignment: .top)
                } else {
                    // The last resort: not even one line a card fits, so the cards scroll, faded at
                    // the bottom while there is more below.
                    ScrollView(.vertical, showsIndicators: true) {
                        grid.background(GeometryReader { geometry in
                            Color.clear.preference(key: ScrolledBottomKey.self, value: geometry.frame(in: .named(Self.scrollSpace)).maxY)
                        })
                    }
                    .coordinateSpace(name: Self.scrollSpace)
                    .frame(width: width, height: maxHeight)
                    .onPreferenceChange(ScrolledBottomKey.self) { scrolledBottom = $0 }
                    .mask(
                        VStack(spacing: 0) {
                            Color.black
                            LinearGradient(colors: [.black, .clear], startPoint: .top, endPoint: .bottom)
                                .frame(height: scrolledBottom > maxHeight + 1 ? 32 : 0)
                        }
                    )
                }
            } else {
                Color.clear.frame(width: width, height: 0)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: plan?.signature)
        .background(alignment: .topLeading) {
            probes(specs: specs, densities: densities, measurer: measurer, nextBlockMax: nextBlockMax)
        }
        .onPreferenceChange(CardProbeKey.self) { measured = $0 }
    }

    private static let scrollSpace = "routeCards"

    /// Each card as the board decides it before drawing: its size class from its own width, its
    /// band lined up with its grid row's, its alternatives still to leave and whether they are open.
    private func makeSpecs(measurer: CardMeasurer) -> [RouteCardSpec] {
        let fontScale = Float(measurer.fontScale)
        let columns = RouteCardLayout.shared.columns(count: Int32(routes.count), widthDp: Float(width), gapDp: Float(Self.cardGap), fontScale: fontScale)
        let widths = RouteCardLayout.shared.cardWidths(count: Int32(routes.count), columns: columns, widthDp: Float(width), gapDp: Float(Self.cardGap)).points
        let sizes = widths.map { RouteCardLayout.shared.size(widthDp: Float($0), fontScale: fontScale) }
        let canToggle = routes.map { state.canToggleRouteAlternatives(destinationId: $0.id, nowMillis: nowMillis) }
        let own = routes.indices.map { index in
            RouteCardBands.shared.own(
                isSearched: routes[index].id == state.temporaryDestination?.id,
                hasRoute: !(state.fastestTrips[routes[index].id]?.isEmpty ?? true),
                size: sizes[index],
                hasAlternatives: canToggle[index]
            )
        }
        let footers = RouteCardBands.shared.footers(own: own, columns: columns)
        return routes.indices.map { index in
            RouteCardSpec(
                destination: routes[index],
                size: sizes[index],
                narrow: RouteCardLayout.shared.isNarrow(widthDp: Float(widths[index]), fontScale: fontScale),
                footer: footers[index],
                alternatives: canToggle[index]
                    ? BoardRouteAlternatives.shared.alternatives(trips: state.fastestTrips[routes[index].id], nowMillis: nowMillis)
                    : [],
                expanded: state.showsRouteAlternatives(destinationId: routes[index].id),
                width: floor(widths[index])
            )
        }
    }

    /// The measuring copies, out of sight: every card collapsed at every density, and each
    /// expanded card's rows one by one ("+N in details" last).
    private func probes(specs: [RouteCardSpec], densities: [RouteCardDensity], measurer: CardMeasurer, nextBlockMax: CGFloat) -> some View {
        let unmeasured = BoardRouteAlternatives.shared.unmeasured(count: 1)[0]
        return ZStack(alignment: .topLeading) {
            ForEach(Array(densities.enumerated()), id: \.offset) { _, density in
                ForEach(specs, id: \.destination.id) { spec in
                    RouteCardView(
                        spec: spec,
                        state: state,
                        density: density,
                        visible: unmeasured,
                        nowMillis: nowMillis,
                        measurer: measurer,
                        actions: RouteCardActions(),
                        nextBlockMax: nextBlockMax,
                        probeKey: "\(density.name)/\(spec.destination.id)"
                    )
                    .fixedSize(horizontal: false, vertical: true)
                }
            }
            ForEach(specs.filter { $0.footer == .toggle && $0.expanded }, id: \.destination.id) { spec in
                ForEach(0 ... spec.alternatives.count, id: \.self) { row in
                    VStack(spacing: 0) {
                        Rectangle().frame(height: 1)
                        if row < spec.alternatives.count {
                            AlternativeRow(trip: spec.alternatives[row], size: spec.size, measurer: measurer, width: spec.width, nowMillis: nowMillis) {}
                        } else {
                            RouteMoreInDetailsRow(hidden: spec.alternatives.count) {}
                        }
                    }
                    .frame(width: spec.width)
                    .fixedSize(horizontal: false, vertical: true)
                    .background(GeometryReader { geometry in
                        Color.clear.preference(key: CardProbeKey.self, value: ["rows/\(spec.destination.id)/\(row)": geometry.size.height])
                    })
                }
            }
        }
        .opacity(0)
        .allowsHitTesting(false)
        // Out of VoiceOver's and the UI tests' reach: only the drawn cards are there.
        .accessibilityElement(children: .ignore)
        .accessibilityHidden(true)
    }
}

/// What the measured heights decide: the density all cards take, how many rows each expanded card
/// shows, and how each card fills its grid row. Nil until every card has been measured.
private struct GridPlan {
    let density: RouteCardDensity
    let visible: [VisibleAlternatives]
    let fills: [CardFill]
    let gridHeight: CGFloat

    /// Changes when what is shown changes, for the animation.
    var signature: [CGFloat] { fills.map(\.height) + [CGFloat(density.ordinal)] }

    init?(specs: [RouteCardSpec], densities: [RouteCardDensity], measured: [String: CGFloat], columns: Int, maxHeight: CGFloat) {
        guard !specs.isEmpty, let last = densities.last else { return nil }
        let gap = Float(BoardRouteCards.cardGap)
        func part(_ density: RouteCardDensity, _ spec: RouteCardSpec, _ name: String) -> CGFloat? {
            measured["\(density.name)/\(spec.destination.id)/\(name)"]
        }
        // The richest density whose collapsed cards fit, else the last.
        var chosen = last
        var totals: [CGFloat] = []
        for density in densities {
            let heights = specs.map { part(density, $0, "total") }
            guard heights.allSatisfy({ $0 != nil }) else { return nil }
            totals = heights.map { $0! }
            chosen = density
            if RouteCardLayout.shared.fits(cardHeights: totals.kotlin, columns: Int32(columns), gap: gap, maxHeight: Float(maxHeight)) { break }
        }
        let heads = specs.map { part(chosen, $0, "head") ?? 0 }
        let footers = specs.map { part(chosen, $0, "footer") ?? 0 }

        // The expanded cards' rows as measured, and how many of them fit.
        let expanded = specs.map { chosen == .full && $0.footer == .toggle && $0.expanded }
        var rowHeights: [[CGFloat]] = []
        for (index, spec) in specs.enumerated() {
            guard expanded[index] else {
                rowHeights.append([])
                continue
            }
            let rows = (0 ... spec.alternatives.count).map { measured["rows/\(spec.destination.id)/\($0)"] }
            guard rows.allSatisfy({ $0 != nil }) else { return nil }
            rowHeights.append(rows.map { $0! })
        }
        let alternatives = BoardRouteAlternatives.shared
        let visible: [VisibleAlternatives] = expanded.contains(true)
            ? alternatives.visibleRows(
                alternativeCounts: specs.indices.map { KotlinInt(int: Int32(expanded[$0] ? specs[$0].alternatives.count : 0)) },
                columns: Int32(columns),
                spareHeight: Float(maxHeight) - RouteCardLayout.shared.gridHeight(cardHeights: totals.kotlin, columns: Int32(columns), gap: gap),
                rowHeight: Float(rowHeights.map { $0.dropLast().max() ?? 0 }.max() ?? 0),
                moreRowHeight: Float(rowHeights.map { $0.last ?? 0 }.max() ?? 0)
            )
            : alternatives.unmeasured(count: Int32(specs.count))
        // Each card's rows as shown.
        let shownRows: [[CGFloat]] = specs.indices.map { index in
            let rows = rowHeights[index]
            let shown = visible[index]
            guard !rows.isEmpty, shown.showsRows else { return [] }
            return Array(rows.prefix(Int(shown.shown))) + (shown.showsMoreRow ? [rows[rows.count - 1]] : [])
        }
        let natural = specs.indices.map { totals[$0] + shownRows[$0].reduce(0, +) }
        let gridRowHeights = RouteCardLayout.shared.rowHeights(cardHeights: natural.kotlin, columns: Int32(columns)).points
        let headMax = RouteCardLayout.shared.rowHeights(cardHeights: heads.kotlin, columns: Int32(columns)).points
        density = chosen
        self.visible = visible
        fills = specs.indices.map { index in
            let height = gridRowHeights[index / columns]
            let stretch = RouteCardLayout.shared.stretch(
                head: Float(heads[index]),
                rowHeadMax: Float(headMax[index / columns]),
                rows: Int32(shownRows[index].count),
                rowsHeight: Float(shownRows[index].reduce(0, +)),
                footer: Float(footers[index]),
                rowHeight: Float(height)
            )
            let extra = CGFloat(stretch.rowExtra)
            return CardFill(
                height: height,
                headBand: CGFloat(stretch.headBand),
                rowHeights: shownRows[index].map { $0 + extra },
                footerBand: CGFloat(stretch.footerBand)
            )
        }
        gridHeight = gridRowHeights.reduce(0, +) + BoardRouteCards.cardGap * CGFloat(max(0, gridRowHeights.count - 1))
    }
}

private struct ScrolledBottomKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}
