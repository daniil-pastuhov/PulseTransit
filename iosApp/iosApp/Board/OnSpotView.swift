import Shared
import SwiftUI
import UIKit

// Heights the auto route count is worked out from: one alternative's row, and a destination card's
// title and padding. The counterpart of Android's OnSpotContent constants.
private let tripRowHeight: Float = 64
private let destinationHeaderHeight: Float = 72

// The routes/stop split: the bar a folded section keeps, the size below which a dropped section
// folds to it (route rows need more width than height), and the grip between the two.
private let splitBarSize: Float = 56
private let splitMinSectionSize: Float = 120
private let splitMinSectionWidth: Float = 240
private let splitHandleSize: CGFloat = 20

/// The on-spot board below the header, the counterpart of Android's OnSpotContent: route
/// alternatives to each destination from where the device is, taking most of the space, and the
/// nearest stop's departures beside or below them.
struct OnSpotView: View {
    let state: StopBoardViewModel.UiState
    let viewModel: StopBoardViewModel
    let onOpenSettings: () -> Void

    var body: some View {
        switch onEnum(of: state.location) {
        case .located:
            SplitSections(state: state, viewModel: viewModel, onOpenSettings: onOpenSettings)
                .padding(6)
        case let .locating(locating):
            Locating(approximate: locating.accuracy == .approximate, destinations: state.routeDestinations)
        case .idle:
            Locating(approximate: false, destinations: state.routeDestinations)
        case .unavailable:
            LocationStatus(
                symbol: "location.slash",
                title: L("location_unavailable"),
                detail: L("on_spot_unavailable_body"),
                actionTitle: L("try_again"),
                prominent: false
            ) { viewModel.retryLocation() }
        case .permissionDenied:
            // iOS asks only once; after a refusal, access can only be granted in the Settings app.
            // The board looks again when the app becomes active, so returning from there is enough.
            LocationStatus(
                symbol: "location.slash",
                title: L("on_spot_denied_title"),
                detail: L("location_denied"),
                actionTitle: L("location_allow"),
                hint: L("on_spot_search_hint")
            ) {
                if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
            }
        }
    }
}

/// The routes and the stop, sharing the board as the user drags the grip between them, the
/// counterpart of Android's LocatedContent. A section dragged below its minimum folds to a bar at
/// its edge, like a bottom sheet; the split is kept across launches by the ViewModel.
private struct SplitSections: View {
    let state: StopBoardViewModel.UiState
    let viewModel: StopBoardViewModel
    let onOpenSettings: () -> Void
    @Environment(\.layoutDirection) private var layoutDirection
    /// The routes' size when the drag began, and now; nil while not dragged.
    @State private var dragStartSize: Float?
    @State private var dragSize: Float?
    /// The split just dropped or tapped open, until the ViewModel's state has it.
    @State private var pendingSplit: Float?
    /// How tall the routes' cards are, measured; nil while they show something else.
    @State private var routesContentHeight: CGFloat?

