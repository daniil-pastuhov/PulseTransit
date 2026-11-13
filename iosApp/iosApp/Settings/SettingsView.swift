import AVFoundation
import Shared
import SwiftUI

/// Settings, the iOS counterpart of Android's SettingsScreen: what the board shows and how the
/// device behaves, every change saved as it is made; the board re-reads them when this sheet
/// closes. Only what applies to the mode is shown. In board mode on a window from 1000 pt wide
/// (iPad), two columns: what the board shows beside how the device behaves.
struct SettingsView: View {
    /// The board's theme setting, so the sheet opens in the same scheme the board uses.
    let themeMode: ThemeMode
    @StateObject private var model = SettingsModel()
    /// Which list is being reordered; move handles need the Form in edit mode, not one section.
    @State private var reordering: ReorderTarget?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let state = model.state
        let viewModel = model.viewModel
        NavigationView {
            GeometryReader { proxy in
                let isBoard = state.appMode == .board
                Group {
                    if isBoard && proxy.size.width >= 1000 {
                        HStack(spacing: 0) {
                            form { whatTheBoardShows }
                            Divider()
                            form { howTheDeviceBehaves }
                        }
                    } else if isBoard {
                        form {
                            whatTheBoardShows
                            howTheDeviceBehaves
                        }
                    } else {
                        form { onTheSpot }
                    }
                }
            }
            .navigationTitle(L("settings_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("back")) { dismiss() }
                }
            }
            .overlay(alignment: .bottom) {
                // Removing a stop is undone here; adding one is undone on Add stop, where it happened.
                let removed = state.pendingUndo as? SettingsViewModelSettingsUndoStopRemoved
                UndoBar(
                    message: removed.map { L("set_stop_removed", $0.stop.name) },
                    serial: removed?.serial ?? 0,
                    onUndo: { withAnimation { viewModel.undo() } },
                    onDismiss: { withAnimation { viewModel.dismissUndo() } }
                )
                .animation(.easeInOut, value: state.pendingUndo?.serial)
            }
        }
        .navigationViewStyle(.stack)
        .preferredColorScheme((state.themeMode).colorScheme ?? themeMode.colorScheme)
        .onAppear {
            viewModel.loadSettings()
            // Debug builds can be launched with -PTRequestLocation YES to start "Detect location"
            // straight away, for checking the permission flow in a simulator without tapping.
            #if DEBUG
            if UserDefaults.standard.bool(forKey: "PTRequestLocation") { viewModel.requestLocationAndNearbyStops() }
            #endif
        }
    }

    private func form<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        Form { content() }
            .environment(\.editMode, .constant(reordering != nil ? .active : .inactive))
    }

    /// Board mode, left: stops, destinations, routes, lines and what departures show.
    @ViewBuilder private var whatTheBoardShows: some View {
        let state = model.state
        let viewModel = model.viewModel
        ModeSection(state: state, viewModel: viewModel)
        StopsSection(model: model, reordering: $reordering)
        DestinationsSection(state: state, viewModel: viewModel, isBoard: true, reordering: $reordering)
        RouteSearchSection(model: model, showsAlternatives: false)
        if state.journeyAlertsSupported { JourneyAlertsSection(model: model) }
        FilteredLinesSection(state: state, viewModel: viewModel)
        TransportSection(state: state, viewModel: viewModel)
    }

    /// Board mode, right: theme, power, waking and dimming, the screen's care, and location.
    @ViewBuilder private var howTheDeviceBehaves: some View {
        let state = model.state
        let viewModel = model.viewModel
        ThemeSection(state: state, viewModel: viewModel)
        PowerSection(state: state, viewModel: viewModel, isBoard: true)
        WakeAndDimSection(state: state, viewModel: viewModel)
        OledSection(state: state, viewModel: viewModel)
        LocationSection(state: state, viewModel: viewModel)
        AboutSection()
    }

    /// On the spot: only what applies when following the device.
    @ViewBuilder private var onTheSpot: some View {
        let state = model.state
        let viewModel = model.viewModel
        ModeSection(state: state, viewModel: viewModel)
        DestinationsSection(state: state, viewModel: viewModel, isBoard: false, reordering: $reordering)
        RouteSearchSection(model: model, showsAlternatives: true)
        if state.journeyAlertsSupported { JourneyAlertsSection(model: model) }
        ThemeSection(state: state, viewModel: viewModel)
        TransportSection(state: state, viewModel: viewModel)
        FilteredLinesSection(state: state, viewModel: viewModel)
        PowerSection(state: state, viewModel: viewModel, isBoard: false)
        AboutSection()
    }
}

