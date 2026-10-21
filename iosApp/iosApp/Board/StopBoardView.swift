import AVFoundation
import Shared
import SwiftUI

/// The departure board: the iOS counterpart of Android's MainScreen.
struct StopBoardView: View {
    @StateObject private var model = StopBoardModel()
    @StateObject private var updateModel = AppUpdateModel()
    @Environment(\.scenePhase) private var scenePhase
    // A launch argument (-PTOpenSettings YES) opens Settings straight away, so the screen can be
    // reached in a simulator without tapping; debug builds only.
    #if DEBUG
    @State private var showSettings = UserDefaults.standard.bool(forKey: "PTOpenSettings")
    #else
    @State private var showSettings = false
    #endif
    /// The fastest route whose details are open.
    @State private var detailsTrip: TripSelection?
    @State private var showRouteSearch = false

    var body: some View {
        let state = model.state
        let viewModel = model.viewModel
        // Dimmed means unattended: dark, nearly transparent, and any tap wakes it.
        let scheme: ColorScheme? = state.isScreenDimmed ? .dark : state.activeTheme.colorScheme

        ZStack {
            (state.isScreenDimmed ? Color.black : Palette.background).ignoresSafeArea()

            VStack(spacing: 0) {
                BoardHeader(
                    state: state,
                    onCycleDateStyle: { viewModel.cycleDateStyle() },
                    onRefresh: { viewModel.refreshData() },
                    onSearchRoute: { showRouteSearch = true },
                    onOpenSettings: { showSettings = true }
                )
                // Its own anchor: two sheets on one view do not both present on iOS 15.
                .sheet(isPresented: $showRouteSearch, onDismiss: { viewModel.clearRouteSearch() }) {
                    RouteSearchView(state: model.state, viewModel: viewModel)
                }
                if let update = updateModel.state.update {
                    UpdateBanner(update: update, onDismiss: { updateModel.viewModel.dismiss() })
                }
                if state.mode == .onSpot {
                    OnSpotView(state: state, viewModel: viewModel, onOpenSettings: { showSettings = true })
                } else {
                    boardBody(state: state, viewModel: viewModel)
                }
                BoardFooter(state: state)
            }
            .opacity(state.isScreenDimmed ? 0.05 : 1)
            .pixelShift(state.burnInLevel)

            if state.isScreenDimmed {
                Color.clear
                    .contentShape(Rectangle())
                    .ignoresSafeArea()
                    .onTapGesture { viewModel.wakeScreen() }
            }
        }
        .preferredColorScheme(scheme)
        .statusBarHidden(state.isFullScreenMode)
        .hidingHomeIndicator(state.isFullScreenMode)
        .keepingScreenAwake(state.isAlwaysOnEnabled)
        // The camera runs only while the board is on screen and the app is active, as on Android:
        // released in the background and while Settings is open, reopened when the board returns.
        // On-spot mode follows the device on the same terms, except that Settings leaves it running.
        .onAppear {
            startCameraIfAllowed()
            viewModel.startLocationTracking()
        }
        #if DEBUG
        // -PTDemo YES plays a short scripted tour for recording App Store previews in a simulator,
        // where nothing can be tapped. Debug builds only.
        .task {
            guard UserDefaults.standard.bool(forKey: "PTDemo") else { return }
            await runDemo()
        }
        #endif
        .onDisappear {
            viewModel.stopCameraMotion()
            viewModel.stopLocationTracking()
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active && !showSettings { startCameraIfAllowed() } else { viewModel.stopCameraMotion() }
            if phase == .active { viewModel.startLocationTracking() } else if phase == .background { viewModel.stopLocationTracking() }
        }
        .onChange(of: showSettings) { open in
            if open { viewModel.stopCameraMotion() }
        }
        .sheet(isPresented: $showSettings, onDismiss: {
            // Settings are not reactive: the board re-reads them when Settings closes, as Android
            // does on navigating back, and then refetches at once, so stops just added show their
            // departures without waiting for the next refresh (or forever, with refresh off).
            // That may also have switched camera wake on or off, or switched modes.
            viewModel.loadSettings()
            viewModel.startAutoRefresh()
            startCameraIfAllowed()
        }) {
            SettingsView(themeMode: state.themeMode)
                .pageSizedSheet()
        }
    }

