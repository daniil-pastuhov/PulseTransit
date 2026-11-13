import Shared
import SwiftUI

/// "Travel by", pushed from Settings: which kinds of transport route searches may use, one section
/// per group. It shares Settings' model, so the summary there updates as these change.
struct RouteModesView: View {
    @ObservedObject var model: SettingsModel

    /// Every kind with its string key, by group, in the order Android shows them.
    static let groups: [(String, RouteModeGroup, String, [(RouteTransportMode, String)])] = [
        ("route_group_bus", .bus, "bus.fill", [
            (.localBus, "route_mode_local_bus"), (.expressBus, "route_mode_express_bus"),
            (.airportBus, "route_mode_airport_bus"), (.railReplacementBus, "route_mode_rail_replacement_bus"),
        ]),
        ("route_group_train", .train, "tram.fill", [
            (.localTrain, "route_mode_local_train"), (.regionalTrain, "route_mode_regional_train"),
            (.longDistanceTrain, "route_mode_long_distance_train"), (.expressTrain, "route_mode_express_train"),
        ]),
        ("route_group_boat", .boat, "ferry.fill", [
            (.carFerry, "route_mode_car_ferry"), (.passengerFerry, "route_mode_passenger_ferry"),
            (.expressBoat, "route_mode_express_boat"),
        ]),
        ("route_group_other", .other, "cablecar.fill", [
            (.metro, "route_mode_metro"), (.tram, "route_mode_tram"), (.plane, "route_mode_plane"),
            (.onDemand, "route_mode_on_demand"), (.cableCar, "route_mode_cable_car"),
        ]),
    ]

    static var kindCount: Int { groups.reduce(0) { $0 + $1.3.count } }

    var body: some View {
        let excluded = model.state.excludedRouteModes
        Form {
            Section(footer: Text(L("route_modes_hint")).font(.pulse(.footnote))) {}
            ForEach(Self.groups, id: \.0) { key, group, symbol, modes in
                let allowed = modes.filter { !excluded.contains($0.0) }.count
                Section {
                    // The group's box: ticked, mixed with "3 of 4", or clear; it sets them all.
                    Button { model.viewModel.toggleRouteModeGroup(group: group) } label: {
                        HStack(spacing: 12) {
                            Image(systemName: symbol).foregroundColor(Palette.primary).accessibilityHidden(true)
                            Text(L(key)).font(.pulse(.headline)).foregroundColor(Palette.textPrimary)
                            Spacer()
                            Text(allowed == modes.count ? L("route_modes_all") : L("route_modes_count", allowed, modes.count))
                                .foregroundColor(Palette.textSecondary)
                            Image(systemName: allowed == modes.count ? "checkmark.square.fill" : (allowed == 0 ? "square" : "minus.square.fill"))
                                .font(.pulse(.title3))
                                .foregroundColor(allowed == 0 ? Palette.textSecondary : Palette.primary)
                                .accessibilityHidden(true)
                        }
                    }
                    // Excluding this group would leave nothing allowed, which the ViewModel refuses: locked instead.
                    .disabled(allowed == modes.count && excluded.count == Self.kindCount - modes.count)
                    .accessibilityLabel(L("travel_by_group_toggle", L(key)))
                    .accessibilityValue(allowed == modes.count ? L("route_modes_all") : L("route_modes_count", allowed, modes.count))
                    ForEach(modes, id: \.0) { mode, kindKey in
                        let isAllowed = !excluded.contains(mode)
                        Toggle(L(kindKey), isOn: Binding(
                            get: { isAllowed },
                            set: { _ in model.viewModel.toggleRouteMode(mode: mode) }
                        ))
                        // The ViewModel refuses to exclude the last allowed kind, and a refused
                        // change publishes no state, so the switch would stay off; it is locked instead.
                        .disabled(isAllowed && excluded.count == Self.kindCount - 1)
                        .padding(.leading, 28)
                    }
                }
            }
        }
        .navigationTitle(L("route_modes"))
        .navigationBarTitleDisplayMode(.inline)
    }
}