/// The list whose rows show move handles.
enum ReorderTarget {
    case stops, destinations
}

/// A section's header: its title and, under it, what the section is for.
private struct SectionHeader<Action: View>: View {
    let title: String
    var hint: String?
    @ViewBuilder var action: () -> Action

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(title).font(.pulse(.subheadline, weight: .semibold)).foregroundColor(Palette.primary)
                Spacer()
                action()
            }
            if let hint {
                Text(hint).font(.pulse(.footnote)).foregroundColor(Palette.textSecondary)
            }
        }
        .textCase(nil)
    }
}

extension SectionHeader where Action == EmptyView {
    init(title: String, hint: String? = nil) {
        self.init(title: title, hint: hint) { EmptyView() }
    }
}

/// A switch with its title and what it does.
private struct SettingToggle: View {
    let title: String
    var hint: String?
    let isOn: Bool
    let onChange: (Bool) -> Void

    var body: some View {
        Toggle(isOn: binding(isOn, onChange)) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.pulse(.body))
                if let hint {
                    Text(hint).font(.pulse(.footnote)).foregroundColor(Palette.textSecondary)
                }
            }
        }
    }
}

// MARK: - Mode

/// The mode as a two-way choice. Switching asks first (T6): it changes what the whole app does,
/// though nothing is lost either way.
private struct ModeSection: View {
    let state: SettingsViewModel.UiState
    let viewModel: SettingsViewModel
    @State private var confirming: AppMode?

    var body: some View {
        Section(header: SectionHeader(title: L("settings_mode"))) {
            VStack(alignment: .leading, spacing: 8) {
                // Bound to the saved mode, so the thumb stays put until the switch is confirmed.
                Picker(L("settings_mode"), selection: Binding(get: { state.appMode }, set: { if $0 != state.appMode { confirming = $0 } })) {
                    Text(L("mode_on_spot")).tag(AppMode.onSpot)
                    Text(L("mode_board")).tag(AppMode.board)
                }
                .pickerStyle(.segmented)
                .labelsHidden()
                Text(L(state.appMode == .board ? "mode_board_description" : "mode_on_spot_description"))
                    .font(.pulse(.footnote))
                    .foregroundColor(Palette.textSecondary)
            }
            .padding(.vertical, 4)
        }
        .alert(
            confirmingTitle,
            isPresented: Binding(get: { confirming != nil }, set: { if !$0 { confirming = nil } }),
            presenting: confirming
        ) { target in
            Button(L(target == .board ? "set_switch_to_board_confirm" : "set_switch_to_spot_confirm")) {
                viewModel.updateAppMode(mode: target)
            }
            Button(L(target == .board ? "set_switch_to_board_keep" : "set_switch_to_spot_keep"), role: .cancel) {}
        } message: { target in
            let stops = state.favorites.count
            if target == .board {
                Text(stops == 0 ? L("set_switch_to_board_empty") : L("set_switch_to_board_body", stops))
            } else {
                Text(L("set_switch_to_spot_body", stops))
            }
        }
    }

    private var confirmingTitle: String {
        L(confirming == .board ? "set_switch_to_board_title" : "set_switch_to_spot_title")
    }
}

// MARK: - What the board shows

/// The board's stops, "3 of 6": tap one for its directions, swipe to remove it (with Undo). In
/// Reorder the rows get move handles, and VoiceOver Move up / Move down actions.
private struct StopsSection: View {
    @ObservedObject var model: SettingsModel
    @Binding var reordering: ReorderTarget?

