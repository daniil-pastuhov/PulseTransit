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
    /// The followed journey's details, opened from its bar or a tap on a notification.
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

    /// A board this wide gets the one-row header, larger rows and side-by-side routes, as on Android.
    private var isWide: Bool { boardSize.width >= 700 }
    private var gap: CGFloat { isWide ? FjordTokens.space4 : FjordTokens.space3 }

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

            VStack(spacing: gap) {
                // In iPhone Duo's side bar, the header's clock, weather and buttons are there instead.
                if showsHeader {
                    BoardHeader(
                        state: state,
                        wide: isWide,
                        onCycleDateStyle: { viewModel.cycleDateStyle() },
                        onSearchRoute: { showRouteSearch = true },
                        onOpenSettings: { showSettings = true }
                    )
                }
                BoardNoticeSlot(
                    state: state,
                    update: updateModel.state.update,
                    onRetry: { viewModel.refreshNow() },
                    onDismissUpdate: { updateModel.viewModel.dismiss() }
                )
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
            .padding(.horizontal, isWide ? FjordTokens.space6 : FjordTokens.space3)
            .padding(.vertical, isWide ? FjordTokens.space4 : FjordTokens.space3)
            // Its own anchor: two sheets on one view do not both present on iOS 15. Not on the
            // header, which is not there while it is in the side bar.
            .sheet(isPresented: $showRouteSearch, onDismiss: { viewModel.clearRouteSearch() }) {
                RouteSearchView(state: model.state, viewModel: viewModel, journey: journeyModel)
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
            // Dimmed: dark, at about half brightness, which keeps an OLED board readable across a
            // room without lighting it.
            .opacity(state.isScreenDimmed ? 0.55 : 1)
            .pixelShift(state.burnInLevel)
            .overlay(alignment: .bottom) {
                UndoToast(
                    pending: state.pendingUndo,
                    onUndo: { withAnimation { viewModel.undo() } },
                    onDismiss: { withAnimation { viewModel.dismissUndo() } }
                )
                .animation(.easeInOut, value: state.pendingUndo?.serial)
            }

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
        // keeps its slot, and so does the searched-for one, to say so and keep its buttons.
        let routes = state.routeDestinations.filter {
            state.fastestTrips[$0.id] != nil ||
                state.loadingTripDestinationIds.contains($0.id) ||
                state.failedTripDestinationIds.contains($0.id) ||
                $0.id == state.temporaryDestination?.id
        }
        // Side by side on a wide board, as many as fit at 320 pt or more each.
        let columns = isWide ? max(1, Int(boardSize.width / 320)) : 1
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: FjordTokens.space3), count: min(columns, max(1, routes.count))), spacing: FjordTokens.space2) {
            ForEach(routes, id: \.id) { destination in
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
                        } else if let noRouteTime = state.noRouteTime(destinationId: destination.id) {
                            FastestTripNone(destinationName: destination.name, time: noRouteTime)
                                .transition(.opacity)
                        } else {
                            FastestTripPlaceholder(destinationName: destination.name)
                                .transition(.opacity)
                        }
                    }
                    .animation(.easeInOut(duration: 0.3), value: state.fastestTrips[destination.id] == nil)
                    if destination.id == state.temporaryDestination?.id {
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
            EmptyBoard(onAddStops: { showSettings = true })
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
                    PagedStopPanels(state: state, viewModel: viewModel, pageSizes: pageSizes, paused: state.isScreenDimmed, gap: gap)
                } else {
                    StopPanels(state: state, viewModel: viewModel, firstIndex: 0, count: state.favorites.count, gap: gap)
                }
            }
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
    var gap: CGFloat = FjordTokens.space3
    @State private var page = 0
    @State private var openDetails = Set<String>()
    /// Tapping the page dots holds the current page until they are tapped again.
    @State private var heldByUser = false
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
        let isPaused = paused || heldByUser || !openDetails.isEmpty
        VStack(spacing: FjordTokens.space2) {
            TabView(selection: $page) {
                ForEach(pageSizes.indices, id: \.self) { index in
                    StopPanels(
                        state: state,
                        viewModel: viewModel,
                        firstIndex: starts[index],
                        count: pageSizes[index],
                        gap: gap,
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
                onTogglePause: { heldByUser.toggle() }
            )
            .frame(height: CGFloat(BoardLayout.shared.PAGE_INDICATOR_HEIGHT_DP))
        }
        .onChange(of: pageSizes.count) { count in page = min(page, count - 1) }
        .task(id: Turn(page: current, pageCount: pageSizes.count, paused: isPaused, restarts: restarts)) {
            guard !isPaused else { return }
            try? await Task.sleep(nanoseconds: UInt64(BoardLayout.shared.PAGE_INTERVAL_MS) * 1_000_000)
            guard !Task.isCancelled else { return }
            withAnimation { page = (current + 1) % pageSizes.count }
        }
    }
}

/// Which stops the page shows ("Stops 3–4 of 6") beside a dot per page, the current one long. The
/// board turns by itself every `PAGE_INTERVAL_MS`; VoiceOver hears when. Tapping pauses it.
private struct BoardPageIndicator: View {
    let page: Int
    let pageCount: Int
    let firstStop: Int
    let lastStop: Int
    let stopCount: Int
    let paused: Bool
    let onTogglePause: () -> Void

    var body: some View {
        HStack(spacing: FjordTokens.space2) {
            Text(firstStop == lastStop ? L("board_page_stop", firstStop, stopCount) : L("board_page_stops", firstStop, lastStop, stopCount))
                .font(.pulse(.caption))
                .foregroundColor(Palette.textSecondary)
                .lineLimit(1)
            ForEach(0 ..< pageCount, id: \.self) { index in
                Capsule()
                    .fill(index == page ? Palette.primary : Palette.outlineVariant)
                    .frame(width: index == page ? 20 : 8, height: 8)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 44)
        .contentShape(Rectangle())
        .onTapGesture(perform: onTogglePause)
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isButton)
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
    var gap: CGFloat = FjordTokens.space3
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
            HStack(spacing: gap) {
                ForEach(columns, id: \.first?.element.id) { column in
                    VStack(spacing: gap) {
                        ForEach(column, id: \.element.id) { index, stop in
                            panel(index: index, stop: stop)
                        }
                    }
                }
            }
        }
    }

    /// Refreshes on a pull, and lets the pull go once the refresh has finished.
    private func refreshNow() async {
        viewModel.refreshNow()
        for _ in 0 ..< 60 {
            try? await Task.sleep(nanoseconds: 250_000_000)
            if !viewModel.uiState.value.isManualRefreshing { return }
        }
    }

    private func panel(index: Int, stop: StopPlace) -> some View {
        StopPanel(
            stop: stop,
            groups: state.departures.first { $0.stopId == stop.id }?.groups,
            directions: state.directionSummaries[stop.id],
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
            onRowCapacity: { viewModel.setPanelRowCapacity(stopId: stop.id, rows: Int32($0)) },
            onDetailsOpenChange: { onDetailsOpenChange(stop.id, $0) },
            showsDragHandle: count > 1,
            onPullToRefresh: { await refreshNow() }
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
