import Shared
import SwiftUI

/// The wall board's route cards above the stop panels, `columns` side by side, as on Android
/// (MainScreen.kt's RouteCards): one per destination in `routes`, the searched-for one first.
///
/// A saved destination's banner names its next alternatives on a "Then" line and offers "N more";
/// expanded (a saved choice, see `UiState.showsRouteAlternatives`) they are listed below it as on
/// the spot instead. Expanded cards take only the rows that leave the stop panels
/// `BoardLayout.MIN_PANEL_HEIGHT_DP` of `bodyHeight` (by `BoardRouteAlternatives.visibleRows`),
/// ending with "+N in details" (`onMoreInDetails`) when that is not all of them. The rule needs
/// each card's height without its rows, measured as they are drawn, so until every card has been
/// measured none shows a row: the first frames never squeeze the panels. Nothing here collapses a card.
struct BoardRouteCards: View {
    let state: StopBoardViewModel.UiState
    let viewModel: StopBoardViewModel
    let routes: [StopPlace]
    let columns: Int
    /// The height the cards and the stop panels share.
    let bodyHeight: CGFloat
    /// The space between the cards and the panels.
    let gap: CGFloat
    let nowMillis: Int64
    let onOpenTrip: (_ destinationId: String, _ trip: TripInfo) -> Void
    /// "+N in details": the route search, on that destination's routes.
    let onMoreInDetails: (_ destination: StopPlace) -> Void

    /// The rows' heights until one has been drawn, grown with the text size as Android's grow with
    /// the font scale.
    @ScaledMetric private var fallbackRowHeight = CGFloat(BoardRouteAlternatives.shared.ROW_HEIGHT_DP)
    @ScaledMetric private var fallbackMoreRowHeight = CGFloat(BoardRouteAlternatives.shared.MORE_ROW_HEIGHT_DP)
    /// Each card's head (the card without its rows), by destination id, each measured on its own.
    @State private var headHeights: [String: CGFloat] = [:]
    /// An alternative row's height and the "+N in details" row's, once one has been drawn.
    @State private var rowHeight: CGFloat?
    @State private var moreRowHeight: CGFloat?

    var body: some View {
        let perRow = min(columns, max(1, routes.count))
        let alternatives = BoardRouteAlternatives.shared
        let expanded: [[TripInfo]] = routes.map { destination in
            state.showsRouteAlternatives(destinationId: destination.id)
                ? alternatives.alternatives(trips: state.fastestTrips[destination.id], nowMillis: nowMillis)
                : []
        }
        let gridRows = stride(from: 0, to: routes.count, by: perRow).map { Array(routes[$0 ..< min($0 + perRow, routes.count)]) }
        let measured = routes.allSatisfy { headHeights[$0.id] != nil }
        // The cards without their rows: a grid row is as tall as its tallest card.
        let headsHeight = gridRows.map { row in row.map { headHeights[$0.id] ?? 0 }.max() ?? 0 }.reduce(0, +)
            + FjordTokens.space2 * CGFloat(max(0, gridRows.count - 1))
        let spare = bodyHeight - headsHeight - gap - CGFloat(BoardLayout.shared.MIN_PANEL_HEIGHT_DP)
        let visible = measured
            ? alternatives.visibleRows(
                alternativeCounts: expanded.map { KotlinInt(int: Int32($0.count)) },
                columns: Int32(perRow),
                spareHeight: Float(spare),
                rowHeight: Float(rowHeight ?? fallbackRowHeight),
                moreRowHeight: Float(moreRowHeight ?? fallbackMoreRowHeight)
            )
            : alternatives.unmeasured(count: Int32(routes.count))
        LazyVGrid(
            columns: Array(repeating: GridItem(.flexible(), spacing: FjordTokens.space3, alignment: .top), count: perRow),
            spacing: FjordTokens.space2
        ) {
            ForEach(Array(routes.enumerated()), id: \.element.id) { index, destination in
                card(destination, alternatives: expanded[index], visible: visible[index])
            }
        }
        .onPreferenceChange(HeadHeightsKey.self) { headHeights = $0 }
        .onPreferenceChange(RowHeightKey.self) { if let height = $0 { rowHeight = height } }
        .onPreferenceChange(MoreRowHeightKey.self) { if let height = $0 { moreRowHeight = height } }
    }