    var body: some View {
        let state = model.state
        let viewModel = model.viewModel
        let favorites = state.favorites
        let max = Int(SettingsRepositoryCompanion.shared.MAX_FAVORITE_STOPS)
        let isReordering = reordering == .stops
        let hint = favorites.isEmpty ? L("set_no_stops") : (isReordering ? L("set_reorder_hint") : L("set_stops_hint"))
        Section(header: SectionHeader(title: L("set_stops_title", favorites.count, max), hint: hint) {
            if favorites.count > 1 {
                Button(isReordering ? L("set_done") : L("reorder")) {
                    withAnimation { reordering = isReordering ? nil : .stops }
                }
                .font(.pulse(.footnote, weight: .semibold))
                .disabled(reordering == .destinations)
            }
        }) {
            ForEach(Array(favorites.enumerated()), id: \.element.id) { index, stop in
                NavigationLink(destination: StopDirectionsView(model: model, stopId: stop.id)) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(stop.name).font(.pulse(.body))
                        Text(directionsSummary(stop: stop, state: state))
                            .font(.pulse(.footnote))
                            .foregroundColor(Palette.textSecondary)
                            .lineLimit(1)
                    }
                }
                .accessibilityAction(named: L("move_up")) {
                    if index > 0 { viewModel.moveFavorite(fromIndex: Int32(index), toIndex: Int32(index - 1)) }
                }
                .accessibilityAction(named: L("move_down")) {
                    if index < favorites.count - 1 { viewModel.moveFavorite(fromIndex: Int32(index), toIndex: Int32(index + 1)) }
                }
                .deleteDisabled(reordering != nil)
            }
            .onMove { sources, destination in
                // SwiftUI's destination counts the moved row still in place; the ViewModel wants
                // the index it ends up at.
                guard let from = sources.first else { return }
                let to = destination > from ? destination - 1 : destination
                viewModel.moveFavorite(fromIndex: Int32(from), toIndex: Int32(to))
            }
            .onDelete { offsets in
                offsets.map { favorites[$0].id }.forEach { viewModel.removeFavorite(stopId: $0) }
            }
            // Stays visible when full, with the reason, rather than disappearing.
            if favorites.count >= max {
                VStack(alignment: .leading, spacing: 2) {
                    Label(L("set_add_stop"), systemImage: "plus").foregroundColor(Palette.textSecondary)
                    Text(L("set_stops_max", max)).font(.pulse(.footnote)).foregroundColor(Palette.textSecondary)
                }
            } else {
                NavigationLink(destination: AddStopView(model: model)) {
                    Label(L("set_add_stop"), systemImage: "plus").foregroundColor(Palette.primary)
                }
                .accessibilityIdentifier("settings.addStop")
            }
        }
        .onChange(of: favorites.count) { if $0 < 2 && reordering == .stops { reordering = nil } }
    }
}

/// "All directions", or the chosen platforms: "Platform 2, Platform 4".
func directionsSummary(stop: StopPlace, state: SettingsViewModel.UiState) -> String {
    let selected = state.selectedQuays[stop.id] ?? []
    if selected.isEmpty { return L("set_all_directions") }
    guard let withQuays = state.favoriteStopsWithQuays[stop.id] else {
        return L("directions_showing_some", selected.count, selected.count)
    }
    let labels = QuaySectionKt.quaySections(stop: withQuays).flatMap { section in
        section.quays.filter { selected.contains($0.id) }.map { quayLabel($0, stopName: stop.name, sectionTitle: section.title) }
    }
    return labels.isEmpty ? L("directions_showing_some", selected.count, withQuays.quays.count) : labels.joined(separator: ", ")
}

/// The saved destinations: tap one for when it shows, reorder, remove; then the field to add another.
private struct DestinationsSection: View {
    let state: SettingsViewModel.UiState
    let viewModel: SettingsViewModel
    let isBoard: Bool
    @Binding var reordering: ReorderTarget?
    @State private var editing: StopPlace?

