import AVFoundation
import Shared
import SwiftUI

/// The departure board: the iOS counterpart of Android's MainScreen.
struct StopBoardView: View {
    @StateObject private var model = StopBoardModel()
    @Environment(\.scenePhase) private var scenePhase
    // A launch argument (-PTOpenSettings YES) opens Settings straight away, so the screen can be
    // reached in a simulator without tapping; debug builds only.
    #if DEBUG
    @State private var showSettings = UserDefaults.standard.bool(forKey: "PTOpenSettings")
    #else
    @State private var showSettings = false
    #endif

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
                    onOpenSettings: { showSettings = true }
                )
                ForEach(Array(state.fastestTrips.enumerated()), id: \.element.destinationName) { index, trip in
                    if index > 0 { Divider() }
                    FastestTripBanner(trip: trip)
                }
                if state.favorites.isEmpty {
                    EmptyBoard(onOpenSettings: { showSettings = true })
                } else {
                    // Side by side when the space is wider than tall. Read from the layout rather than
                    // the screen, so an iPad rotation (regular size class both ways) re-lays it out.
                    GeometryReader { geometry in
                        StopPanels(state: state, viewModel: viewModel, isLandscape: geometry.size.width > geometry.size.height)
                    }
                    .padding(6)
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
        .onAppear { startCameraIfAllowed() }
        #if DEBUG
        // -PTDemo YES plays a short scripted tour for recording App Store previews in a simulator,
        // where nothing can be tapped. Debug builds only.
        .task {
            guard UserDefaults.standard.bool(forKey: "PTDemo") else { return }
            await runDemo()
        }
        #endif
        .onDisappear { viewModel.stopCameraMotion() }
        .onChange(of: scenePhase) { phase in
            if phase == .active && !showSettings { startCameraIfAllowed() } else { viewModel.stopCameraMotion() }
        }
        .onChange(of: showSettings) { open in
            if open { viewModel.stopCameraMotion() }
        }
        .sheet(isPresented: $showSettings, onDismiss: {
            // Settings are not reactive: the board re-reads them when Settings closes, as Android
            // does on navigating back, and then refetches at once, so stops just added show their
            // departures without waiting for the next refresh (or forever, with refresh off).
            // That may also have switched camera wake on or off.
            viewModel.loadSettings()
            viewModel.startAutoRefresh()
            startCameraIfAllowed()
        }) {
            SettingsView(themeMode: state.themeMode)
        }
    }
}

#if DEBUG
extension StopBoardView {
    /// The App Store preview tour: date styles, Settings, then the wall-display dim and wake.
    @MainActor
    private func runDemo() async {
        let viewModel = model.viewModel
        func wait(_ seconds: Double) async { try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000)) }
        await wait(5)
        viewModel.cycleDateStyle()
        await wait(3)
        viewModel.cycleDateStyle()
        await wait(3)
        viewModel.cycleDateStyle()
        await wait(3)
        showSettings = true
        await wait(5)
        showSettings = false
        await wait(3)
        viewModel.dimScreen()
        await wait(4)
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

/// One panel per favourite, sharing the space equally: stacked in portrait, side by side in
/// landscape. A long press drags a panel to reorder the favourites, as on Android.
private struct StopPanels: View {
    let state: StopBoardViewModel.UiState
    let viewModel: StopBoardViewModel
    let isLandscape: Bool
    // Which panel a drag started from. Set afresh by every drag and only read when a drop lands
    // on another panel. A cancelled drag leaves it set until the next one; the only effect is that
    // a text drop from another app onto a panel would then reorder, which is harmless.
    @State private var dragging: String?

    var body: some View {
        let layout = isLandscape ? AnyLayoutBox.horizontal : .vertical
        layout.stack {
            ForEach(Array(state.favorites.enumerated()), id: \.element.id) { index, stop in
                StopPanel(
                    stop: stop,
                    groups: state.departures.first { $0.stopId == stop.id }?.groups ?? [],
                    showDelays: state.showDelays,
                    showMessages: state.showMessages,
                    isLoading: state.isLoading,
                    isExtended: state.extendedStopIds.contains(stop.id),
                    onFilterOut: { viewModel.filterOutLine(line: $0) },
                    onSearchFurtherAhead: { viewModel.searchFurtherAhead(stopId: stop.id) }
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

/// HStack or VStack chosen at runtime; `AnyLayout` needs iOS 16.
private enum AnyLayoutBox {
    case horizontal, vertical

    @ViewBuilder
    func stack<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        switch self {
        case .horizontal: HStack(spacing: 0, content: content)
        case .vertical: VStack(spacing: 0, content: content)
        }
    }
}

private struct BoardFooter: View {
    let state: StopBoardViewModel.UiState

    var body: some View {
        VStack(spacing: 2) {
            if let staleMinutes = state.staleAgeMinutes {
                Label(L("offline_stale_data", Int(staleMinutes.intValue)), systemImage: "icloud.slash")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(Palette.error)
            }
            Text(L("last_updated", state.lastUpdated))
                .font(.system(size: 11))
                .foregroundColor(Palette.textPrimary.opacity(0.3))
            HStack(spacing: 8) {
                Text(L("attribution_entur"))
                Text(L("attribution_met"))
            }
            .font(.system(size: 9))
            .foregroundColor(Palette.textPrimary.opacity(0.2))
        }
        .padding(.top, 2)
        .padding(.bottom, 4)
    }
}