    var body: some View {
        GeometryReader { geometry in
            let isLandscape = geometry.size.width > geometry.size.height
            let total = Float(isLandscape ? geometry.size.width : geometry.size.height) - Float(splitHandleSize)
            let minSize = OnSpotSplit.shared.minSectionSize(
                totalSize: total,
                minSize: isLandscape ? splitMinSectionWidth : splitMinSectionSize
            )
            let split = pendingSplit ?? state.onSpotSplit
            // The routes' share, which decides how many alternatives they show...
            let allottedSize = dragSize ?? OnSpotSplit.shared.routesSize(
                split: split,
                totalSize: total,
                barSize: splitBarSize,
                minSize: minSize
            )
            // ...and the size they get: no taller than those need, the stop taking the rest. Not
            // while dragged, so the grip follows the finger.
            let contentHeight = isLandscape ? nil : routesContentHeight
            let routesSize = dragSize ?? OnSpotSplit.shared.fittedRoutesSize(
                split: split,
                totalSize: total,
                barSize: splitBarSize,
                minSize: minSize,
                contentSize: contentHeight.map { Float($0) } ?? .infinity
            )
            // A section dragged below its minimum shows the bar it will fold to.
            let routesFolded = routesSize < minSize
            let stopFolded = total - routesSize < minSize
            let drag = splitDrag(routesSize: routesSize, total: total, minSize: minSize, isLandscape: isLandscape)
            // A folded bar opens when dragged, but the section turns back into its content on the
            // way, so the drag sits on the section and stays active until it ends.
            let routes = Group {
                if routesFolded {
                    FoldedSection(
                        // Says what it hides: "Show routes · 2 destinations".
                        label: "\(L("on_spot_show_routes")) · \(L("on_spot_destinations", state.routeDestinations.count))",
                        systemImage: "arrow.triangle.turn.up.right.diamond.fill",
                        expandLabel: L("on_spot_show_routes"),
                        edge: isLandscape ? .leading : .top,
                        onExpand: expand
                    )
                    .transition(.opacity)
                } else {
                    RoutesSection(
                        state: state,
                        viewModel: viewModel,
                        onOpenSettings: onOpenSettings,
                        availableHeight: isLandscape ? geometry.size.height : CGFloat(allottedSize)
                    )
                    .transition(.opacity)
                }
            }
            .gesture(drag, including: routesFolded || dragSize != nil ? .all : .subviews)
            let stop = Group {
                if stopFolded {
                    FoldedSection(
                        // Says what it hides: "Show departures · Oslo S · 83 m".
                        label: [
                            L("on_spot_show_departures"),
                            state.favorites.first?.name,
                            state.nearbyStops.first { $0.stop.id == state.favorites.first?.id }?.distanceMeters.map { distanceText(Int($0.intValue)) },
                        ].compactMap { $0 }.joined(separator: " · "),
                        systemImage: "bus.fill",
                        expandLabel: L("on_spot_show_departures"),
                        edge: isLandscape ? .trailing : .bottom,
                        onExpand: expand
                    )
                    .transition(.opacity)
                } else {
                    NearbyStopSection(state: state, viewModel: viewModel)
                        .transition(.opacity)
                }
            }
            .gesture(drag, including: stopFolded || dragSize != nil ? .all : .subviews)
            if isLandscape {
                HStack(spacing: 0) {
                    routes.frame(width: CGFloat(routesSize))
                    SplitHandle(isVertical: true)
                        .frame(width: splitHandleSize)
                        .gesture(drag)
                    stop.frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            } else {
                VStack(spacing: 0) {
                    routes.frame(height: CGFloat(routesSize))
                    SplitHandle(isVertical: false)
                        .frame(height: splitHandleSize)
                        .gesture(drag)
                    stop.frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
        }
        .onPreferenceChange(RoutesContentHeightKey.self) { routesContentHeight = $0 }
        // The routes grow and shrink with what they show, and a split the ViewModel brings (a
        // restored one) moves like a dropped one; neither while dragged.
        .animation(dragSize == nil ? .spring() : nil, value: routesContentHeight)
        .animation(dragSize == nil ? .spring() : nil, value: state.onSpotSplit)
        .onChange(of: state.onSpotSplit) { _ in pendingSplit = nil }
    }

    /// Drags the routes' edge; measured on screen, since the grip moves with the drag.
    private func splitDrag(routesSize: Float, total: Float, minSize: Float, isLandscape: Bool) -> some Gesture {
        DragGesture(minimumDistance: 4, coordinateSpace: .global)
            .onChanged { value in
                let start = dragStartSize ?? routesSize
                dragStartSize = start
                let moved = Float(isLandscape ? value.translation.width : value.translation.height)
                // In a right-to-left row the routes are on the right, so dragging right shrinks them.
                let delta = isLandscape && layoutDirection == .rightToLeft ? -moved : moved
                dragSize = OnSpotSplit.shared.dragRoutesSize(routesSize: start, delta: delta, totalSize: total, barSize: splitBarSize)
            }
            .onEnded { _ in
                let split = OnSpotSplit.shared.settle(routesSize: dragSize ?? routesSize, totalSize: total, minSize: minSize)
                withAnimation(.spring()) {
                    pendingSplit = split
                    dragSize = nil
                    dragStartSize = nil
                }
                viewModel.setOnSpotSplit(split: split)
            }
    }

    private func expand() {
        withAnimation(.spring()) { pendingSplit = OnSpotSplit.shared.DEFAULT }
        viewModel.setOnSpotSplit(split: OnSpotSplit.shared.DEFAULT)
    }
}

/// The height of the routes' cards, for the split to give what they leave over to the stop.
private struct RoutesContentHeightKey: PreferenceKey {
    static var defaultValue: CGFloat?
    static func reduce(value: inout CGFloat?, nextValue: () -> CGFloat?) {
        value = nextValue() ?? value
    }
}

/// The grip between the routes and the stop, dragged to share the board between them.
private struct SplitHandle: View {
    let isVertical: Bool

    var body: some View {
        Capsule()
            .fill(Palette.textSecondary.opacity(0.4))
            .frame(width: isVertical ? 4 : 32, height: isVertical ? 32 : 4)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .contentShape(Rectangle())
            .accessibilityElement()
            .accessibilityLabel(L("on_spot_resize"))
    }
}

/// A section folded to a bar at its `edge`, like a bottom sheet: tapped, it opens to the default
/// split; dragged, it opens as far as it is pulled. Side bars, in landscape, are too narrow for the label.
private struct FoldedSection: View {
    let label: String
    let systemImage: String
    let expandLabel: String
    let edge: Edge
    let onExpand: () -> Void

    var body: some View {
        Group {
            if edge == .leading || edge == .trailing {
                VStack(spacing: 8) {
                    Image(systemName: systemImage)
                    Image(systemName: arrow)
                }
            } else {
                HStack(spacing: 12) {
                    Image(systemName: systemImage)
                    Text(label)
                        .font(.pulse(.headline))
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: arrow)
                }
                .padding(.horizontal, 16)
            }
        }
        .foregroundColor(Palette.textPrimary)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(RoundedRectangle(cornerRadius: FjordTokens.radiusLg).fill(Palette.surfaceContainer))
        .padding(6)
        .contentShape(Rectangle())
        .onTapGesture(perform: onExpand)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityAddTraits(.isButton)
        .accessibilityHint(expandLabel)
    }

    /// Points the way the section opens.
    private var arrow: String {
        switch edge {
        case .top: return "chevron.down"
        case .bottom: return "chevron.up"
        case .leading: return "chevron.forward"
        case .trailing: return "chevron.backward"
        }
    }
}

/// Before a fix: what the app is doing, over the routes and stop as skeletons, so the layout
/// doesn't jump when it comes.
private struct Locating: View {
    let approximate: Bool
    let destinations: [StopPlace]
    @State private var pulse = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label(L(approximate ? "locating_approximate" : "locating_precise"), systemImage: "location")
                .font(.pulse(.subheadline))
                .foregroundColor(Palette.textSecondary)
            ForEach(destinations, id: \.id) { FastestTripPlaceholder(destinationName: $0.name) }
            VStack(spacing: 12) {
                ForEach(0 ..< 5, id: \.self) { _ in
                    HStack(spacing: 8) {
                        PlaceholderBlock(width: FjordTokens.pillW, height: 28, pulse: pulse)
                        PlaceholderBlock(height: 14, pulse: pulse)
                        PlaceholderBlock(width: 48, height: 18, pulse: pulse)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(16)
            .frame(maxHeight: .infinity)
            .background(RoundedRectangle(cornerRadius: FjordTokens.radiusLg).fill(Palette.surface))
            .modifier(PlaceholderPulse(pulse: $pulse))
        }
        .padding(12)
    }
}

/// Why the device could not be found (T5), with what to do; the header stays above it.
private struct LocationStatus: View {
    let symbol: String
    let title: String
    let detail: String
    let actionTitle: String
    var hint: String?
    var prominent = true
    let action: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Spacer()
            Image(systemName: symbol)
                .font(.system(size: 40))
                .foregroundColor(Palette.primary)
                .accessibilityHidden(true)
            Text(title)
                .font(.pulse(role: FjordTokens.typeTitle, relativeTo: .title3))
                .multilineTextAlignment(.center)
                .foregroundColor(Palette.textPrimary)
            Text(detail)
                .font(.pulse(.body))
                .multilineTextAlignment(.center)
                .foregroundColor(Palette.textSecondary)
                .frame(maxWidth: 360)
            if prominent {
                Button(actionTitle, action: action).buttonStyle(.borderedProminent)
            } else {
                Button(actionTitle, action: action).buttonStyle(.bordered)
            }
            if let hint {
                Text(hint).font(.pulse(.subheadline)).foregroundColor(Palette.textSecondary).multilineTextAlignment(.center)
            }
            Spacer()
        }
        .padding(24)
        .frame(maxWidth: .infinity)
    }
}

private struct RoutesSection: View {
    let state: StopBoardViewModel.UiState
    let viewModel: StopBoardViewModel
    let onOpenSettings: () -> Void
    /// The height the alternatives are counted for: the routes' share, which the section may be
    /// shrunk below to fit what that count shows.
    let availableHeight: CGFloat
    /// The tapped alternative, whose details are open.
    @State private var detailsTrip: TripSelection?
    @EnvironmentObject private var journeyModel: JourneyAlertsModel