    var body: some View {
        let max = Int(SettingsRepositoryCompanion.shared.MAX_DESTINATION_STATIONS)
        let isReordering = reordering == .destinations
        Section(header: SectionHeader(
            title: L(isBoard ? "set_destinations_board" : "set_destinations_spot"),
            hint: L(isBoard ? "set_destinations_board_hint" : "set_destinations_spot_hint")
        ) {
            if state.destinationStations.count > 1 {
                Button(isReordering ? L("set_done") : L("reorder")) {
                    withAnimation { reordering = isReordering ? nil : .destinations }
                }
                .font(.pulse(.footnote, weight: .semibold))
                .disabled(reordering == .stops)
                .accessibilityIdentifier("settings.destinations.reorder")
            }
        }) {
            ForEach(state.destinationStations, id: \.id) { destination in
                let schedule = state.destinationSchedules[destination.id]
                Button { editing = destination } label: {
                    HStack {
                        PlaceKindIcon(kind: destination.kind)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(destination.name).font(.pulse(.body)).foregroundColor(Palette.textPrimary)
                            // An HStack rather than a Label: inside a Form, a Label's icon gets a
                            // fixed-width column that pushes the text away from it.
                            HStack(spacing: 4) {
                                Image(systemName: "clock").accessibilityHidden(true)
                                Text(scheduleSummary(schedule))
                            }
                            .font(.pulse(.footnote))
                            .foregroundColor(schedule == nil ? Palette.textSecondary : Palette.primary)
                        }
                        Spacer()
                    }
                    .contentShape(Rectangle())
                }
                .accessibilityHint(L("schedule_edit", destination.name))
                .deleteDisabled(reordering != nil)
            }
            .onMove { sources, destination in
                guard let from = sources.first else { return }
                let to = destination > from ? destination - 1 : destination
                viewModel.moveDestinationStation(fromIndex: Int32(from), toIndex: Int32(to))
            }
            .onDelete { offsets in
                offsets.map { state.destinationStations[$0].id }.forEach { viewModel.removeDestinationStation(stopId: $0) }
            }
            if state.destinationStations.count >= max {
                Text(L("max_destinations_reached", max)).font(.pulse(.footnote)).foregroundColor(Palette.textSecondary)
            } else {
                HStack {
                    Image(systemName: "magnifyingglass").foregroundColor(Palette.textSecondary).accessibilityHidden(true)
                    TextField(L("set_add_destination"), text: binding(state.destinationQuery) { viewModel.onDestinationQueryChanged(newQuery: $0) })
                        .disableAutocorrection(true)
                }
                if state.isSearchingDestination && state.destinationResults.isEmpty {
                    ProgressView()
                }
                ForEach(state.destinationResults.filter { result in !state.destinationStations.contains { $0.id == result.id } }, id: \.id) { place in
                    Button { viewModel.addDestinationStation(stopPlace: place) } label: {
                        HStack {
                            PlaceKindIcon(kind: place.kind)
                            PlaceLabel(place: place)
                            Spacer()
                            Image(systemName: "plus.circle").foregroundColor(Palette.primary)
                        }
                    }
                    .foregroundColor(Palette.textPrimary)
                    .accessibilityHint(L("add"))
                }
                Text(L("set_destinations_limit", max)).font(.pulse(.caption)).foregroundColor(Palette.textSecondary)
            }
        }
        .onChange(of: state.destinationStations.count) { if $0 < 2 && reordering == .destinations { reordering = nil } }
        .sheet(isPresented: Binding(get: { editing != nil }, set: { if !$0 { editing = nil } })) {
            if let destination = editing {
                DestinationScheduleView(
                    destination: destination,
                    schedule: state.destinationSchedules[destination.id],
                    onSave: { viewModel.setDestinationSchedule(destinationId: destination.id, schedule: $0) }
                )
            }
        }
    }
}

/// How routes are searched: preference, transport, walking speed and, on the spot, how many.
private struct RouteSearchSection: View {
    @ObservedObject var model: SettingsModel
    let showsAlternatives: Bool

    var body: some View {
        let state = model.state
        let allowed = RouteModesView.kindCount - state.excludedRouteModes.count
        Section(header: SectionHeader(title: L("route_settings_title"))) {
            OptionPicker(
                title: L("route_preference"),
                options: [
                    (RoutePreference.fastest, L("route_pref_fastest")),
                    (.fewerTransfers, L("route_pref_fewer_transfers")),
                    (.lessWalking, L("route_pref_less_walking")),
                ],
                selection: state.routePreference,
                onSelect: { model.viewModel.updateRoutePreference(preference: $0) }
            )
            NavigationLink(destination: RouteModesView(model: model)) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(L("route_modes")).font(.pulse(.body))
                        Text(L("set_travel_by_hint")).font(.pulse(.footnote)).foregroundColor(Palette.textSecondary)
                    }
                    Spacer()
                    Text(state.excludedRouteModes.isEmpty ? L("route_modes_all") : L("route_modes_count", allowed, RouteModesView.kindCount))
                        .foregroundColor(Palette.textSecondary)
                }
            }
            OptionPicker(
                title: L("walking_speed"),
                options: [
                    (WalkingSpeed.slow, L("walking_speed_slow")),
                    (.normal, L("walking_speed_normal")),
                    (.fast, L("walking_speed_fast")),
                ],
                selection: state.walkingSpeed,
                onSelect: { model.viewModel.updateWalkingSpeed(speed: $0) }
            )
            if showsAlternatives {
                OptionPicker(
                    title: L("trip_options_title"),
                    footnote: L("trip_options_hint"),
                    options: (0 ... 5).map { count in
                        (Int32(count), count == 0 ? L("trip_options_auto") : String(count))
                    },
                    selection: state.tripOptionsCount,
                    onSelect: { model.viewModel.updateTripOptionsCount(count: $0) }
                )
            }
        }
    }
}

/// How "Notify me about this journey" follows a trip; the choices are on a screen of their own.
private struct JourneyAlertsSection: View {
    @ObservedObject var model: SettingsModel