    /// The wall board's body: a banner per destination (the searched-for one first), then a panel per favourite stop.
    @ViewBuilder
    private func boardBody(state: StopBoardViewModel.UiState, viewModel: StopBoardViewModel) -> some View {
        // One banner per destination, each appearing as soon as its own route arrives; a
        // destination with no route is left out once it has loaded, but one that failed
        // keeps its slot.
        let routes = state.routeDestinations.filter {
            state.fastestTrips[$0.id] != nil ||
                state.loadingTripDestinationIds.contains($0.id) ||
                state.failedTripDestinationIds.contains($0.id)
        }
        VStack(spacing: 0) {
            ForEach(Array(routes.enumerated()), id: \.element.id) { index, destination in
                if index > 0 { Divider() }
                HStack(spacing: 0) {
                    // Overlaid, not stacked, so the placeholder and the banner cross-fade in place.
                    ZStack {
                        if let trip = state.fastestTrips[destination.id]?.first {
                            FastestTripBanner(
                                trip: trip,
                                isRefreshing: state.loadingTripDestinationIds.contains(destination.id),
                                onTap: { detailsTrip = TripSelection(destinationId: destination.id, trip: trip) }
                            )
                            .transition(.opacity)
                        } else if state.failedTripDestinationIds.contains(destination.id) {
                            FastestTripUnavailable(
                                destinationName: destination.name,
                                isOnline: state.isOnline,
                                isRetrying: state.loadingTripDestinationIds.contains(destination.id)
                            )
                            .transition(.opacity)
                        } else {
                            FastestTripPlaceholder(destinationName: destination.name)
                                .transition(.opacity)
                        }
                    }
                    .animation(.easeInOut(duration: 0.3), value: state.fastestTrips[destination.id] == nil)
                    if destination.id == state.temporaryDestination?.id {
                        TemporaryRouteActions(
                            canSave: state.canSaveTemporaryDestination,
                            onSave: { viewModel.saveTemporaryDestination() },
                            onDismiss: { viewModel.dismissTemporaryDestination() },
                            fillsBackground: true
                        )
                    }
                }
                // The banner fills its own slot; the buttons share its colour at its height.
                .fixedSize(horizontal: false, vertical: true)
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
        if state.favorites.isEmpty {
            EmptyBoard(onOpenSettings: { showSettings = true })
        } else {
            // Columns by the shared rules for the space the panels have. Read from the layout
            // rather than the screen, so an iPad rotation (regular size class both ways) re-lays it out.
            GeometryReader { geometry in
                let count = Int32(state.favorites.count)
                let columns = BoardLayout.shared.columns(
                    stopCount: count,
                    widthDp: Float(geometry.size.width),
                    heightDp: Float(geometry.size.height)
                )
                StopPanels(
                    state: state,
                    viewModel: viewModel,
                    columnSizes: BoardLayout.shared.columnSizes(stopCount: count, columns: columns).map(\.intValue)
                )
            }
            .padding(6)
        }
    }
}

#if DEBUG
extension StopBoardView {
    /// The App Store preview tour: On the spot mode, Settings switching to the wall board, a
    /// route's details, then the dim and wake.
    @MainActor
    private func runDemo() async {
        let viewModel = model.viewModel
        func wait(_ seconds: Double) async { try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000)) }
        // What the mode switch in Settings saves. Set here rather than seeded by the recording
        // script, since the app's own write from the previous take would shadow a seeded value.
        func setMode(_ mode: AppMode) { UserDefaults.standard.set(mode.name, forKey: "app_mode") }
        setMode(.onSpot)
        viewModel.loadSettings()
        await wait(8)
        showSettings = true
        await wait(2)
        setMode(.board)
        await wait(1)
        showSettings = false
        viewModel.loadSettings()
        // The state reaches Swift asynchronously, and the board's fastest route from the network.
        for _ in 0..<12 {
            if model.state.mode == .board && !model.state.fastestTrips.isEmpty { break }
            await wait(0.5)
        }
        await wait(2)
        if let destination = model.state.routeDestinations.first,
           let trip = model.state.fastestTrips[destination.id]?.first {
            detailsTrip = TripSelection(destinationId: destination.id, trip: trip)
            await wait(5)
            detailsTrip = nil
            await wait(2)
        }
        viewModel.dimScreen()
        await wait(3)
        viewModel.wakeScreen()
    }
}
#endif