    var body: some View {
        if state.routeDestinations.isEmpty {
            // First use: teach the next step; the nearest stop's departures already work.
            VStack(spacing: 12) {
                Image(systemName: "flag").font(.system(size: 36)).foregroundColor(Palette.primary).accessibilityHidden(true)
                Text(L("on_spot_no_destinations_title"))
                    .font(.pulse(role: FjordTokens.typeTitle, relativeTo: .title3))
                Text(L("routes_add_destination"))
                    .font(.pulse(.body))
                    .foregroundColor(Palette.textSecondary)
                    .multilineTextAlignment(.center)
                Button(L("add_destination"), action: onOpenSettings)
                    .buttonStyle(.borderedProminent)
                Text(L("on_spot_one_off_hint")).font(.pulse(.subheadline)).foregroundColor(Palette.textSecondary)
            }
            .foregroundColor(Palette.textPrimary)
            .padding(24)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(RoundedRectangle(cornerRadius: FjordTokens.radiusLg).fill(Palette.surface))
            .padding(6)
        } else {
            // A destination with no route is left out once it has loaded, as on the wall board,
            // except the searched-for one, which says so and keeps its buttons.
            let destinations = state.routeDestinations.filter {
                state.fastestTrips[$0.id] != nil ||
                    state.loadingTripDestinationIds.contains($0.id) ||
                    state.failedTripDestinationIds.contains($0.id) ||
                    $0.id == state.temporaryDestination?.id
            }
            Group {
                let autoCount = TripOptionLayoutKt.autoTripOptionCount(
                    availableHeight: Float(availableHeight),
                    destinationCount: Int32(destinations.count),
                    rowHeight: tripRowHeight,
                    headerHeight: destinationHeaderHeight
                )
                let count = Int(TripOptionLayoutKt.visibleTripOptionCount(setting: state.tripOptionsCount, autoCount: autoCount))
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        if case let .located(located) = onEnum(of: state.location), located.accuracy == .approximate {
                            // Distances and walks come from a rough fix until a precise one replaces it (T4).
                            Label(L("on_spot_approximate"), systemImage: "location")
                                .font(.pulse(.caption, weight: .semibold))
                                .foregroundColor(Palette.onWarningContainer)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 2)
                                .background(RoundedRectangle(cornerRadius: FjordTokens.radiusSm).fill(Palette.warningContainer))
                                .padding(6)
                        }
                        ForEach(destinations, id: \.id) { destination in
                            DestinationRoutesCard(
                                destination: destination,
                                trips: state.fastestTrips[destination.id],
                                count: count,
                                isRefreshing: state.loadingTripDestinationIds.contains(destination.id),
                                isFailed: state.failedTripDestinationIds.contains(destination.id),
                                noRouteTime: state.noRouteTime(destinationId: destination.id),
                                isOnline: state.isOnline,
                                onTripTap: { detailsTrip = TripSelection(destinationId: destination.id, trip: $0) },
                                actions: destination.id == state.temporaryDestination?.id
                                    ? TemporaryRouteActions(
                                        time: state.temporaryTripTime,
                                        onTimeChange: { viewModel.setTripTime(time: $0) },
                                        canSave: state.canSaveTemporaryDestination,
                                        onSave: { viewModel.saveTemporaryDestination() },
                                        onDismiss: { viewModel.dismissTemporaryDestination() }
                                    )
                                    : nil
                            )
                        }
                    }
                    .background(GeometryReader {
                        Color.clear.preference(key: RoutesContentHeightKey.self, value: $0.size.height)
                    })
                    // Alternatives come and go as the split or the setting changes.
                    .animation(.easeInOut(duration: 0.3), value: count)
                }
            }
            .sheet(item: $detailsTrip) { selection in
                TripDetailsView(
                    trip: TripDetailsKt.currentTrip(trips: state.fastestTrips[selection.destinationId], selected: selection.trip),
                    showDelays: state.showDelays,
                    showMessages: state.showMessages,
                    loadLegStops: { leg in (try? await viewModel.tripLegStops(leg: leg)) ?? [] },
                    journey: journeyModel
                )
            }
        }
    }
}

