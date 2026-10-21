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
            LocationStatus(
                message: L(locating.accuracy == .precise ? "locating_precise" : "locating_approximate"),
                failed: false
            )
        case .idle:
            LocationStatus(message: L("locating_precise"), failed: false)
        case .unavailable:
            LocationStatus(message: L("location_unavailable"), failed: true, actionTitle: L("retry")) {
                viewModel.retryLocation()
            }
        case .permissionDenied:
            // iOS asks only once; after a refusal, access can only be granted in the Settings app.
            // The board looks again when the app becomes active, so returning from there is enough.
            LocationStatus(message: L("location_denied"), failed: true, actionTitle: L("location_allow")) {
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

    var body: some View {
        GeometryReader { geometry in
            let isLandscape = geometry.size.width > geometry.size.height
            let total = Float(isLandscape ? geometry.size.width : geometry.size.height) - Float(splitHandleSize)
            let minSize = OnSpotSplit.shared.minSectionSize(
                totalSize: total,
                minSize: isLandscape ? splitMinSectionWidth : splitMinSectionSize
            )
            let routesSize = dragSize ?? OnSpotSplit.shared.routesSize(
                split: pendingSplit ?? state.onSpotSplit,
                totalSize: total,
                barSize: splitBarSize,
                minSize: minSize
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
                        label: L("on_spot_routes"),
                        systemImage: "arrow.triangle.turn.up.right.diamond.fill",
                        expandLabel: L("on_spot_show_routes"),
                        edge: isLandscape ? .leading : .top,
                        onExpand: expand
                    )
                } else {
                    RoutesSection(state: state, viewModel: viewModel, onOpenSettings: onOpenSettings)
                }
            }
            .gesture(drag, including: routesFolded || dragSize != nil ? .all : .subviews)
            let stop = Group {
                if stopFolded {
                    FoldedSection(
                        label: state.favorites.first?.name ?? L("details_departures"),
                        systemImage: "bus.fill",
                        expandLabel: L("on_spot_show_departures"),
                        edge: isLandscape ? .trailing : .bottom,
                        onExpand: expand
                    )
                } else {
                    NearbyStopSection(state: state, viewModel: viewModel)
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
                        .font(.headline)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: arrow)
                }
                .padding(.horizontal, 16)
            }
        }
        .foregroundColor(Palette.textPrimary)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(RoundedRectangle(cornerRadius: 12).fill(Palette.surface))
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

/// Finding the device, or why it could not be found, filling the board until it is.
private struct LocationStatus: View {
    let message: String
    let failed: Bool
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            if failed {
                Image(systemName: "location.slash")
                    .font(.system(size: 40))
                    .foregroundColor(Palette.error)
                    .accessibilityHidden(true)
            } else {
                ProgressView()
            }
            Text(message)
                .font(.headline)
                .multilineTextAlignment(.center)
                .foregroundColor(Palette.textPrimary)
            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .buttonStyle(.borderedProminent)
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
    /// The tapped alternative, whose details are open.
    @State private var detailsTrip: TripSelection?