    var body: some View {
        let state = model.state
        let total = JourneyAlertsView.kinds.count
        Section(header: SectionHeader(title: L("journey_rationale_title"), hint: L("set_journey_hint"))) {
            NavigationLink(destination: JourneyAlertsView(model: model)) {
                HStack {
                    Image(systemName: "bell.fill").foregroundColor(Palette.primary).accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(L("journey_rationale_title")).font(.pulse(.body))
                        Text(L(Self.modeKey(state.journeyMode))).font(.pulse(.footnote)).foregroundColor(Palette.textSecondary)
                    }
                    Spacer()
                    Text(state.disabledJourneyAlerts.isEmpty
                        ? L("journey_alerts_all")
                        : L("journey_alerts_count", total - state.disabledJourneyAlerts.count, total))
                        .foregroundColor(Palette.textSecondary)
                }
            }
            .accessibilityIdentifier("settings.journeyAlerts")
        }
    }

    /// The chosen way of following a journey, or "Not set" until the rationale or this screen picks one.
    private static func modeKey(_ mode: JourneyTrackingMode?) -> String {
        switch mode {
        case .timeOnly: return "journey_mode_time"
        case .location: return "journey_mode_location"
        default: return "not_set"
        }
    }
}

/// Lines hidden from the board, each with a way to show it again.
private struct FilteredLinesSection: View {
    let state: SettingsViewModel.UiState
    let viewModel: SettingsViewModel

    var body: some View {
        Section(
            header: SectionHeader(title: L("set_filtered_title"), hint: L("filtered_lines_hint")),
            footer: state.filteredLines.isEmpty ? nil : Text(L("set_filtered_footer")).font(.pulse(.footnote))
        ) {
            if state.filteredLines.isEmpty {
                Text(L("set_filtered_none")).font(.pulse(.footnote)).foregroundColor(Palette.textSecondary)
            } else {
                ForEach(state.filteredLines.sorted(), id: \.self) { line in
                    HStack {
                        Text(line).font(.pulse(.body, weight: .semibold))
                        Spacer()
                        Button { viewModel.removeFilteredLine(line: line) } label: {
                            Image(systemName: "xmark.circle.fill").foregroundColor(Palette.textSecondary)
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel(L("set_unfilter_line", line))
                    }
                }
            }
        }
    }
}

/// What departures show besides their times.
private struct TransportSection: View {
    let state: SettingsViewModel.UiState
    let viewModel: SettingsViewModel

    var body: some View {
        Section(header: SectionHeader(title: L("set_transport_title"))) {
            SettingToggle(title: L("show_delays"), hint: L("set_delays_hint"), isOn: state.showDelays) { viewModel.updateShowDelays(show: $0) }
            SettingToggle(title: L("show_messages"), hint: L("set_messages_hint"), isOn: state.showMessages) { viewModel.updateShowMessages(show: $0) }
            SettingToggle(title: L("show_cancelled"), isOn: state.showCancelledLines) { viewModel.updateShowCancelledLines(show: $0) }
            SettingToggle(title: L("show_weather"), isOn: state.showWeather) { viewModel.updateShowWeather(show: $0) }
        }
    }
}

// MARK: - How the device behaves

/// Light, dark, the system's, or by the room's light, with that mode's two thresholds.
private struct ThemeSection: View {
    let state: SettingsViewModel.UiState
    let viewModel: SettingsViewModel

    var body: some View {
        Section(header: SectionHeader(title: L("set_theme_title"))) {
            OptionPicker(
                title: L("set_theme_mode"),
                footnote: L("set_theme_hint"),
                options: [(ThemeMode.system, L("theme_system")), (.light, L("theme_light")), (.dark, L("theme_dark")), (.auto, L("theme_auto"))],
                selection: state.themeMode,
                onSelect: { viewModel.updateThemeMode(mode: $0) }
            )
            if state.themeMode == .auto {
                VStack(alignment: .leading, spacing: 4) {
                    Text(L("set_thresholds_title")).font(.pulse(.body))
                    Text(L("set_thresholds_hint")).font(.pulse(.footnote)).foregroundColor(Palette.textSecondary)
                    if let lux = state.currentLux {
                        // iOS has no public ambient light sensor, so this is an estimate; showing it lets
                        // the user pick thresholds that suit their room.
                        Label(L("current_lux", Int(lux.floatValue.rounded())), systemImage: "sun.max")
                            .font(.pulse(.footnote))
                            .foregroundColor(Palette.textSecondary)
                    }
                }
                OptionPicker(
                    title: L("set_thresholds_dark"),
                    options: [Float(10), 30, 50, 100].map { ($0, L("lux_value", Int($0))) },
                    selection: state.autoThemeDarkLux,
                    onSelect: { viewModel.updateAutoThemeDarkLux(lux: $0) }
                )
                OptionPicker(
                    title: L("set_thresholds_light"),
                    options: [Float(100), 150, 200, 250].map { ($0, L("lux_value", Int($0))) },
                    selection: state.autoThemeLightLux,
                    onSelect: { viewModel.updateAutoThemeLightLux(lux: $0) }
                )
            }
        }
    }
}

/// How often things update; on the board also the power-related switches.
private struct PowerSection: View {
    let state: SettingsViewModel.UiState
    let viewModel: SettingsViewModel
    let isBoard: Bool