/// One destination's next `count` route alternatives, best first, with `actions` beside its title.
private struct DestinationRoutesCard: View {
    let destination: StopPlace
    let trips: [TripInfo]?
    let count: Int
    let isRefreshing: Bool
    let isFailed: Bool
    /// Set when Entur found no route: the time it was searched for.
    let noRouteTime: TripTime?
    let isOnline: Bool
    let onTripTap: (TripInfo) -> Void
    var actions: TemporaryRouteActions?

    var body: some View {
        Group {
            if let trips {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 0) {
                        Text(L("route_to", destination.name))
                            .font(.pulse(.headline))
                            .lineLimit(1)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 4)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        actions
                    }
                    ForEach(Array(trips.prefix(count).enumerated()), id: \.offset) { index, trip in
                        if index > 0 { Divider().padding(.horizontal, 16) }
                        TripOptionRow(trip: trip, isFirst: index == 0)
                            .contentShape(Rectangle())
                            .onTapGesture { onTripTap(trip) }
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    }
                }
                .padding(.vertical, 8)
                .foregroundColor(Palette.onRoute)
                .background(Palette.route)
                .overlay(alignment: .bottom) {
                    if isRefreshing { RefreshBar(color: Palette.onRoute.opacity(0.6)) }
                }
            } else {
                HStack(spacing: 0) {
                    if isFailed {
                        FastestTripUnavailable(destinationName: destination.name, isOnline: isOnline, isRetrying: isRefreshing)
                    } else if let noRouteTime {
                        FastestTripNone(destinationName: destination.name, time: noRouteTime)
                    } else {
                        FastestTripPlaceholder(destinationName: destination.name)
                    }
                    actions.map { actions in
                        var filled = actions
                        filled.fillsBackground = true
                        return filled
                    }
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: FjordTokens.radiusLg))
        .padding(6)
    }
}

