import Shared
import SwiftUI

/// Finds a place to route to from the board. Picking one closes the sheet; the board then routes
/// to it until the user dismisses or saves it. As Android's RouteSearchSheet.
struct RouteSearchView: View {
    let state: StopBoardViewModel.UiState
    let viewModel: StopBoardViewModel

    @Environment(\.dismiss) private var dismiss
    @FocusState private var isFieldFocused: Bool

    var body: some View {
        NavigationView {
            List {
                TextField(
                    L("search_destination_hint"),
                    text: Binding(get: { state.routeSearchQuery }, set: { viewModel.onRouteSearchQueryChanged(query: $0) })
                )
                .disableAutocorrection(true)
                .submitLabel(.search)
                .focused($isFieldFocused)
                if state.isSearchingRoutePlaces && state.routeSearchResults.isEmpty {
                    ProgressView().frame(maxWidth: .infinity)
                } else if !state.routeSearchQuery.trimmingCharacters(in: .whitespaces).isEmpty && state.routeSearchResults.isEmpty {
                    Text(L("search_route_no_results")).foregroundColor(.secondary)
                }
                ForEach(state.routeSearchResults, id: \.id) { place in
                    Button {
                        viewModel.showTemporaryDestination(place: place)
                        dismiss()
                    } label: {
                        HStack(spacing: 12) {
                            PlaceKindIcon(kind: place.kind)
                            PlaceLabel(place: place)
                        }
                    }
                    .foregroundColor(.primary)
                }
            }
            .navigationTitle(L("search_route"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("back")) { dismiss() }
                }
            }
        }
        .navigationViewStyle(.stack)
        .onAppear {
            // Focusing in the same pass as the sheet's presentation is ignored, so wait a beat.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { isFieldFocused = true }
        }
    }
}

/// Save and close buttons for the board's temporary destination; save is left out when it can't be.
struct TemporaryRouteActions: View {
    let canSave: Bool
    let onSave: () -> Void
    let onDismiss: () -> Void
    /// Beside a banner, which has its own background: fill the same colour at the banner's height.
    var fillsBackground = false

    var body: some View {
        HStack(spacing: 4) {
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
        .font(.title3)
        .foregroundColor(Palette.textPrimary)
        .padding(.trailing, 8)
        .frame(maxHeight: fillsBackground ? .infinity : nil)
        .background(fillsBackground ? Palette.primary.opacity(0.18) : nil)
    }
}