extension StopBoardView {
    /// Starts camera motion wake if the setting is on. Settings asks for camera access when the
    /// switch is turned on; this asks only when the setting is on but access was never requested
    /// (e.g. after a reinstall). `startCameraMotion()` itself does nothing while the setting is off.
    private func startCameraIfAllowed() {
        let viewModel = model.viewModel
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            viewModel.startCameraMotion()
        case .notDetermined where viewModel.isCameraMotionEnabled():
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async {
                    if granted { viewModel.startCameraMotion() }
                }
            }
        default:
            break
        }
    }
}

private struct EmptyBoard: View {
    let onOpenSettings: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Text(L("no_stops_configured"))
                .font(.title2)
                .foregroundColor(Palette.textPrimary)
            Button(L("open_settings_to_add"), action: onOpenSettings)
                .buttonStyle(.borderedProminent)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }
}

/// One panel per favourite, in columns of `columnSizes` panels each, filled in order and sharing
/// the space equally. A long press drags a panel to reorder the favourites, as on Android.
private struct StopPanels: View {
    let state: StopBoardViewModel.UiState
    let viewModel: StopBoardViewModel
    let columnSizes: [Int]
    // Which panel a drag started from. Set afresh by every drag and only read when a drop lands
    // on another panel. A cancelled drag leaves it set until the next one; the only effect is that
    // a text drop from another app onto a panel would then reorder, which is harmless.
    @State private var dragging: String?

    var body: some View {
        let favorites = Array(state.favorites.enumerated())
        let columns = columnSizes.indices.map { column -> [(offset: Int, element: StopPlace)] in
            let first = columnSizes[..<column].reduce(0, +)
            return Array(favorites[first ..< min(first + columnSizes[column], favorites.count)])
        }
        HStack(spacing: 0) {
            ForEach(columns, id: \.first?.element.id) { column in
                VStack(spacing: 0) {
                    ForEach(column, id: \.element.id) { index, stop in
                        panel(index: index, stop: stop)
                    }
                }
            }
        }
    }

    private func panel(index: Int, stop: StopPlace) -> some View {
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
            onRowCapacity: { viewModel.setPanelRowCapacity(stopId: stop.id, rows: Int32($0)) }
        )
        .onDrag {
            dragging = stop.id
            return NSItemProvider(object: stop.id as NSString)
        }
        .onDrop(of: [.text], delegate: PanelDropDelegate(
            targetIndex: index,
            favorites: state.favorites,
            dragging: $dragging,
            onMove: { from, to in viewModel.reorderFavorites(fromIndex: Int32(from), toIndex: Int32(to)) }
        ))
    }
}

private struct PanelDropDelegate: DropDelegate {
    let targetIndex: Int
    let favorites: [StopPlace]
    @Binding var dragging: String?
    let onMove: (Int, Int) -> Void

    func performDrop(info: DropInfo) -> Bool {
        defer { dragging = nil }
        guard let id = dragging, let from = favorites.firstIndex(where: { $0.id == id }), from != targetIndex else {
            return false
        }
        onMove(from, targetIndex)
        return true
    }

    func dropUpdated(info: DropInfo) -> DropProposal? {
        DropProposal(operation: .move)
    }
}

private struct BoardFooter: View {
    let state: StopBoardViewModel.UiState

    var body: some View {
        VStack(spacing: 2) {
            if let staleMinutes = state.staleAgeMinutes {
                Label(
                    L(state.isOnline ? "offline_stale_data" : "offline_no_internet_stale_data", Int(staleMinutes.intValue)),
                    systemImage: loadFailureSymbol(isOnline: state.isOnline)
                )
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(Palette.error)
            }
            Text(L("last_updated", state.lastUpdated))
                .font(.system(size: 11))
                .foregroundColor(Palette.textPrimary.opacity(0.3))
            HStack(spacing: 8) {
                Text(L("attribution_entur"))
                if state.showWeather {
                    Text(L("attribution_met"))
                }
            }
            .font(.system(size: 9))
            .foregroundColor(Palette.textPrimary.opacity(0.2))
        }
        .padding(.top, 2)
        .padding(.bottom, 4)
    }
}