/// One alternative: "19:41 in 4 min", the legs, then how long it takes and when it arrives.
private struct TripOptionRow: View {
    let trip: TripInfo
    let isFirst: Bool

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 0) {
                Text(isoClockTime(trip.expectedStartTime))
                    .font(isFirst ? .pulse(role: FjordTokens.typeCountdown, relativeTo: .title3) : .pulse(.headline))
                    .foregroundColor(isFirst ? Palette.routeAccent : Palette.onRoute)
                // Ticks on its own, so "in 4 min" counts down between refreshes of the trips.
                TimelineView(.periodic(from: .now, by: 1)) { timeline in
                    Text(leavesInText(trip.expectedStartTime, now: timeline.date))
                        .font(.pulse(.caption))
                }
            }
            TripLegs(trip: trip, color: Palette.onRoute)
                .frame(maxWidth: .infinity, alignment: .leading)
            VStack(alignment: .trailing, spacing: 0) {
                Text(tripDuration(trip.durationMinutes)).font(.pulse(.subheadline, weight: .semibold))
                if let arrival = TripArrivalKt.tripArrival(trip: trip) {
                    let time = arrival.dayOffset > 0 ? "\(arrival.time)⁺\(arrival.dayOffset)" : arrival.time
                    Text(L("route_arrival", time)).font(.pulse(.caption))
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .accessibilityElement(children: .combine)
    }
}

