import Shared
import SwiftUI
import UIKit

// Heights the auto route count is worked out from: one alternative's row, and a destination card's
// title and padding. The counterpart of Android's OnSpotContent constants.
private let tripRowHeight: Float = 64
private let destinationHeaderHeight: Float = 72

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
            GeometryReader { geometry in
                if geometry.size.width > geometry.size.height {
                    HStack(spacing: 0) {
                        RoutesSection(state: state, viewModel: viewModel, onOpenSettings: onOpenSettings)
                            .frame(width: geometry.size.width * 0.58)
                        NearbyStopSection(state: state, viewModel: viewModel)
                    }
                } else {
                    VStack(spacing: 0) {
                        RoutesSection(state: state, viewModel: viewModel, onOpenSettings: onOpenSettings)
                            .frame(height: geometry.size.height * 2 / 3)
                        NearbyStopSection(state: state, viewModel: viewModel)
                    }
                }
            }
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
        if state.destinationStations.isEmpty {
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
            let destinations = state.destinationStations.filter {
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
                                onTripTap: { detailsTrip = TripSelection(destinationId: destination.id, trip: $0) }
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

/// One destination's next `count` route alternatives, best first.
private struct DestinationRoutesCard: View {
    let destination: StopPlace
    let trips: [TripInfo]?
    let count: Int
    let isRefreshing: Bool
    let isFailed: Bool
    let isOnline: Bool
    let onTripTap: (TripInfo) -> Void

    var body: some View {
        Group {
            if let trips {
                VStack(alignment: .leading, spacing: 0) {
                    Text(L("route_to", destination.name))
                        .font(.headline)
                        .lineLimit(1)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 4)
                    ForEach(Array(trips.prefix(count).enumerated()), id: \.offset) { index, trip in
                        if index > 0 { Divider().padding(.horizontal, 16) }
                        TripOptionRow(trip: trip, isFirst: index == 0)
                            .contentShape(Rectangle())
                            .onTapGesture { onTripTap(trip) }
                    }
                }
                .padding(.vertical, 8)
                .foregroundColor(Palette.textPrimary)
                .background(Palette.primary.opacity(0.18))
                .overlay(alignment: .bottom) {
                    if isRefreshing { RefreshBar(color: Palette.textPrimary.opacity(0.6)) }
                }
            } else if isFailed {
                FastestTripUnavailable(destinationName: destination.name, isOnline: isOnline, isRetrying: isRefreshing)
            } else {
                FastestTripPlaceholder(destinationName: destination.name)
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