    var body: some View {
        Section(header: SectionHeader(title: L(isBoard ? "set_power_title" : "set_refresh_title"))) {
            OptionPicker(
                title: L("set_refresh_interval"),
                footnote: L(isBoard ? "set_refresh_hint_board" : "set_refresh_hint_spot"),
                options: [(0, L("refresh_none")), (30, L("refresh_30s")), (60, L("refresh_1m")), (120, L("refresh_2m")), (300, L("refresh_5m"))] as [(Int32, String)],
                selection: state.refreshIntervalSeconds,
                onSelect: { viewModel.updateRefreshInterval(seconds: $0) }
            )
            if isBoard {
                SettingToggle(title: L("set_dynamic_refresh"), hint: L("set_dynamic_refresh_hint"), isOn: state.dynamicRefresh) { viewModel.updateDynamicRefresh(enabled: $0) }
                SettingToggle(title: L("set_full_screen"), hint: L("set_full_screen_hint"), isOn: state.isFullScreenMode) { viewModel.updateFullScreenMode(enabled: $0) }
                SettingToggle(title: L("set_always_on"), hint: L("set_always_on_hint"), isOn: state.isAlwaysOnEnabled) { viewModel.updateAlwaysOnMode(enabled: $0) }
            }
        }
    }
}

/// Camera wake, and the two settings that only matter with it on: sensitivity and auto-dim, shown
/// disabled with the reason while it is off, so the rows don't shift. Turning it on without camera
/// access explains first (`CameraAccessView`).
private struct WakeAndDimSection: View {
    let state: SettingsViewModel.UiState
    let viewModel: SettingsViewModel

    @State private var askingCamera = false
    @State private var cameraAuthorized = AVCaptureDevice.authorizationStatus(for: .video) == .authorized

    var body: some View {
        let on = state.cameraMotionWake
        Section(header: SectionHeader(title: L("set_wake_title"))) {
            SettingToggle(title: L("wake_camera"), hint: L("set_camera_hint"), isOn: on) { enabled in
                // Turned on without camera access: why first, then the prompt, here where the user
                // chose the feature rather than later on the board.
                if enabled && !cameraAuthorized {
                    askingCamera = true
                } else {
                    viewModel.updateCameraMotionWake(enabled: enabled)
                }
            }
            .fullScreenCover(isPresented: $askingCamera) {
                CameraAccessView(
                    onGranted: {
                        cameraAuthorized = true
                        askingCamera = false
                        viewModel.updateCameraMotionWake(enabled: true)
                    },
                    onDismiss: { askingCamera = false }
                )
            }
            if on && !cameraAuthorized {
                // On from before, with access since taken away: say why it does nothing (T4).
                Callout(L("camera_denied_title"), tone: .warning, symbol: "video.slash") {
                    Button(L("open_app_settings")) {
                        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                    }
                }
            }
            OptionPicker(
                title: L("camera_sensitivity"),
                footnote: L(on ? "camera_sensitivity_hint" : "set_sensitivity_off_hint"),
                options: [(CameraSensitivity.low, L("sensitivity_low")), (.medium, L("sensitivity_medium")), (.high, L("sensitivity_high"))],
                selection: state.cameraSensitivity,
                enabled: on,
                onSelect: { viewModel.updateCameraSensitivity(sensitivity: $0) }
            )
            OptionPicker(
                title: L("auto_dim_delay"),
                footnote: L(on ? "set_auto_dim_hint" : "set_auto_dim_off_hint"),
                options: [(15, L("dim_15s")), (30, L("dim_30s")), (60, L("dim_1m")), (120, L("dim_2m")), (0, L("dim_never"))] as [(Int32, String)],
                selection: state.autoDimSeconds,
                enabled: on,
                onSelect: { viewModel.updateAutoDimSeconds(seconds: $0) }
            )
        }
        // Back from the Settings app, where camera access may have been turned on or off.
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
            cameraAuthorized = AVCaptureDevice.authorizationStatus(for: .video) == .authorized
        }
    }
}