    /// One destination's card: its banner (or why it has none), and when it is expanded the
    /// `visible` ones of `alternatives` below it, on the banner's colour as one card.
    @ViewBuilder
    private func card(_ destination: StopPlace, alternatives: [TripInfo], visible: VisibleAlternatives) -> some View {
        let trips = state.fastestTrips[destination.id]
        let trip = trips?.first
        let expanded = state.showsRouteAlternatives(destinationId: destination.id)
        let showsRows = expanded && trip != nil && visible.showsRows
        let isTemporary = destination.id == state.temporaryDestination?.id
        HStack(spacing: 0) {
            VStack(spacing: 0) {
                // Overlaid, not stacked, so the placeholder and the banner cross-fade in place.
                ZStack {
                    if let trip {
                        FastestTripBanner(
                            trip: trip,
                            isRefreshing: state.loadingTripDestinationIds.contains(destination.id),
                            thenLine: state.routeThenLine(destinationId: destination.id, nowMillis: nowMillis, visible: visible),
                            toggle: state.canToggleRouteAlternatives(destinationId: destination.id, nowMillis: nowMillis)
                                ? RouteAlternativesToggle(
                                    count: BoardRouteAlternatives.shared.alternatives(trips: trips, nowMillis: nowMillis).count,
                                    expanded: expanded,
                                    destinationName: destination.name,
                                    onTap: { viewModel.toggleRouteAlternatives(destinationId: destination.id) }
                                )
                                : nil,
                            onTap: { onOpenTrip(destination.id, trip) }
                        )
                        .transition(.opacity)
                    } else if state.failedTripDestinationIds.contains(destination.id) {
                        FastestTripUnavailable(
                            destinationName: destination.name,
                            isOnline: state.isOnline,
                            isRetrying: state.loadingTripDestinationIds.contains(destination.id)
                        )
                        .transition(.opacity)
                    } else if let noRouteTime = state.noRouteTime(destinationId: destination.id) {
                        FastestTripNone(destinationName: destination.name, time: noRouteTime)
                            .transition(.opacity)
                    } else {
                        FastestTripPlaceholder(destinationName: destination.name)
                            .transition(.opacity)
                    }
                }
                .animation(.easeInOut(duration: 0.3), value: trip == nil)
                // The head of a card that can open is its banner, measured on its own rather than
                // as the card less its rows.
                .background(isTemporary ? nil : HeadHeightReader(id: destination.id))
                if showsRows {
                    rows(destination, alternatives: alternatives, visible: visible)
                        .transition(.opacity.combined(with: .move(edge: .top)))
                }
            }
            .background(showsRows ? Palette.route : Color.clear)
            .clipShape(RoundedRectangle(cornerRadius: FjordTokens.radiusLg))
            if isTemporary {
                TemporaryRouteActions(
                    time: state.temporaryTripTime,
                    onTimeChange: { viewModel.setTripTime(time: $0) },
                    canSave: state.canSaveTemporaryDestination,
                    onSave: { viewModel.saveTemporaryDestination() },
                    onDismiss: { viewModel.dismissTemporaryDestination() },
                    fillsBackground: true
                )
            }
        }
        .fixedSize(horizontal: false, vertical: true)
        .animation(.easeInOut(duration: 0.3), value: showsRows)
        // The searched-for card never opens, so its head is all of it, buttons included.
        .background(isTemporary ? HeadHeightReader(id: destination.id) : nil)
    }

    /// An expanded card's rows: the on-spot rows, then "+N in details" when not all of them fit.
    private func rows(_ destination: StopPlace, alternatives: [TripInfo], visible: VisibleAlternatives) -> some View {
        let shown = min(Int(visible.shown), alternatives.count)
        return VStack(spacing: 0) {
            if shown > 0 {
                VStack(spacing: 0) {
                    ForEach(Array(alternatives.prefix(shown).enumerated()), id: \.offset) { _, alternative in
                        Divider().padding(.horizontal, 16)
                        TripOptionRow(trip: alternative, isFirst: false)
                            .contentShape(Rectangle())
                            .onTapGesture { onOpenTrip(destination.id, alternative) }
                            .accessibilityAddTraits(.isButton)
                            .accessibilityIdentifier("routeAlternative")
                    }
                }
                .background(GeometryReader { geometry in
                    Color.clear.preference(key: RowHeightKey.self, value: geometry.size.height / CGFloat(shown))
                })
            }
            if visible.showsMoreRow {
                VStack(spacing: 0) {
                    Divider().padding(.horizontal, 16)
                    RouteMoreInDetailsRow(hidden: Int(visible.hidden)) { onMoreInDetails(destination) }
                }
                .background(GeometryReader { geometry in
                    Color.clear.preference(key: MoreRowHeightKey.self, value: geometry.size.height)
                })
            }
        }
        .foregroundColor(Palette.onRoute)
    }
}

/// Reports the height of what it is the background of as `id`'s head.
private struct HeadHeightReader: View {
    let id: String

    var body: some View {
        GeometryReader { geometry in
            Color.clear.preference(key: HeadHeightsKey.self, value: [id: geometry.size.height])
        }
    }
}

/// Each route card's head height, by destination id.
private struct HeadHeightsKey: PreferenceKey {
    static var defaultValue: [String: CGFloat] = [:]
    static func reduce(value: inout [String: CGFloat], nextValue: () -> [String: CGFloat]) {
        value.merge(nextValue()) { $1 }
    }
}

/// The height of one alternative row, from any expanded card that shows some.
private struct RowHeightKey: PreferenceKey {
    static var defaultValue: CGFloat?
    static func reduce(value: inout CGFloat?, nextValue: () -> CGFloat?) {
        value = nextValue() ?? value
    }
}

/// The height of the "+N in details" row, from any card that shows one.
private struct MoreRowHeightKey: PreferenceKey {
    static var defaultValue: CGFloat?
    static func reduce(value: inout CGFloat?, nextValue: () -> CGFloat?) {
        value = nextValue() ?? value
    }
}
