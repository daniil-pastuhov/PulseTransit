import Shared
import SwiftUI

/// Adding a stop to the board, as Android's AddStopScreen: the stops near the board's location,
/// nearest first, or a search anywhere in Norway. Stops already on the board say Added. Adding acts
/// at once, with Undo; once the board is full (T4) the Add buttons are disabled and a callout says why.
struct AddStopView: View {
    @ObservedObject var model: SettingsModel

    var body: some View {
        let state = model.state
        let viewModel = model.viewModel
        let max = Int(SettingsRepositoryCompanion.shared.MAX_FAVORITE_STOPS)
        let full = state.favorites.count >= max
        let added = Set(state.favorites.map(\.id))
        let searching = !state.query.trimmingCharacters(in: .whitespaces).isEmpty
        List {
            if full {
                Callout(L("add_stop_max_body"), tone: .info, title: L("add_stop_max_title", max), symbol: "info.circle")
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
            }
            Section {
                HStack {
                    Image(systemName: "magnifyingglass").foregroundColor(Palette.textSecondary).accessibilityHidden(true)
                    TextField(L("add_stop_search"), text: binding(state.query) { viewModel.onQueryChanged(newQuery: $0) })
                        .disableAutocorrection(true)
                    if searching {
                        Button { viewModel.onQueryChanged(newQuery: "") } label: {
                            Image(systemName: "xmark.circle.fill").foregroundColor(Palette.textSecondary)
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel(L("add_stop_clear"))
                    }
                }
            }
            if searching {
                results(state: state, added: added, full: full)
            } else {
                nearby(state: state, added: added, full: full)
            }
        }
        .navigationTitle(L("add_stop_title"))
        .navigationBarTitleDisplayMode(.inline)
        .overlay(alignment: .bottom) {
            // Adding is undone here; removing is undone in Settings, where it happened.
            let addedNow = state.pendingUndo as? SettingsViewModelSettingsUndoStopAdded
            UndoBar(
                message: addedNow.map { L("set_stop_added", $0.stop.name) },
                serial: addedNow?.serial ?? 0,
                onUndo: { withAnimation { viewModel.undo() } },
                onDismiss: { withAnimation { viewModel.dismissUndo() } }
            )
            .animation(.easeInOut, value: state.pendingUndo?.serial)
        }
    }

    @ViewBuilder
    private func nearby(state: SettingsViewModel.UiState, added: Set<String>, full: Bool) -> some View {
        if state.latitude == nil || state.longitude == nil {
            Text(L("add_stop_no_location")).font(.pulse(.footnote)).foregroundColor(Palette.textSecondary)
        } else if state.nearbyStops.isEmpty && state.isSearchingNearby {
            StopSkeleton(title: L("add_stop_nearby"))
        } else if state.nearbyStops.isEmpty {
            EmptyListMessage(symbol: "location.slash", title: L("add_stop_none_nearby"), detail: L("add_stop_none_nearby_body"))
        } else {
            Section(header: Text(L("add_stop_nearby")).textCase(nil)) {
                ForEach(state.nearbyStops, id: \.stop.id) { nearby in
                    AddStopRow(stop: nearby.stop, distance: nearby.distanceMeters?.intValue, isAdded: added.contains(nearby.stop.id), full: full) {
                        model.viewModel.addFavorite(stopPlace: nearby.stop)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func results(state: SettingsViewModel.UiState, added: Set<String>, full: Bool) -> some View {
        if state.searchResults.isEmpty && state.isSearching {
            StopSkeleton(title: L("add_stop_results"))
        } else if state.searchResults.isEmpty {
            // An empty result, not an error: it says what to try next.
            EmptyListMessage(symbol: "magnifyingglass", title: L("no_stops_found"), detail: L("add_stop_no_results_body"))
        } else {
            Section(header: Text(L("add_stop_results")).textCase(nil)) {
                ForEach(state.searchResults, id: \.id) { stop in
                    AddStopRow(stop: stop, distance: nil, isAdded: added.contains(stop.id), full: full) {
                        model.viewModel.addFavorite(stopPlace: stop)
                    }
                }
            }
        }
    }
}

/// A stop to add: its name, "Oslo · 60 m", and Add, or Added when it is on the board already.
private struct AddStopRow: View {
    let stop: StopPlace
    let distance: Int?
    let isAdded: Bool
    let full: Bool
    let onAdd: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            PlaceKindIcon(kind: stop.kind)
            VStack(alignment: .leading, spacing: 2) {
                Text(stop.name).font(.pulse(.body))
                let detail = [stop.locality, distance.map(distanceText)].compactMap { $0 }.joined(separator: " · ")
                if !detail.isEmpty {
                    Text(detail).font(.pulse(.footnote)).foregroundColor(Palette.textSecondary)
                }
            }
            Spacer()
            if isAdded {
                Label(L("add_stop_added"), systemImage: "checkmark.circle.fill")
                    .font(.pulse(.subheadline, weight: .semibold))
                    .foregroundColor(Palette.primary)
            } else {
                Button(L("add"), action: onAdd)
                    .buttonStyle(.bordered)
                    .disabled(full)
                    .accessibilityLabel(L("add_stop_add_named", stop.name))
            }
        }
    }
}

/// Rows shaped like the loaded ones while the list loads; the search field stays usable.
private struct StopSkeleton: View {
    let title: String
    @State private var pulse = false

    var body: some View {
        Section(header: Text(title).textCase(nil)) {
            ForEach(0 ..< 4, id: \.self) { _ in
                HStack(spacing: 12) {
                    PlaceholderBlock(width: 22, height: 22, pulse: pulse)
                    VStack(alignment: .leading, spacing: 6) {
                        PlaceholderBlock(width: 150, height: 14, pulse: pulse)
                        PlaceholderBlock(width: 90, height: 11, pulse: pulse)
                    }
                    Spacer()
                    PlaceholderBlock(width: 56, height: 28, pulse: pulse)
                }
                .padding(.vertical, 4)
            }
        }
        .modifier(PlaceholderPulse(pulse: $pulse))
        .accessibilityHidden(true)
    }
}

/// An empty list's answer, centred: what happened and what to try.
struct EmptyListMessage<Action: View>: View {
    let symbol: String
    let title: String
    let detail: String
    @ViewBuilder var action: () -> Action

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: symbol).font(.system(size: 32)).foregroundColor(Palette.textSecondary).accessibilityHidden(true)
            Text(title).font(.pulse(.headline)).multilineTextAlignment(.center)
            if !detail.isEmpty {
                Text(detail).font(.pulse(.subheadline)).foregroundColor(Palette.textSecondary).multilineTextAlignment(.center)
            }
            action()
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
        .listRowBackground(Color.clear)
    }
}

extension EmptyListMessage where Action == EmptyView {
    init(symbol: String, title: String, detail: String) {
        self.init(symbol: symbol, title: title, detail: detail) { EmptyView() }
    }
}