private struct OledSection: View {
    let state: SettingsViewModel.UiState
    let viewModel: SettingsViewModel

    var body: some View {
        Section(header: SectionHeader(title: L("set_oled_title"))) {
            OptionPicker(
                title: L("set_pixel_shift"),
                footnote: L("set_pixel_shift_hint"),
                options: [(BurnInProtectionLevel.off, L("burn_in_off")), (.low, L("burn_in_low")), (.medium, L("burn_in_medium")), (.high, L("burn_in_high"))],
                selection: state.burnInLevel,
                onSelect: { viewModel.updateBurnInLevel(level: $0) }
            )
        }
    }
}

/// The board's location: where it stands, finding it (with progress, T4 callouts when refused or
/// failed) and typing it, with each field's own error (T1).
private struct LocationSection: View {
    let state: SettingsViewModel.UiState
    let viewModel: SettingsViewModel
    @State private var latitude = ""
    @State private var longitude = ""

    var body: some View {
        let status = onEnum(of: state.locationStatus)
        let requesting: Bool = {
            switch status {
            case .requestingPrecise, .requestingApproximate: return true
            default: return false
            }
        }()
        Section(header: SectionHeader(title: L("set_location_title"), hint: L("set_location_hint"))) {
            HStack(spacing: 12) {
                Image(systemName: "mappin.and.ellipse").foregroundColor(Palette.primary).accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(L("set_current_coordinates")).font(.pulse(.body))
                    Text(coordinates).font(.pulse(.footnote)).foregroundColor(Palette.textSecondary)
                }
            }
            .accessibilityElement(children: .combine)
            switch status {
            case .denied:
                Callout(L("set_location_denied_body"), tone: .warning, title: L("set_location_denied_title"), symbol: "location.slash") {
                    Button(L("open_app_settings")) {
                        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                    }
                }
            case .failed, .error:
                Callout(L("set_location_failed_body"), tone: .error, title: L("set_location_failed_title"), symbol: "exclamationmark.circle") {
                    Button(L("try_again")) { viewModel.requestLocationAndNearbyStops() }
                }
            default:
                if let line = statusLine(state.locationStatus) {
                    HStack(spacing: 8) {
                        if requesting { ProgressView() } else { Image(systemName: line.symbol).accessibilityHidden(true) }
                        Text(line.text)
                    }
                    .font(.pulse(.footnote))
                    .foregroundColor(Palette.textSecondary)
                }
            }
            Button {
                viewModel.requestLocationAndNearbyStops()
            } label: {
                Label(L("set_detect_location"), systemImage: "location.fill")
            }
            .disabled(requesting)
            VStack(alignment: .leading, spacing: 6) {
                Text(L("set_enter_coordinates")).font(.pulse(.body))
                CoordinateField(label: L("set_latitude"), text: $latitude, error: state.coordinatesError?.latitude.map { errorText($0, isLatitude: true) }) {
                    viewModel.clearCoordinatesError()
                }
                CoordinateField(label: L("set_longitude"), text: $longitude, error: state.coordinatesError?.longitude.map { errorText($0, isLatitude: false) }) {
                    viewModel.clearCoordinatesError()
                }
            }
            .padding(.vertical, 4)
            Button(L("set_save_coordinates")) { viewModel.setManualCoordinates(latStr: latitude, lonStr: longitude) }
        }
    }

    private var coordinates: String {
        guard let lat = state.latitude?.doubleValue, let lon = state.longitude?.doubleValue else { return L("not_set") }
        return String(format: "%.4f, %.4f", locale: Locale(identifier: "en_US_POSIX"), lat, lon)
    }

    private func statusLine(_ status: any SettingsViewModelLocationStatus) -> (symbol: String, text: String)? {
        switch onEnum(of: status) {
        case .notRequested: return ("info.circle", L("loc_not_requested"))
        case .requestingPrecise: return ("", L("loc_requesting_gps"))
        case .requestingApproximate: return ("", L("loc_requesting_network"))
        case let .found(found): return ("checkmark.circle", L("loc_success", L(found.accuracy == .precise ? "gps_source" : "network_source")))
        case .manual: return ("checkmark.circle", L("loc_manual"))
        case .denied, .failed, .error: return nil
        }
    }

    /// What a coordinate field says is wrong, with an example of what to type (T1).
    private func errorText(_ problem: CoordinateProblem, isLatitude: Bool) -> String {
        let example = isLatitude ? "59.9111" : "10.7528"
        switch problem {
        case .missing: return L("set_coord_missing", example)
        case .notANumber: return L("set_coord_not_number", example)
        case .outOfRange: return L(isLatitude ? "set_latitude_range" : "set_longitude_range")
        }
    }
}

