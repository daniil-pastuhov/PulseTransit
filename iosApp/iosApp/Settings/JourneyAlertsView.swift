import Shared
import SwiftUI

/// Journey alerts, pushed from Settings: how "Notify me about this journey" follows a trip, as the
/// rationale offers it, and which of its alerts to show. Location mode still asks for the location
/// through the rationale the first time it is used. It shares Settings' model, so the summary there
/// updates as these change.
struct JourneyAlertsView: View {
    @ObservedObject var model: SettingsModel

    /// Every kind of journey alert, in the order Android lists them, with its label.
    static let kinds: [(JourneyAlertKind, String)] = [
        (.departing, "journey_alert_kind_departing"),
        (.nextStop, "journey_alert_kind_next_stop"),
        (.transfer, "journey_alert_kind_transfer"),
        (.walk, "journey_alert_kind_walk"),
        (.fasterRoute, "journey_alert_kind_faster_route"),
        (.missed, "journey_alert_kind_missed"),
        (.arrived, "journey_alert_kind_arrived"),
    ]

    var body: some View {
        let state = model.state
        Form {
            Section {
                choice(.timeOnly, title: "journey_mode_time", detail: "journey_mode_time_detail_ios")
                choice(.location, title: "journey_mode_location", detail: "journey_mode_location_detail")
            } header: {
                Text(L("journey_tracking_title"))
            } footer: {
                VStack(alignment: .leading, spacing: 8) {
                    // What time-only alerts cannot do on iOS, while they are the ones chosen.
                    if state.journeyMode == .timeOnly {
                        Label(L("journey_ios_time_limit"), systemImage: "info.circle")
                            .accessibilityIdentifier("settings.journeyMode.iosLimit")
                    }
                    Text(L("settings_journey_alerts_hint"))
                }
            }
            Section {
                ForEach(Self.kinds, id: \.0) { kind, title in
                    Toggle(L(title), isOn: Binding(
                        get: { !state.disabledJourneyAlerts.contains(kind) },
                        set: { model.viewModel.updateJourneyAlert(kind: kind, enabled: $0) }
                    ))
                    .accessibilityIdentifier("settings.journeyAlert.\(kind.name.lowercased())")
                }
            } header: {
                Text(L("journey_alert_kinds_title"))
            }
        }
        .navigationTitle(L("journey_rationale_title"))
        .navigationBarTitleDisplayMode(.inline)
    }

    private func choice(_ mode: JourneyTrackingMode, title: String, detail: String) -> some View {
        let selected = model.state.journeyMode == mode
        return Button {
            model.viewModel.updateJourneyMode(mode: mode)
        } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L(title)).foregroundColor(Palette.textPrimary)
                    Text(L(detail)).font(.pulse(.caption)).foregroundColor(.secondary)
                }
                Spacer(minLength: 0)
                if selected {
                    Image(systemName: "checkmark").foregroundColor(Palette.primary)
                }
            }
        }
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityIdentifier("settings.journeyMode.\(mode.name.lowercased())")
    }
}
