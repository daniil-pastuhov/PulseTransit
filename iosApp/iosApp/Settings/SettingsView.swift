import AVFoundation
import Shared
import SwiftUI

/// Settings, the iOS counterpart of Android's SettingsScreen. Changes are saved as they are made;
/// the board re-reads them when this sheet closes.
struct SettingsView: View {
    /// The board's theme setting, so the sheet opens in the same scheme the board uses.
    let themeMode: ThemeMode
    @StateObject private var model = SettingsModel()
    /// Destinations being reordered. Move handles need the whole Form in edit mode, not one section.
    @State private var reorderingDestinations = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let state = model.state
        let viewModel = model.viewModel
        NavigationView {
            // Only what applies to the chosen mode is shown: the wall board's screen care, power and
            // stop settings mean nothing to a phone following its owner.
            let isBoard = state.appMode == .board
            Form {
                ModeSection(state: state, viewModel: viewModel)
                DestinationsSection(state: state, viewModel: viewModel, reordering: $reorderingDestinations)
                ThemeSection(state: state, viewModel: viewModel)
                RefreshSection(state: state, viewModel: viewModel)
                if isBoard {
                    ScreenCareSection(state: state, viewModel: viewModel)
                    PowerSection(state: state, viewModel: viewModel)
                }
                TransportSection(state: state, viewModel: viewModel)
                FilteredLinesSection(state: state, viewModel: viewModel)
                if !isBoard {
                    TripOptionsSection(state: state, viewModel: viewModel)
                }
                RouteSearchSection(model: model)
                if state.journeyAlertsSupported {
                    JourneyAlertsSection(model: model)
                }
                if isBoard {
                    LocationSection(state: state, viewModel: viewModel)
                }
                DataSourcesSection()
                if isBoard {
                    AddStopsSection(state: state, viewModel: viewModel)
                    FavoritesSection(state: state, viewModel: viewModel, deleteDisabled: reorderingDestinations)
                }
            }
            .environment(\.editMode, .constant(reorderingDestinations ? .active : .inactive))
            .navigationTitle(L("settings_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("back")) { dismiss() }
                }
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
}

// MARK: - Mode

private struct ModeSection: View {
    let state: SettingsViewModel.UiState
    let viewModel: SettingsViewModel

    var body: some View {
        Section(L("settings_mode")) {
            OptionPicker(
                title: L("settings_mode"),
                footnote: L(state.appMode == .board ? "mode_board_description" : "mode_on_spot_description"),
                options: [(AppMode.onSpot, L("mode_on_spot")), (.board, L("mode_board"))],
                selection: state.appMode,
                onSelect: { viewModel.updateAppMode(mode: $0) }
            )
        }
    }
}

/// Journey alerts in Settings: how a followed journey is tracked and how many of its alerts are on,
/// with the choices themselves on a screen of their own.
/// The companies whose timetables reach Entur, on a screen of their own; it answers "is the bus
/// I take in here?". As on Android, it comes after the settings and before the stop lists.
private struct DataSourcesSection: View {
    var body: some View {
        Section(L("data_sources_title")) {
            NavigationLink(destination: DataSourcesView()) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L("data_sources_row"))
                    Text(L("data_sources_row_hint"))
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            .accessibilityIdentifier("settings.dataSources")
        }
    }
}

private struct JourneyAlertsSection: View {
    @ObservedObject var model: SettingsModel

