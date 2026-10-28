import Shared
import SwiftUI

/// "Notify me about this journey" in a route's details, or "Stop following" while it is the journey
/// being followed. Absent where notifications are denied. The counterpart of Android's
/// `JourneyNotifyButton`; the rationale it may open is presented by `TripDetailsView`.
struct JourneyNotifyButton: View {
    @ObservedObject var model: JourneyAlertsModel
    let trip: TripInfo

    var body: some View {
        if model.state.isFollowing(trip: trip) {
            Button {
                model.viewModel.onStopClick()
            } label: {
                Label(L("journey_stop_button"), systemImage: "bell.slash")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .accessibilityIdentifier("journey.stop")
        } else if model.state.available {
            Button {
                model.viewModel.onNotifyClick(trip: trip)
            } label: {
                Label(L("journey_notify_button"), systemImage: "bell.badge")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .accessibilityIdentifier("journey.notify")
        }
    }
}

/// Why journey alerts want notifications and location, and the choice of how much to give. Says
/// plainly that the location never leaves the device. As Android's `JourneyRationaleDialog`.
struct JourneyRationaleView: View {
    let onChoice: (JourneyTrackingMode) -> Void
    let onDismiss: () -> Void

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Image(systemName: "bell.badge")
                        .font(.largeTitle)
                        .foregroundColor(Palette.primary)
                        .accessibilityHidden(true)
                    Text(L("journey_rationale_body"))
                    Label(L("journey_rationale_privacy"), systemImage: "lock.shield")
                        .foregroundColor(Palette.primary)
                        .accessibilityIdentifier("journey.rationale.privacy")
                    choice(.location, symbol: "location.fill", title: "journey_mode_location", detail: "journey_mode_location_detail")
                        .accessibilityIdentifier("journey.mode.location")
                    choice(.timeOnly, symbol: "clock", title: "journey_mode_time", detail: "journey_mode_time_detail")
                        .accessibilityIdentifier("journey.mode.time")
                }
                .padding(20)
            }
            .foregroundColor(Palette.textPrimary)
            .navigationTitle(L("journey_rationale_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L("journey_rationale_cancel"), action: onDismiss)
                        .accessibilityIdentifier("journey.rationale.cancel")
                }
            }
        }
        .navigationViewStyle(.stack)
    }

    private func choice(_ mode: JourneyTrackingMode, symbol: String, title: String, detail: String) -> some View {
        Button {
            onChoice(mode)
        } label: {
            HStack(spacing: 12) {
                Image(systemName: symbol)
                    .font(.title3)
                    .foregroundColor(Palette.primary)
                    .frame(width: 28)
                VStack(alignment: .leading, spacing: 2) {
                    Text(L(title)).font(.headline)
                    Text(L(detail)).font(.subheadline).foregroundColor(Palette.textSecondary)
                }
                Spacer(minLength: 0)
            }
            .padding(12)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}

/// The journey being followed, under the board's header: tap to see it, or stop following it. As
/// Android's `FollowedJourneyBar`.
struct FollowedJourneyBar: View {
    let journey: TrackedJourney
    let onOpen: () -> Void
    let onStop: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onOpen) {
                HStack(spacing: 12) {
                    Image(systemName: "bell.badge.fill")
                        .accessibilityHidden(true)
                    Text(L("journey_following", journey.trip.destinationName))
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .accessibilityIdentifier("journey.bar")
            Button(L("journey_stop_button"), action: onStop)
                .font(.subheadline.bold())
                .foregroundColor(Palette.primary)
                .accessibilityIdentifier("journey.bar.stop")
        }
        .buttonStyle(.borderless)
        .font(.subheadline)
        .foregroundColor(Palette.textPrimary)
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Palette.primary.opacity(0.18), ignoresSafeAreaEdges: .horizontal)
    }
}

extension JourneyAlertsModel where ViewModel == JourneyAlertsViewModel, State == JourneyAlertsViewModel.UiState {
    /// The rationale's choice: records it, then shows the system prompts it needs and reports back.
    @MainActor
    func choose(_ mode: JourneyTrackingMode) {
        viewModel.onRationaleChoice(mode: mode)
        guard let request = viewModel.uiState.value.permissionRequest else { return }
        Task { @MainActor in
            let granted = await JourneyPermissionsSource.shared.request(
                notifications: request.notifications, location: request.location
            )
            viewModel.onPermissionsResult(notificationsGranted: granted.notifications, locationGranted: granted.location)
        }
    }

    /// Re-reads the permissions, which may have changed in Settings while the app was away.
    @MainActor
    func refreshPermissions() {
        Task { @MainActor in
            await JourneyPermissionsSource.shared.refresh()
            viewModel.refreshPermissions()
        }
    }
}
