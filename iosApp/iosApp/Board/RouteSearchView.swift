import Shared
import SwiftUI

/// Finds a route to a place that isn't saved and keeps the answer in the sheet, as Android's
/// RouteSearchSheet: the alternatives at the chosen time, then "Save destination". Before typing it
/// offers the saved and recent places. Picking a place also shows its route on the board.
struct RouteSearchView: View {
    let state: StopBoardViewModel.UiState
    let viewModel: StopBoardViewModel
    let journey: JourneyAlertsModel

    @Environment(\.dismiss) private var dismiss
    @FocusState private var isFieldFocused: Bool
    @State private var picked: StopPlace?
    @State private var openTrip: TripSelection?

    var body: some View {
        NavigationView {
            TimelineView(.periodic(from: .now, by: 15)) { timeline in
                let nowMillis = Int64(timeline.date.timeIntervalSince1970 * 1000)
                List {
                    Section {
                        Text(L(state.mode == .onSpot ? "route_from_here" : "route_from_board"))
                            .font(.pulse(.subheadline))
                            .foregroundColor(Palette.textSecondary)
                        TextField(
                            L("search_destination_hint"),
                            text: Binding(
                                get: { picked?.name ?? state.routeSearchQuery },
                                set: {
                                    picked = nil
                                    viewModel.onRouteSearchQueryChanged(query: $0)
                                }
                            )
                        )
                        .disableAutocorrection(true)
                        .submitLabel(.search)
                        .focused($isFieldFocused)
                        TripTimeControls(time: state.temporaryTripTime) { time in
                            viewModel.setTripTime(time: time)
                            // A new time searches the picked place again: a saved destination routed
                            // for now is not the searched place until it has a time of its own.
                            if let picked { viewModel.showTemporaryDestination(place: picked) }
                        }
                    }
                    if let picked {
                        routes(to: picked, nowMillis: nowMillis)
                    } else {
                        places
                    }
                }
            }
            .navigationTitle(L("search_route"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("back")) { dismiss() }
                }
            }
            .sheet(item: $openTrip) { selection in
                TripDetailsView(
                    trip: selection.trip,
                    showDelays: state.showDelays,
                    showMessages: state.showMessages,
                    loadLegStops: { leg in (try? await viewModel.tripLegStops(leg: leg)) ?? [] },
                    journey: journey
                )
            }
        }
        .navigationViewStyle(.stack)
        .onAppear {
            // Focusing in the same pass as the sheet's presentation is ignored, so wait a beat.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { isFieldFocused = true }
        }
    }

    @ViewBuilder
    private var places: some View {
        let query = state.routeSearchQuery.trimmingCharacters(in: .whitespaces)
        if query.isEmpty {
            let saved = state.destinationStations
            let recent = state.recentDestinations.filter { place in !saved.contains { $0.id == place.id } }
            if !saved.isEmpty {
                Section(L("route_saved_destinations")) { ForEach(saved, id: \.id) { placeRow($0, recent: false) } }
            }
            if !recent.isEmpty {
                Section(L("route_recent")) { ForEach(recent, id: \.id) { placeRow($0, recent: true) } }
            }
        } else if state.isSearchingRoutePlaces && state.routeSearchResults.isEmpty {
            ProgressView().frame(maxWidth: .infinity)
        } else if state.routeSearchResults.isEmpty {
            VStack(alignment: .leading, spacing: 4) {
                Text(L("search_route_no_results")).font(.pulse(.headline))
                Text(L("route_no_places_hint")).font(.pulse(.subheadline)).foregroundColor(Palette.textSecondary)
            }
        } else {
            ForEach(state.routeSearchResults, id: \.id) { placeRow($0, recent: false) }
        }
    }

    private func placeRow(_ place: StopPlace, recent: Bool) -> some View {
        Button {
            isFieldFocused = false
            picked = place
            viewModel.showTemporaryDestination(place: place)
        } label: {
            HStack(spacing: 12) {
                if recent {
                    Image(systemName: "clock.arrow.circlepath").foregroundColor(Palette.textSecondary)
                } else {
                    PlaceKindIcon(kind: place.kind)
                }
                PlaceLabel(place: place)
            }
        }
        .foregroundColor(.primary)
    }

    @ViewBuilder
    private func routes(to place: StopPlace, nowMillis: Int64) -> some View {
        let trips = state.fastestTrips[place.id] ?? []
        Section(L("trip_details_title", place.name)) {
            if !trips.isEmpty {
                ForEach(Array(trips.enumerated()), id: \.offset) { _, trip in
                    tripRow(trip, place: place, nowMillis: nowMillis)
                }
            } else if state.loadingTripDestinationIds.contains(place.id) {
                ProgressView().frame(maxWidth: .infinity)
            } else if state.failedTripDestinationIds.contains(place.id) {
                Callout(L("route_offline_body"), tone: .error, title: L(state.isOnline ? "stale_entur_title" : "stale_no_internet_title")) {
                    Button(L("try_again")) { viewModel.refreshNow() }.buttonStyle(.borderless)
                }
            } else if let earliest = state.earliestArrivals[place.id] {
                let arriveBy = state.temporaryTripTime as? TripTimeArriveBy
                Callout(
                    L("route_earliest_body", earliest.expectedEndTime.map { clockText($0, nowMillis: nowMillis) } ?? "", clockText(earliest.expectedStartTime, nowMillis: nowMillis)),
                    tone: .warning,
                    title: arriveBy.map { L("no_route_to_arriving", place.name, tripClockText($0.atMillis)) } ?? L("no_route_to", place.name),
                    symbol: "clock"
                )
                Text(L("route_earliest_title")).font(.pulse(.subheadline, weight: .semibold)).foregroundColor(Palette.textSecondary)
                tripRow(earliest, place: place, nowMillis: nowMillis)
            } else if state.noRouteDestinationIds.contains(place.id) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(L("no_route_to", place.name)).font(.pulse(.headline))
                    Text(L("route_no_route_body")).font(.pulse(.subheadline)).foregroundColor(Palette.textSecondary)
                }
            }
        }
        Section { saveRow(place) }
    }

    private func tripRow(_ trip: TripInfo, place: StopPlace, nowMillis: Int64) -> some View {
        Button {
            openTrip = TripSelection(destinationId: place.id, trip: trip)
        } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(clockText(trip.expectedStartTime, nowMillis: nowMillis)).font(.pulse(.headline))
                    if let minutes = DepartureDetailsKt.minutesUntil(time: trip.expectedStartTime, nowMillis: nowMillis) {
                        Text(inTimeText(minutes.int64Value)).font(.pulse(.caption)).foregroundColor(Palette.textSecondary)
                    }
                }
                TripLegs(trip: trip, maxLines: 2).frame(maxWidth: .infinity, alignment: .leading)
                VStack(alignment: .trailing, spacing: 0) {
                    Text(tripDuration(trip.durationMinutes)).font(.pulse(.subheadline, weight: .semibold))
                    if let end = trip.expectedEndTime {
                        Text(L("route_arrival", clockText(end, nowMillis: nowMillis))).font(.pulse(.caption)).foregroundColor(Palette.textSecondary)
                    }
                }
            }
        }
        .foregroundColor(.primary)
    }

    @ViewBuilder
    private func saveRow(_ place: StopPlace) -> some View {
        if let saved = state.pendingUndo as? BoardUndoDestinationSaved, saved.place.id == place.id {
            Callout(L("destination_saved", place.name), tone: .success) {
                Button(L("undo")) { viewModel.undo() }.buttonStyle(.borderless)
            }
        } else if state.destinationStations.contains(where: { $0.id == place.id }) {
            Label(L("route_saved"), systemImage: "bookmark.fill").foregroundColor(Palette.textSecondary)
        } else if state.canSaveTemporaryDestination && state.temporaryDestination?.id == place.id {
            Button {
                viewModel.saveTemporaryDestination()
            } label: {
                Label(L("save_destination"), systemImage: "bookmark")
            }
        }
    }
}

/// The board's temporary destination's time, save and close buttons; save is left out when it can't be.
struct TemporaryRouteActions: View {
    let time: TripTime
    let onTimeChange: (TripTime) -> Void
    let canSave: Bool
    let onSave: () -> Void
    let onDismiss: () -> Void
    /// Beside a route card: as tall as the card, so the buttons centre on it.
    var fillsBackground = false

    var body: some View {
        HStack(spacing: 4) {
            TripTimeButton(time: time, onChange: onTimeChange)
            if canSave {
                Button(action: onSave) {
                    Image(systemName: "bookmark")
                        .frame(width: 36, height: 36)
                }
                .accessibilityLabel(L("save_destination"))
            }
            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .frame(width: 36, height: 36)
            }
            .accessibilityLabel(L("dismiss_route"))
        }
        .buttonStyle(.borderless)
        .font(.pulse(.title3))
        .foregroundColor(Palette.textPrimary)
        .padding(.trailing, 8)
        .frame(maxHeight: fillsBackground ? .infinity : nil)
    }
}