    var body: some View {
        let state = model.state
        let total = JourneyAlertsView.kinds.count
        Section(L("journey_rationale_title")) {
            NavigationLink(destination: JourneyAlertsView(model: model)) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L(Self.modeKey(state.journeyMode)))
                    Text(state.disabledJourneyAlerts.isEmpty
                        ? L("journey_alerts_all")
                        : L("journey_alerts_count", total - state.disabledJourneyAlerts.count, total))
                        .font(.caption)
                        .foregroundColor(.secondary)
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

private struct TripOptionsSection: View {
    let state: SettingsViewModel.UiState
    let viewModel: SettingsViewModel

    var body: some View {
        Section(L("trip_options_title")) {
            OptionPicker(
                title: L("trip_options_title"),
                footnote: L("trip_options_hint"),
                options: (0 ... 5).map { count in
                    (Int32(count), count == 0 ? L("trip_options_auto") : String(count))
                },
                selection: state.tripOptionsCount,
                onSelect: { viewModel.updateTripOptionsCount(count: $0) }
            )
        }
    }
}

// MARK: - Display

private struct ThemeSection: View {
    let state: SettingsViewModel.UiState
    let viewModel: SettingsViewModel

    var body: some View {
        Section(L("theme_settings")) {
            OptionPicker(
                title: L("day_night_mode"),
                options: [(ThemeMode.system, L("theme_system")), (.light, L("theme_light")), (.dark, L("theme_dark")), (.auto, L("theme_auto"))],
                selection: state.themeMode,
                onSelect: { viewModel.updateThemeMode(mode: $0) }
            )
            if state.themeMode == .auto {
                Text(L("auto_theme_thresholds")).font(.subheadline.bold())
                OptionPicker(
                    title: L("auto_theme_dark"),
                    options: [Float(10), 30, 50, 100].map { ($0, L("lux_value", Int($0))) },
                    selection: state.autoThemeDarkLux,
                    onSelect: { viewModel.updateAutoThemeDarkLux(lux: $0) }
                )
                OptionPicker(
                    title: L("auto_theme_light"),
                    options: [Float(100), 150, 200, 250].map { ($0, L("lux_value", Int($0))) },
                    selection: state.autoThemeLightLux,
                    onSelect: { viewModel.updateAutoThemeLightLux(lux: $0) }
                )
                if let lux = state.currentLux {
                    // iOS has no public ambient light sensor, so this is an estimate; showing it lets
                    // the user pick thresholds that suit their room.
                    Text(L("current_lux", Int(lux.floatValue.rounded())))
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }
            }
        }
    }
}

private struct RefreshSection: View {
    let state: SettingsViewModel.UiState
    let viewModel: SettingsViewModel

    var body: some View {
        Section(L("refresh_interval")) {
            OptionPicker(
                title: L("auto_update_schedule"),
                options: [(0, L("refresh_none")), (30, L("refresh_30s")), (60, L("refresh_1m")), (120, L("refresh_2m")), (300, L("refresh_5m"))] as [(Int32, String)],
                selection: state.refreshIntervalSeconds,
                onSelect: { viewModel.updateRefreshInterval(seconds: $0) }
            )
        }
    }
}

private struct ScreenCareSection: View {
    let state: SettingsViewModel.UiState
    let viewModel: SettingsViewModel

    var body: some View {
        Section(L("oled_screen_care")) {
            OptionPicker(
                title: L("pixel_shift_level"),
                options: [(BurnInProtectionLevel.off, L("burn_in_off")), (.low, L("burn_in_low")), (.medium, L("burn_in_medium")), (.high, L("burn_in_high"))],
                selection: state.burnInLevel,
                onSelect: { viewModel.updateBurnInLevel(level: $0) }
            )
        }
    }
}

private struct PowerSection: View {
    let state: SettingsViewModel.UiState
    let viewModel: SettingsViewModel

    var body: some View {
        Section(L("power_refresh")) {
            Toggle(L("wake_camera"), isOn: binding(state.cameraMotionWake) { enabled in
                viewModel.updateCameraMotionWake(enabled: enabled)
                // Ask for camera access here, where the user just chose the feature, rather than
                // later on the board, where the prompt would come out of nowhere.
                if enabled && AVCaptureDevice.authorizationStatus(for: .video) == .notDetermined {
                    AVCaptureDevice.requestAccess(for: .video) { _ in }
                }
            })
            Toggle(L("dynamic_refresh"), isOn: binding(state.dynamicRefresh) { viewModel.updateDynamicRefresh(enabled: $0) })
            Toggle(L("full_screen_mode"), isOn: binding(state.isFullScreenMode) { viewModel.updateFullScreenMode(enabled: $0) })
            Toggle(L("always_on_display"), isOn: binding(state.isAlwaysOnEnabled) { viewModel.updateAlwaysOnMode(enabled: $0) })
            if state.cameraMotionWake {
                OptionPicker(
                    title: L("auto_dim_delay"),
                    footnote: L("auto_dim_hint"),
                    options: [(15, L("dim_15s")), (30, L("dim_30s")), (60, L("dim_1m")), (120, L("dim_2m")), (0, L("dim_never"))] as [(Int32, String)],
                    selection: state.autoDimSeconds,
                    onSelect: { viewModel.updateAutoDimSeconds(seconds: $0) }
                )
                OptionPicker(
                    title: L("camera_sensitivity"),
                    footnote: L("camera_sensitivity_hint"),
                    options: [(CameraSensitivity.low, L("sensitivity_low")), (.medium, L("sensitivity_medium")), (.high, L("sensitivity_high"))],
                    selection: state.cameraSensitivity,
                    onSelect: { viewModel.updateCameraSensitivity(sensitivity: $0) }
                )
            }
        }
    }
}

private struct TransportSection: View {
    let state: SettingsViewModel.UiState
    let viewModel: SettingsViewModel

    var body: some View {
        Section(L("transport_info")) {
            Toggle(L("show_delays"), isOn: binding(state.showDelays) { viewModel.updateShowDelays(show: $0) })
            Toggle(L("show_messages"), isOn: binding(state.showMessages) { viewModel.updateShowMessages(show: $0) })
            Toggle(L("show_cancelled"), isOn: binding(state.showCancelledLines) { viewModel.updateShowCancelledLines(show: $0) })
            Toggle(L("show_weather"), isOn: binding(state.showWeather) { viewModel.updateShowWeather(show: $0) })
        }
    }
}

private struct FilteredLinesSection: View {
    let state: SettingsViewModel.UiState
    let viewModel: SettingsViewModel

    var body: some View {
        Section(header: Text(L("filtered_lines")), footer: Text(L("filtered_lines_hint"))) {
            if state.filteredLines.isEmpty {
                Text(L("no_filtered_lines")).font(.footnote).foregroundColor(.secondary)
            } else {
                ForEach(state.filteredLines.sorted(), id: \.self) { line in
                    HStack {
                        Text(line).bold()
                        Spacer()
                        Button(role: .destructive) { viewModel.removeFilteredLine(line: line) } label: {
                            Image(systemName: "xmark.circle.fill")
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel(L("remove"))
                    }
                }
            }
        }
    }
}

private struct RouteSearchSection: View {
    @ObservedObject var model: SettingsModel

    var body: some View {
        let state = model.state
        let allowed = RouteModesView.kindCount - state.excludedRouteModes.count
        Section(L("route_settings_title")) {
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
            NavigationLink(destination: RouteModesView(model: model)) {
                HStack {
                    Text(L("route_modes"))
                    Spacer()
                    Text(state.excludedRouteModes.isEmpty ? L("route_modes_all") : L("route_modes_count", allowed, RouteModesView.kindCount))
                        .foregroundColor(.secondary)
                }
            }
        }
    }
}

// MARK: - Places

private struct DestinationsSection: View {
    let state: SettingsViewModel.UiState
    let viewModel: SettingsViewModel
    @State private var editing: StopPlace?
    /// While on, the rows show their move handles; the board shows routes in this order.
    @Binding var reordering: Bool

    private var header: some View {
        HStack {
            Text(L("fastest_route_destination"))
            Spacer()
            if state.destinationStations.count > 1 {
                Button(reordering ? L("reorder_done") : L("reorder")) {
                    withAnimation { reordering.toggle() }
                }
                .font(.footnote)
                .textCase(nil)
                .accessibilityIdentifier("settings.destinations.reorder")
            }
        }
    }

    var body: some View {
        let max = Int(SettingsRepositoryCompanion.shared.MAX_DESTINATION_STATIONS)
        Section(header: header) {
            if state.destinationStations.isEmpty {
                Text(L("no_destination_set")).foregroundColor(.secondary)
            }
            ForEach(state.destinationStations, id: \.id) { destination in
                let schedule = state.destinationSchedules[destination.id]
                HStack {
                    Button { editing = destination } label: {
                        HStack {
                            PlaceKindIcon(kind: destination.kind)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(destination.name).foregroundColor(.primary)
                                // An HStack rather than a Label: inside a Form, a Label's icon gets a
                                // fixed-width column that pushes the text away from it.
                                HStack(spacing: 4) {
                                    Image(systemName: "clock").accessibilityHidden(true)
                                    Text(scheduleSummary(schedule))
                                }
                                .font(.caption)
                                .foregroundColor(schedule == nil ? .secondary : .accentColor)
                            }
                            Spacer()
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.borderless)
                    .accessibilityHint(L("schedule_edit", destination.name))
                    Button(role: .destructive) { viewModel.removeDestinationStation(stopId: destination.id) } label: {
                        Image(systemName: "xmark.circle.fill")
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel(L("remove"))
                }
            }
            .onMove { sources, destination in
                // SwiftUI's destination counts the moved row still in place; the ViewModel wants
                // the index it ends up at.
                guard let from = sources.first else { return }
                let to = destination > from ? destination - 1 : destination
                viewModel.moveDestinationStation(fromIndex: Int32(from), toIndex: Int32(to))
            }
            if state.destinationStations.count >= max {
                Text(L("max_destinations_reached", max)).font(.footnote).foregroundColor(.secondary)
            } else {
                TextField(L("search_destination_hint"), text: binding(state.destinationQuery) { viewModel.onDestinationQueryChanged(newQuery: $0) })
                    .disableAutocorrection(true)
                if state.isSearchingDestination && state.destinationResults.isEmpty {
                    ProgressView()
                }
                ForEach(state.destinationResults, id: \.id) { place in
                    HStack {
                        PlaceKindIcon(kind: place.kind)
                        PlaceLabel(place: place)
                        Spacer()
                        Button(L("select")) { viewModel.addDestinationStation(stopPlace: place) }
                            .buttonStyle(.bordered)
                    }
                }
            }
        }
        .onChange(of: state.destinationStations.count) { if $0 < 2 { reordering = false } }
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

private struct LocationSection: View {
    let state: SettingsViewModel.UiState
    let viewModel: SettingsViewModel
    @State private var manualLatitude = ""
    @State private var manualLongitude = ""

    var body: some View {
        Section(L("location_config")) {
            VStack(alignment: .leading, spacing: 4) {
                Text(L("current_coords")).font(.subheadline.bold())
                let notSet = L("not_set")
                Text("\(L("latitude_val", state.latitude.map { String($0.doubleValue) } ?? notSet)), \(L("longitude_val", state.longitude.map { String($0.doubleValue) } ?? notSet))")
                Text(locationStatusText(state.locationStatus)).font(.footnote).foregroundColor(.secondary)
            }
            Button {
                viewModel.requestLocationAndNearbyStops()
            } label: {
                Label(L("detect_location"), systemImage: "location.fill")
            }
            .disabled(state.isSearchingNearby)
            if state.showManualCoordinatesPrompt {
                Text(L("enter_manual")).font(.footnote)
                TextField(L("latitude_label"), text: $manualLatitude).keyboardType(.numbersAndPunctuation)
                TextField(L("longitude_label"), text: $manualLongitude).keyboardType(.numbersAndPunctuation)
                Button(L("save_coords")) { viewModel.setManualCoordinates(latStr: manualLatitude, lonStr: manualLongitude) }
            }
        }
    }

    private func locationStatusText(_ status: any SettingsViewModelLocationStatus) -> String {
        switch onEnum(of: status) {
        case .notRequested: return L("loc_not_requested")
        case .requestingPrecise: return L("loc_requesting_gps")
        case .requestingApproximate: return L("loc_requesting_network")
        case let .found(found): return L("loc_success", L(found.accuracy == .precise ? "gps_source" : "network_source"))
        case .failed: return L("loc_failed")
        case .denied: return L("loc_denied")
        case let .error(error): return L("loc_error", error.message)
        case .manual: return L("loc_manual")
        case .invalidManualFormat: return L("loc_invalid_format")
        }
    }
}

private struct AddStopsSection: View {
    let state: SettingsViewModel.UiState
    let viewModel: SettingsViewModel

    var body: some View {
        // With no query, the list offers the stops near the saved location instead.
        let showNearby = state.query.trimmingCharacters(in: .whitespaces).isEmpty
        let stops = showNearby ? state.nearbyStops : state.searchResults
        let isLoading = showNearby ? state.isSearchingNearby : state.isSearching
        let max = Int(SettingsRepositoryCompanion.shared.MAX_FAVORITE_STOPS)
        let canAdd = state.favorites.count < max
        Section(L("add_stop_places")) {
            TextField(L("search_hint"), text: binding(state.query) { viewModel.onQueryChanged(newQuery: $0) })
                .disableAutocorrection(true)
            // Search stays open when full, since its results can still remove a stop.
            if !canAdd {
                Text(L("max_favorite_stops_reached", max)).font(.footnote).foregroundColor(.secondary)
            }
            if isLoading && stops.isEmpty {
                ProgressView()
            } else if !stops.isEmpty {
                Text(L(showNearby ? "nearby_stops" : "search_results")).font(.subheadline.bold())
                ForEach(stops, id: \.id) { stop in
                    let isFavorite = state.favorites.contains { $0.id == stop.id }
                    HStack {
                        PlaceLabel(place: stop)
                        Spacer()
                        if isFavorite {
                            Button(L("remove"), role: .destructive) { viewModel.removeFavorite(stopId: stop.id) }
                                .buttonStyle(.bordered)
                        } else {
                            Button(L("add")) { viewModel.addFavorite(stopPlace: stop) }
                                .buttonStyle(.borderedProminent)
                                .disabled(!canAdd)
                        }
                    }
                }
            } else if !showNearby {
                Text(L("no_stops_found")).foregroundColor(.secondary)
            }
        }
    }
}

private struct FavoritesSection: View {
    let state: SettingsViewModel.UiState
    let viewModel: SettingsViewModel
    /// While destinations are reordered the Form is in edit mode, which would offer to delete these.
    var deleteDisabled = false

    var body: some View {
        Section(L("favorite_stops", state.favorites.count)) {
            if state.favorites.isEmpty {
                Text(L("no_favorites")).foregroundColor(.secondary)
            }
            ForEach(state.favorites, id: \.id) { stop in
                FavoriteRow(
                    stop: stop,
                    sections: state.favoriteStopsWithQuays[stop.id].map { QuaySectionKt.quaySections(stop: $0) } ?? [],
                    selected: state.selectedQuays[stop.id] ?? [],
                    onToggleQuay: { viewModel.toggleQuay(stopId: stop.id, quayId: $0) }
                )
                .deleteDisabled(deleteDisabled)
            }
            .onDelete { offsets in
                offsets.map { state.favorites[$0].id }.forEach { viewModel.removeFavorite(stopId: $0) }
            }
        }
    }
}

private struct FavoriteRow: View {
    let stop: StopPlace
    let sections: [QuaySection]
    let selected: Set<String>
    let onToggleQuay: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            PlaceLabel(place: stop)
            if !sections.isEmpty {
                Text(L("directions_quays")).font(.footnote.bold())
                ForEach(sections, id: \.self) { section in
                    if let title = section.title {
                        Text(title).font(.caption).foregroundColor(.secondary).padding(.top, 4)
                    }
                    ForEach(section.quays, id: \.id) { quay in
                        // Nothing selected means every direction is shown.
                        let isOn = selected.isEmpty || selected.contains(quay.id)
                        Button { onToggleQuay(quay.id) } label: {
                            QuayRow(quay: quay, title: title(for: quay, in: section), isOn: isOn)
                        }
                        .buttonStyle(.plain)
                    }
                }
                if selected.isEmpty {
                    Text(L("showing_all_directions")).font(.caption).foregroundColor(.secondary)
                }
            }
        }
        .padding(.vertical, 4)
    }

    private func title(for quay: Quay, in section: QuaySection) -> String {
        if let platform = quay.publicCode, !platform.isEmpty { return L("platform_label", platform) }
        if let description = quay.quayDescription, !description.isEmpty { return description }
        // A quay named after its own stop says nothing the heading doesn't.
        if let name = quay.name, !name.isEmpty, name != stop.name, name != section.title { return name }
        return L("direction_id", String(quay.id.suffix(3)))
    }
}

/** One quay in the picker: its platform, then every line it serves and where its next departures go. */
private struct QuayRow: View {
    let quay: Quay
    let title: String
    let isOn: Bool

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: isOn ? "checkmark.square.fill" : "square")
                .foregroundColor(isOn ? .accentColor : .secondary)
                .font(.title3)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.subheadline.weight(.semibold))
                if !quay.lines.isEmpty {
                    Text(quay.lines.joined(separator: "  ")).font(.caption).lineLimit(2)
                }
                if !quay.destinations.isEmpty {
                    Text("→ " + quay.destinations.joined(separator: ", "))
                        .font(.caption).foregroundColor(.secondary).lineLimit(1)
                }
            }
            Spacer(minLength: 0)
        }
        .contentShape(Rectangle())
        .padding(.vertical, 2)
    }
}