/// A coordinate field, with its error under it.
private struct CoordinateField: View {
    let label: String
    @Binding var text: String
    let error: String?
    let onEdit: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            TextField(label, text: $text)
                .keyboardType(.numbersAndPunctuation)
                .disableAutocorrection(true)
                .padding(8)
                .overlay(RoundedRectangle(cornerRadius: FjordTokens.radiusSm).strokeBorder(error == nil ? Palette.outline : Palette.error))
                .onChange(of: text) { _ in onEdit() }
                .accessibilityHint(error ?? "")
            if let error {
                Label(error, systemImage: "exclamationmark.circle")
                    .font(.pulse(.caption))
                    .foregroundColor(Palette.error)
            }
        }
    }
}

/// Where the data comes from, and the app's version.
private struct AboutSection: View {
    var body: some View {
        Section(header: SectionHeader(title: L("set_about_title"))) {
            NavigationLink(destination: DataSourcesView()) {
                HStack {
                    Image(systemName: "building.2").foregroundColor(Palette.primary).accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(L("data_sources_row")).font(.pulse(.body))
                        Text(L("data_sources_row_hint")).font(.pulse(.footnote)).foregroundColor(Palette.textSecondary)
                    }
                }
            }
            .accessibilityIdentifier("settings.dataSources")
            HStack {
                Image(systemName: "info.circle").foregroundColor(Palette.primary).accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(L("set_version")).font(.pulse(.body))
                    Text(L("set_version_hint")).font(.pulse(.footnote)).foregroundColor(Palette.textSecondary)
                }
                Spacer()
                Text(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "")
                    .foregroundColor(Palette.textSecondary)
            }
            .accessibilityElement(children: .combine)
        }
    }
}

// MARK: - Building blocks

struct PlaceLabel: View {
    let place: StopPlace

    var body: some View {
        VStack(alignment: .leading) {
            Text(place.name)
            if let locality = place.locality {
                Text(locality).font(.pulse(.caption)).foregroundColor(.secondary)
            }
        }
    }
}

struct PlaceKindIcon: View {
    let kind: PlaceKind

    var body: some View {
        let name: String
        switch kind {
        case .stop: name = "bus.fill"
        case .address: name = "house.fill"
        case .poi: name = "mappin"
        case .place: name = "map.fill"
        }
        return Image(systemName: name).foregroundColor(.secondary)
    }
}

/// A labelled choice among a few options, as a segmented control. Disabled, it is faded and its
/// footnote says why, keeping its place so nothing shifts when it is enabled.
///
/// The control keeps its own copy of the selection. Bound straight to the ViewModel, a tap would
/// only take effect once the new state came back through the StateFlow, after the tap's animation:
/// the Liquid Glass thumb then vanished and reappeared on the new segment, swelling up and down,
/// instead of sliding across to it.
struct OptionPicker<Value: Hashable>: View {
    let title: String
    var footnote: String?
    let options: [(Value, String)]
    let selection: Value
    var enabled = true
    let onSelect: (Value) -> Void

    @State private var current: Value

    init(
        title: String,
        footnote: String? = nil,
        options: [(Value, String)],
        selection: Value,
        enabled: Bool = true,
        onSelect: @escaping (Value) -> Void
    ) {
        self.title = title
        self.footnote = footnote
        self.options = options
        self.selection = selection
        self.enabled = enabled
        self.onSelect = onSelect
        _current = State(initialValue: selection)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.pulse(.body))
            if let footnote {
                Text(footnote).font(.pulse(.footnote)).foregroundColor(.secondary)
            }
            Picker(title, selection: $current) {
                ForEach(options, id: \.0) { option in
                    Text(option.1).tag(option.0)
                }
            }
            .pickerStyle(.segmented)
            .labelsHidden()
            .disabled(!enabled)
        }
        .opacity(enabled ? 1 : 0.5)
        .padding(.vertical, 4)
        // A tap moves the thumb at once and is then saved; a change made elsewhere (loadSettings)
        // moves it when the state arrives. The guards stop each from echoing the other.
        .onChange(of: current) { if $0 != selection { onSelect($0) } }
        .onChange(of: selection) { if $0 != current { current = $0 } }
    }
}

/// A binding that reads the ViewModel's state and writes through one of its update functions.
func binding<Value>(_ value: Value, _ update: @escaping (Value) -> Void) -> Binding<Value> {
    Binding(get: { value }, set: update)
}
