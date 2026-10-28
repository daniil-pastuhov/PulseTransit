import AVFoundation
import Shared
import SwiftUI

/// The departure board: the iOS counterpart of Android's MainScreen.
struct StopBoardView: View {
    @StateObject private var model = StopBoardModel()
    @StateObject private var updateModel = AppUpdateModel()
    @StateObject private var journeyModel = JourneyAlertsModel()
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.verticalSizeClass) private var verticalSizeClass
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
    /// The followed journey's details, opened from its bar or a notification's "Show journey".
    @State private var followedJourney: TripSelection?
    /// The board's size, for how much of the header fits in iPhone Duo's bar.
    @State private var boardSize = CGSize.zero

    var body: some View {
        let state = model.state
        let viewModel = model.viewModel
        // Dimmed means unattended: dark, nearly transparent, and any tap wakes it.
        let scheme: ColorScheme? = state.isScreenDimmed ? .dark : state.activeTheme.colorScheme

        VerticalBarHost(
            isFullScreen: state.isFullScreenMode,
            allowsBar: !state.isScreenDimmed,
            layoutID: [Int(boardSize.width), Int(boardSize.height), verticalSizeClass == .compact ? 1 : 0]
        ) { placement in
            board(state: state, viewModel: viewModel, showsHeader: placement == .none)
        } actions: {
            BoardActionButtons(
                onRefresh: { viewModel.refreshData() },
                onSearchRoute: { showRouteSearch = true },
                onOpenSettings: { showSettings = true },
                shift: state.burnInLevel
            )
        } info: { placement in
            BarHeader.items(
                placement: placement,
                state: state,
                displaySize: boardSize,
                // Landscape on a phone, where iOS hides the status bar and its clock.
                showsClock: verticalSizeClass == .compact,
                onCycleDateStyle: { viewModel.cycleDateStyle() }
            )
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

    /// The board itself; `showsHeader` is false while the header sits in iPhone Duo's bar.
    @ViewBuilder
    private func board(state: StopBoardViewModel.UiState, viewModel: StopBoardViewModel, showsHeader: Bool) -> some View {
        ZStack {
            (state.isScreenDimmed ? Color.black : Palette.background).ignoresSafeArea()
                .background(GeometryReader { geometry in
                    Color.clear
                        .onAppear { boardSize = geometry.size }
                        .onChange(of: geometry.size) { boardSize = $0 }
                })

            VStack(spacing: 0) {
                // In iPhone Duo's side bar, the header's clock, weather and buttons are there instead.
                if showsHeader {
                    BoardHeader(
                        state: state,
                        onCycleDateStyle: { viewModel.cycleDateStyle() },
                        onRefresh: { viewModel.refreshData() },
                        onSearchRoute: { showRouteSearch = true },
                        onOpenSettings: { showSettings = true }
                    )
                }
                if let update = updateModel.state.update {
                    UpdateBanner(update: update, onDismiss: { updateModel.viewModel.dismiss() })
                }
                if let journey = journeyModel.state.activeJourney {
                    FollowedJourneyBar(
                        journey: journey,
                        onOpen: { openFollowedJourney() },
                        onStop: { journeyModel.viewModel.onStopClick() }
                    )
                }
                if state.mode == .onSpot {
                    OnSpotView(state: state, viewModel: viewModel, onOpenSettings: { showSettings = true })
                } else {
                    boardBody(state: state, viewModel: viewModel)
                }
                BoardFooter(state: state)
            }
            // With the header in iPhone Duo's top row, that row keeps the header's colour.
            .background(alignment: .top) {
                if !showsHeader {
                    Palette.surface.frame(height: 0).ignoresSafeArea(edges: .top)
                }
            }
            // Its own anchor: two sheets on one view do not both present on iOS 15. Not on the
            // header, which is not there while it is in the side bar.
            .sheet(isPresented: $showRouteSearch, onDismiss: { viewModel.clearRouteSearch() }) {
                RouteSearchView(state: model.state, viewModel: viewModel)
            }
            .background(
                Color.clear.sheet(item: $followedJourney) { selection in
                    TripDetailsView(
                        trip: journeyModel.state.activeJourney?.trip ?? selection.trip,
                        showDelays: state.showDelays,
                        showMessages: state.showMessages,
                        loadLegStops: { leg in (try? await viewModel.tripLegStops(leg: leg)) ?? [] },
                        journey: journeyModel
                    )
                }
            )
            .environmentObject(journeyModel)
            .onAppear { if journeyModel.state.openJourneyRequested { openFollowedJourney() } }
            .onChange(of: journeyModel.state.openJourneyRequested) { requested in
                if requested { openFollowedJourney() }
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
    }

    /// Opens the followed journey's details, closing any route's details first: one sheet at a time.
    private func openFollowedJourney() {
        journeyModel.viewModel.onJourneyOpened()
        guard let journey = journeyModel.state.activeJourney else { return }
        detailsTrip = nil
        followedJourney = TripSelection(destinationId: journey.id, trip: journey.trip)
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
                loadLegStops: { leg in (try? await viewModel.tripLegStops(leg: leg)) ?? [] },
                journey: journeyModel
            )
        }
        if state.favorites.isEmpty {
            EmptyBoard(onOpenSettings: { showSettings = true })
        } else {
            // Pages by the shared rules for the space the panels have: one when every stop fits,
            // else the board takes turns. Read from the layout rather than the screen, so an iPad
            // rotation (regular size class both ways) re-lays it out.
            GeometryReader { geometry in
                let pageSizes = BoardLayout.shared.pageSizes(
                    stopCount: Int32(state.favorites.count),
                    widthDp: Float(geometry.size.width),
                    heightDp: Float(geometry.size.height)
                ).map(\.intValue)
                if pageSizes.count > 1 {
                    PagedStopPanels(state: state, viewModel: viewModel, pageSizes: pageSizes, paused: state.isScreenDimmed)
                } else {
                    StopPanels(state: state, viewModel: viewModel, firstIndex: 0, count: state.favorites.count)
                }
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

/// The favourite stops' panels a page at a time, for a board where they do not all fit. The page
/// turns every `PAGE_INTERVAL_MS`, and `BoardPageIndicator` below says which stops are up, how many
/// there are and when the next page comes, as on Android. Turning holds while `paused` (the dimmed
/// screen) and while a departure's details are open, since turning would close them; a swipe to
/// another page, or a drag to reorder, starts the full interval again.
private struct PagedStopPanels: View {
    let state: StopBoardViewModel.UiState
    let viewModel: StopBoardViewModel
    let pageSizes: [Int]
    let paused: Bool
    @State private var page = 0
    @State private var progress: CGFloat = 0
    @State private var openDetails = Set<String>()
    /// Bumped by a drag, to start the current page's interval again.
    @State private var restarts = 0

    private struct Turn: Hashable {
        let page: Int
        let pageCount: Int
        let paused: Bool
        let restarts: Int
    }

    var body: some View {
        let starts = pageSizes.reduce(into: [0]) { $0.append($0[$0.count - 1] + $1) }
        let current = min(page, pageSizes.count - 1)
        let isPaused = paused || !openDetails.isEmpty
        VStack(spacing: 0) {
            TabView(selection: $page) {
                ForEach(pageSizes.indices, id: \.self) { index in
                    StopPanels(
                        state: state,
                        viewModel: viewModel,
                        firstIndex: starts[index],
                        count: pageSizes[index],
                        onDetailsOpenChange: { stopId, open in
                            if open { openDetails.insert(stopId) } else { openDetails.remove(stopId) }
                        },
                        onDragStart: { restarts += 1 }
                    )
                    .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            BoardPageIndicator(
                page: current,
                pageCount: pageSizes.count,
                firstStop: starts[current] + 1,
                lastStop: starts[current] + pageSizes[current],
                stopCount: state.favorites.count,
                paused: isPaused,
                progress: progress
            )
            .frame(height: CGFloat(BoardLayout.shared.PAGE_INDICATOR_HEIGHT_DP))
        }
        .onChange(of: pageSizes.count) { count in page = min(page, count - 1) }
        .task(id: Turn(page: current, pageCount: pageSizes.count, paused: isPaused, restarts: restarts)) {
            var reset = Transaction()
            reset.disablesAnimations = true
            withTransaction(reset) { progress = 0 }
            guard !isPaused else { return }
            // A frame at zero first, so the fill animates from the start rather than from where it was.
            try? await Task.sleep(nanoseconds: 50_000_000)
            guard !Task.isCancelled else { return }
            let interval = Double(BoardLayout.shared.PAGE_INTERVAL_MS) / 1000
            withAnimation(.linear(duration: interval)) { progress = 1 }
            try? await Task.sleep(nanoseconds: UInt64(interval * 1_000_000_000))
            guard !Task.isCancelled else { return }
            withAnimation { page = (current + 1) % pageSizes.count }
        }
    }
}

/// Which stops the page shows ("Stops 3–4 of 6") beside a segment per page; the current page's
/// segment fills over its interval, so the board reads as turning by itself, and when it will.
private struct BoardPageIndicator: View {
    let page: Int
    let pageCount: Int
    let firstStop: Int
    let lastStop: Int
    let stopCount: Int
    let paused: Bool
    let progress: CGFloat

    var body: some View {
        HStack(spacing: 8) {
            Text(firstStop == lastStop ? L("board_page_stop", firstStop, stopCount) : L("board_page_stops", firstStop, lastStop, stopCount))
                .font(.caption2)
                .foregroundColor(Palette.textSecondary)
                .lineLimit(1)
            HStack(spacing: 4) {
                ForEach(0 ..< pageCount, id: \.self) { index in
                    Capsule()
                        .fill(Palette.textPrimary.opacity(0.15))
                        .overlay(alignment: .leading) {
                            if index == page {
                                GeometryReader { geometry in
                                    Capsule()
                                        .fill(Palette.primary)
                                        .frame(width: geometry.size.width * progress)
                                }
                            }
                        }
                        .frame(width: 20, height: 4)
                        .clipShape(Capsule())
                }
            }
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            paused
                ? L("board_page_paused", page + 1, pageCount)
                : L("board_page_description", page + 1, pageCount, Int(BoardLayout.shared.PAGE_INTERVAL_MS / 1000))
        )
    }
}

/// `count` favourites from `firstIndex`, in columns by the shared rules for the space they have,
/// filled in order and sharing it equally. A long press drags a panel to reorder the favourites,
/// as on Android, within the page it is on.
private struct StopPanels: View {
    let state: StopBoardViewModel.UiState
    let viewModel: StopBoardViewModel
    let firstIndex: Int
    let count: Int
    var onDetailsOpenChange: (String, Bool) -> Void = { _, _ in }
    var onDragStart: () -> Void = {}
    // Which panel a drag started from. Set afresh by every drag and only read when a drop lands
    // on another panel. A cancelled drag leaves it set until the next one; the only effect is that
    // a text drop from another app onto a panel would then reorder, which is harmless.
    @State private var dragging: String?

    var body: some View {
        GeometryReader { geometry in
            let columnSizes = BoardLayout.shared.columnSizes(
                stopCount: Int32(count),
                columns: BoardLayout.shared.columns(
                    stopCount: Int32(count),
                    widthDp: Float(geometry.size.width),
                    heightDp: Float(geometry.size.height)
                )
            ).map(\.intValue)
            let end = min(firstIndex + count, state.favorites.count)
            let favorites = Array(state.favorites.enumerated())[firstIndex ..< end].map { $0 }
            let columns = columnSizes.indices.map { column -> [(offset: Int, element: StopPlace)] in
                let first = columnSizes[..<column].reduce(0, +)
                return Array(favorites[min(first, favorites.count) ..< min(first + columnSizes[column], favorites.count)])
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
            onRowCapacity: { viewModel.setPanelRowCapacity(stopId: stop.id, rows: Int32($0)) },
            onDetailsOpenChange: { onDetailsOpenChange(stop.id, $0) },
            showsDragHandle: count > 1
        )
        // A panel paged out of view cannot have its details open any more.
        .onDisappear { onDetailsOpenChange(stop.id, false) }
        .onDrag {
            dragging = stop.id
            onDragStart()
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