// MARK: - Building blocks

struct PlaceLabel: View {
    let place: StopPlace

    var body: some View {
        VStack(alignment: .leading) {
            Text(place.name)
            if let locality = place.locality {
                Text(locality).font(.caption).foregroundColor(.secondary)
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

/// A labelled choice among a few options, as a segmented control.
///
/// The control keeps its own copy of the selection. Bound straight to the ViewModel, a tap would
/// only take effect once the new state came back through the StateFlow, after the tap's animation:
/// the Liquid Glass thumb then vanished and reappeared on the new segment, swelling up and down,
/// instead of sliding across to it.
private struct OptionPicker<Value: Hashable>: View {
    let title: String
    var footnote: String?
    let options: [(Value, String)]
    let selection: Value
    let onSelect: (Value) -> Void

    @State private var current: Value

    init(title: String, footnote: String? = nil, options: [(Value, String)], selection: Value, onSelect: @escaping (Value) -> Void) {
        self.title = title
        self.footnote = footnote
        self.options = options
        self.selection = selection
        self.onSelect = onSelect
        _current = State(initialValue: selection)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
            if let footnote {
                Text(footnote).font(.caption).foregroundColor(.secondary)
            }
            Picker(title, selection: $current) {
                ForEach(options, id: \.0) { option in
                    Text(option.1).tag(option.0)
                }
            }
            .pickerStyle(.segmented)
            .labelsHidden()
        }
        // A tap moves the thumb at once and is then saved; a change made elsewhere (loadSettings)
        // moves it when the state arrives. The guards stop each from echoing the other.
        .onChange(of: current) { if $0 != selection { onSelect($0) } }
        .onChange(of: selection) { if $0 != current { current = $0 } }
    }
}

/// A binding that reads the ViewModel's state and writes through one of its update functions.
private func binding<Value>(_ value: Value, _ update: @escaping (Value) -> Void) -> Binding<Value> {
    Binding(get: { value }, set: update)
}
