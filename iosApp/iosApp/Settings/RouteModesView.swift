import Shared
import SwiftUI

/// "Travel by", pushed from Settings: which kinds of transport route searches may use, one section
/// per group. It shares Settings' model, so the summary there updates as these change.
struct RouteModesView: View {
    @ObservedObject var model: SettingsModel

    /// Every kind with its string key, by group, in the order Android shows them.
    static let groups: [(String, [(RouteTransportMode, String)])] = [
        ("route_group_bus", [
            (.localBus, "route_mode_local_bus"), (.expressBus, "route_mode_express_bus"),
            (.airportBus, "route_mode_airport_bus"), (.railReplacementBus, "route_mode_rail_replacement_bus"),
        ]),
        ("route_group_train", [
            (.localTrain, "route_mode_local_train"), (.regionalTrain, "route_mode_regional_train"),
            (.longDistanceTrain, "route_mode_long_distance_train"), (.expressTrain, "route_mode_express_train"),
        ]),
        ("route_group_boat", [
            (.carFerry, "route_mode_car_ferry"), (.passengerFerry, "route_mode_passenger_ferry"),
            (.expressBoat, "route_mode_express_boat"),
        ]),
        ("route_group_other", [
            (.metro, "route_mode_metro"), (.tram, "route_mode_tram"), (.plane, "route_mode_plane"),
            (.onDemand, "route_mode_on_demand"), (.cableCar, "route_mode_cable_car"),
        ]),
    ]

    static var kindCount: Int { groups.reduce(0) { $0 + $1.1.count } }

    var body: some View {
        let excluded = model.state.excludedRouteModes
        Form {
            ForEach(Self.groups, id: \.0) { group, modes in
                Section(
                    header: Text(L(group)),
                    footer: group == Self.groups.first?.0 ? Text(L("route_modes_hint")) : nil
                ) {
                    ForEach(modes, id: \.0) { mode, key in
                        let allowed = !excluded.contains(mode)
                        Toggle(L(key), isOn: Binding(
                            get: { allowed },
                            set: { _ in model.viewModel.toggleRouteMode(mode: mode) }
                        ))
                        // The ViewModel refuses to exclude the last allowed kind, and a refused
                        // change publishes no state, so the switch would stay off; it is locked instead.
                        .disabled(allowed && excluded.count == Self.kindCount - 1)
                    }
                }
            }
        }
        .navigationTitle(L("route_modes"))
        .navigationBarTitleDisplayMode(.inline)
    }
}