    var body: some View {
        if state.routeDestinations.isEmpty {
            VStack(spacing: 16) {
                Text(L("routes_add_destination"))
                    .font(.headline)
                    .multilineTextAlignment(.center)
                Button(L("add_destination"), action: onOpenSettings)
                    .buttonStyle(.borderedProminent)
            }
            .foregroundColor(Palette.textPrimary)
            .padding(24)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(RoundedRectangle(cornerRadius: 12).fill(Palette.surface))
            .padding(6)
        } else {
            // A destination with no route is left out once it has loaded, as on the wall board.
            let destinations = state.routeDestinations.filter {
                state.fastestTrips[$0.id] != nil ||
                    state.loadingTripDestinationIds.contains($0.id) ||
                    state.failedTripDestinationIds.contains($0.id)
            }
            GeometryReader { geometry in
                let autoCount = TripOptionLayoutKt.autoTripOptionCount(
                    availableHeight: Float(geometry.size.height),
                    destinationCount: Int32(destinations.count),
                    rowHeight: tripRowHeight,
                    headerHeight: destinationHeaderHeight
                )
                let count = Int(TripOptionLayoutKt.visibleTripOptionCount(setting: state.tripOptionsCount, autoCount: autoCount))
                ScrollView {
                    VStack(spacing: 0) {
                        ForEach(destinations, id: \.id) { destination in
                            DestinationRoutesCard(
                                destination: destination,
                                trips: state.fastestTrips[destination.id],
                                count: count,
                                isRefreshing: state.loadingTripDestinationIds.contains(destination.id),
                                isFailed: state.failedTripDestinationIds.contains(destination.id),
                                isOnline: state.isOnline,
                                onTripTap: { detailsTrip = TripSelection(destinationId: destination.id, trip: $0) },
                                actions: destination.id == state.temporaryDestination?.id
                                    ? TemporaryRouteActions(
                                        canSave: state.canSaveTemporaryDestination,
                                        onSave: { viewModel.saveTemporaryDestination() },
                                        onDismiss: { viewModel.dismissTemporaryDestination() }
                                    )
                                    : nil
                            )
                        }
                    }
                }
            }
            .sheet(item: $detailsTrip) { selection in
                TripDetailsView(
                    trip: TripDetailsKt.currentTrip(trips: state.fastestTrips[selection.destinationId], selected: selection.trip),
                    showDelays: state.showDelays,
                    showMessages: state.showMessages,
                    loadLegStops: { leg in (try? await viewModel.tripLegStops(leg: leg)) ?? [] }
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
    let isOnline: Bool
    let onTripTap: (TripInfo) -> Void
    var actions: TemporaryRouteActions?

    var body: some View {
        Group {
            if let trips {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 0) {
                        Text(L("route_to", destination.name))
                            .font(.headline)
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
                    }
                }
                .padding(.vertical, 8)
                .foregroundColor(Palette.textPrimary)
                .background(Palette.primary.opacity(0.18), ignoresSafeAreaEdges: .horizontal)
                .overlay(alignment: .bottom) {
                    if isRefreshing { RefreshBar(color: Palette.textPrimary.opacity(0.6)) }
                }
            } else {
                HStack(spacing: 0) {
                    if isFailed {
                        FastestTripUnavailable(destinationName: destination.name, isOnline: isOnline, isRetrying: isRefreshing)
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
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .padding(6)
    }
}

/// One alternative: when to leave, the legs, when it arrives and how long it takes.
private struct TripOptionRow: View {
    let trip: TripInfo
    let isFirst: Bool

    var body: some View {
        HStack(spacing: 16) {
            Text(isoClockTime(trip.expectedStartTime))
                .font(isFirst ? .title2.bold() : .title3)
                .monospacedDigit()
            VStack(alignment: .leading, spacing: 2) {
                Text(tripRouteText(trip))
                    .font(.subheadline)
                    .lineLimit(2)
                if let arrival = TripArrivalKt.tripArrival(trip: trip) {
                    let time = arrival.dayOffset > 0 ? "\(arrival.time)⁺\(arrival.dayOffset)" : arrival.time
                    Text(L("route_arrival", time))
                        .font(.caption)
                        .opacity(0.7)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Text(L("duration_min", Int(trip.durationMinutes)))
                .font(.headline)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .accessibilityElement(children: .combine)
    }
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
                onRetry: { viewModel.refreshData() },
                onLoadMore: { viewModel.loadMoreDepartures(stopId: stop.id) },
                loadJourney: { departure in
                    (try? await viewModel.journeyStopsAhead(departure: departure, stopPlaceId: stop.id)) ?? []
                },
                header: AnyView(
                    StopPicker(
                        selected: stop,
                        nearbyStops: state.nearbyStops,
                        isPinned: state.isStopPinned,
                        onSelect: { viewModel.selectStop(stopId: $0) },
                        onFollowNearest: { viewModel.followNearest() }
                    )
                )
            )
        } else {
            Group {
                if state.isSearchingNearby || !state.nearbyStops.isEmpty {
                    ProgressView()
                } else {
                    Text(L("no_stops_nearby")).foregroundColor(Palette.textPrimary)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(RoundedRectangle(cornerRadius: 12).fill(Palette.surface))
            .padding(6)
        }
    }
}

/// The shown stop's name, opening a menu of the other stops nearby; and a way back to the nearest.
private struct StopPicker: View {
    let selected: StopPlace
    let nearbyStops: [NearbyStop]
    let isPinned: Bool
    let onSelect: (String) -> Void
    let onFollowNearest: () -> Void

    var body: some View {
        HStack(alignment: .center, spacing: 8) {
            Menu {
                ForEach(nearbyStops, id: \.stop.id) { nearby in
                    Button {
                        onSelect(nearby.stop.id)
                    } label: {
                        if nearby.stop.id == selected.id {
                            Label(menuTitle(nearby), systemImage: "checkmark")
                        } else {
                            Text(menuTitle(nearby))
                        }
                    }
                }
            } label: {
                HStack(spacing: 4) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(selected.name)
                            .font(.title3.bold())
                            .lineLimit(1)
                        Text(subtitle)
                            .font(.subheadline)
                            .foregroundColor(Palette.textSecondary)
                            .lineLimit(1)
                    }
                    if nearbyStops.count > 1 {
                        Image(systemName: "chevron.down")
                            .font(.subheadline.bold())
                            .accessibilityLabel(L("choose_stop"))
                    }
                }
                .foregroundColor(Palette.textPrimary)
            }
            .disabled(nearbyStops.count <= 1)
            Spacer(minLength: 0)
            if isPinned {
                Button(action: onFollowNearest) {
                    Label(L("back_to_nearest"), systemImage: "location.fill")
                        .font(.footnote)
                }
                .buttonStyle(.bordered)
            }
        }
    }

    private var subtitle: String {
        let distance = nearbyStops.first { $0.stop.id == selected.id }?.distanceMeters.map { distanceText(Int($0.intValue)) }
        return [distance, selected.locality].compactMap { $0 }.joined(separator: " · ")
    }

    private func menuTitle(_ nearby: NearbyStop) -> String {
        guard let meters = nearby.distanceMeters else { return nearby.stop.name }
        return "\(nearby.stop.name) · \(distanceText(Int(meters.intValue)))"
    }
}

private func distanceText(_ meters: Int) -> String {
    if meters < 1000 { return L("distance_meters", meters) }
    return L("distance_kilometers", String(format: "%.1f", locale: Locale.current, Double(meters) / 1000))
}