/// "Now" when it is leaving, else "in 14 min".
private func leavesInText(_ time: String, now: Date) -> String {
    let nowMillis = Int64(now.timeIntervalSince1970 * 1000)
    if DepartureCountdownKt.departureCountdown(expectedTime: time, nowMillis: nowMillis) is DepartureCountdownNow { return L("now") }
    return DepartureDetailsKt.minutesUntil(time: time, nowMillis: nowMillis).map { inTimeText($0.int64Value) } ?? ""
}

private struct NearbyStopSection: View {
    let state: StopBoardViewModel.UiState
    let viewModel: StopBoardViewModel

    var body: some View {
        if let stop = state.favorites.first {
            StopPanel(
                stop: stop,
                groups: state.departures.first { $0.stopId == stop.id }?.groups,
                showDelays: state.showDelays,
                showMessages: state.showMessages,
                isLoading: state.loadingStopIds.contains(stop.id),
                isExtended: state.extendedStopIds.contains(stop.id),
                isFailed: state.failedStopIds.contains(stop.id),
                isOnline: state.isOnline,
                canLoadMore: state.canLoadMoreStopIds.contains(stop.id),
                isLoadingMore: state.loadingMoreStopIds.contains(stop.id),
                onFilterOut: { viewModel.filterOutLine(line: $0) },
                onSearchFurtherAhead: { viewModel.searchFurtherAhead(stopId: stop.id) },
                onLoadMore: { viewModel.loadMoreDepartures(stopId: stop.id) },
                loadJourney: { departure in
                    (try? await viewModel.journeyStopsAhead(departure: departure, stopPlaceId: stop.id)) ?? []
                },
                header: AnyView(
                    StopPicker(
                        selected: stop,
                        approximate: {
                            if case let .located(located) = onEnum(of: state.location) { return located.accuracy == .approximate }
                            return false
                        }(),
                        isSearching: state.isSearchingNearby,
                        nearbyStops: state.nearbyStops,
                        isPinned: state.isStopPinned,
                        onSelect: { viewModel.selectStop(stopId: $0) },
                        onFollowNearest: { viewModel.followNearest() }
                    )
                ),
                // Asks for enough departures to fill the panel, as on the wall board.
                onRowCapacity: { viewModel.setPanelRowCapacity(stopId: stop.id, rows: Int32($0)) }
            )
        } else {
            Group {
                if state.isSearchingNearby || !state.nearbyStops.isEmpty {
                    ProgressView()
                } else {
                    // Empty, not an error: routes still work, as the planner includes the walk.
                    VStack(spacing: 8) {
                        Image(systemName: "location.slash").font(.system(size: 28)).foregroundColor(Palette.textSecondary)
                        Text(L("no_stops_nearby")).font(.pulse(.headline)).foregroundColor(Palette.textPrimary)
                        Text(L("on_spot_no_stops_body")).font(.pulse(.subheadline)).foregroundColor(Palette.textSecondary)
                            .multilineTextAlignment(.center)
                    }
                    .padding(16)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(RoundedRectangle(cornerRadius: FjordTokens.radiusLg).fill(Palette.surface))
            .padding(6)
        }
    }
}

/// The shown stop's name and distance ("83 m · Oslo", or "About 300 m" from a rough fix), opening a
/// sheet of the stops within 1 km, nearest first; and "Back to nearest" while another is chosen.
private struct StopPicker: View {
    let selected: StopPlace
    let approximate: Bool
    let isSearching: Bool
    let nearbyStops: [NearbyStop]
    let isPinned: Bool
    let onSelect: (String) -> Void
    let onFollowNearest: () -> Void
    @State private var choosing = false

    var body: some View {
        HStack(alignment: .center, spacing: 8) {
            Button { choosing = true } label: {
                HStack(spacing: 4) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(selected.name)
                            .font(.pulse(role: FjordTokens.typeTitle, relativeTo: .title3))
                            .lineLimit(1)
                        Text(subtitle)
                            .font(.pulse(.subheadline))
                            .foregroundColor(Palette.textSecondary)
                            .lineLimit(1)
                    }
                    Image(systemName: "chevron.down")
                        .font(.pulse(.subheadline, weight: .bold))
                }
                .foregroundColor(Palette.textPrimary)
            }
            .buttonStyle(.plain)
            .accessibilityHint(L("choose_stop"))
            Spacer(minLength: 0)
            if isPinned {
                Button(action: onFollowNearest) {
                    Label(L("back_to_nearest"), systemImage: "location.fill")
                        .font(.pulse(.footnote))
                }
                .buttonStyle(.bordered)
            }
        }
        .sheet(isPresented: $choosing) {
            NavigationView {
                List {
                    if nearbyStops.isEmpty && isSearching {
                        ProgressView().frame(maxWidth: .infinity)
                    } else if nearbyStops.isEmpty {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(L("no_stops_nearby")).font(.pulse(.headline))
                            Text(L("on_spot_search_hint")).font(.pulse(.subheadline)).foregroundColor(Palette.textSecondary)
                        }
                    }
                    ForEach(nearbyStops, id: \.stop.id) { nearby in
                        Button {
                            choosing = false
                            onSelect(nearby.stop.id)
                        } label: {
                            HStack {
                                PlaceLabel(place: nearby.stop)
                                Spacer()
                                if let meters = nearby.distanceMeters {
                                    Text(distance(Int(meters.intValue))).font(.pulse(.subheadline, weight: .semibold)).foregroundColor(Palette.textSecondary)
                                }
                                if nearby.stop.id == selected.id {
                                    Image(systemName: "checkmark").foregroundColor(Palette.primary)
                                }
                            }
                        }
                        .foregroundColor(.primary)
                        .accessibilityAddTraits(nearby.stop.id == selected.id ? .isSelected : [])
                    }
                }
                .navigationTitle(L("on_spot_stops_nearby"))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) { Button(L("back")) { choosing = false } }
                }
            }
            .navigationViewStyle(.stack)
        }
    }

    private func distance(_ meters: Int) -> String {
        approximate ? L("distance_about", distanceText(meters)) : distanceText(meters)
    }

    private var subtitle: String {
        let distance = nearbyStops.first { $0.stop.id == selected.id }?.distanceMeters.map { self.distance(Int($0.intValue)) }
        return [distance, selected.locality].compactMap { $0 }.joined(separator: " · ")
    }
}

/// "84 m", or "1.2 km" from a kilometre.
func distanceText(_ meters: Int) -> String {
    if meters < 1000 { return L("distance_meters", meters) }
    return L("distance_kilometers", String(format: "%.1f", locale: Locale.current, Double(meters) / 1000))
}
