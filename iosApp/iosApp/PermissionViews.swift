import AVFoundation
import CoreLocation
import SwiftUI

/// The location authorization, kept current: iOS tells the delegate of every change, including
/// one made in the Settings app while PulseTransit was in the background.
final class LocationAuthorization: NSObject, ObservableObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    @Published private(set) var status: CLAuthorizationStatus

    override init() {
        status = manager.authorizationStatus
        super.init()
        manager.delegate = self
    }

    var isGranted: Bool { status == .authorizedWhenInUse || status == .authorizedAlways }
    var isRefused: Bool { status == .denied || status == .restricted }

    /// The system prompt; only shown while undecided, as iOS asks once.
    func request() { manager.requestWhenInUseAuthorization() }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        status = manager.authorizationStatus
    }
}

private func openSystemSettings() {
    if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
}

/// On the spot's ask for location after its intro, as Android's LocationAccessScreen: why it is
/// needed before the system prompt, then, if refused or put off, what still works. iOS asks only
/// once, so while undecided the way on is the prompt, not the Settings app; and carrying on
/// without location is offered only once it is refused, since the board would otherwise just ask.
struct LocationAccessView: View {
    let onDone: () -> Void
    let onUseBoard: () -> Void
    @StateObject private var authorization = LocationAuthorization()
    @State private var putOff = false

    var body: some View {
        Group {
            if putOff || authorization.isRefused {
                PermissionPage(
                    symbol: "location.slash.fill",
                    refused: true,
                    title: L("location_needed_title"),
                    // Undecided, the prompt is the only way on, so the text names no settings.
                    detail: L(authorization.isRefused ? "location_needed_body" : "location_needed_body_undecided")
                ) {
                    if authorization.isRefused {
                        PrimaryPermissionButton(symbol: "arrow.up.forward.app", title: L("open_app_settings"), action: openSystemSettings)
                        OutlinedPermissionButton(title: L("location_continue_without"), action: onDone)
                    } else {
                        PrimaryPermissionButton(symbol: "location.fill", title: L("location_allow"), action: authorization.request)
                    }
                    TextPermissionButton(title: L("use_as_board_instead"), action: onUseBoard)
                }
            } else {
                PermissionPage(
                    symbol: "location.fill",
                    refused: false,
                    title: L("location_rationale_title"),
                    detail: L("location_rationale_body"),
                    points: [
                        ("location.north.fill", L("location_rationale_precise")),
                        ("gearshape", L("location_rationale_turn_off")),
                    ]
                ) {
                    PrimaryPermissionButton(symbol: "location.fill", title: L("location_allow"), action: authorization.request)
                    TextPermissionButton(title: L("not_now")) { putOff = true }
                }
            }
        }
        .onAppear { if authorization.isGranted { onDone() } }
        // Granted in the prompt or in the Settings app: on to the board.
        .onReceive(authorization.$status) { _ in if authorization.isGranted { onDone() } }
    }
}

/// Settings' ask for the camera, where camera wake is turned on, as Android's CameraAccessDialog:
/// privacy is the whole question, so it is answered before the system prompt. `onGranted` turns
/// the setting on; refused, it says what the board does without it, and `onDismiss` leaves it off.
struct CameraAccessView: View {
    let onGranted: () -> Void
    let onDismiss: () -> Void
    @State private var refused = Self.isRefused

    private static var isRefused: Bool {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        return status == .denied || status == .restricted
    }

    var body: some View {
        NavigationView {
            Group {
                if refused {
                    PermissionPage(
                        symbol: "video.slash.fill",
                        refused: true,
                        title: L("camera_denied_title"),
                        detail: L("camera_denied_body")
                    ) {
                        PrimaryPermissionButton(symbol: "arrow.up.forward.app", title: L("open_app_settings"), action: openSystemSettings)
                        TextPermissionButton(title: L("camera_keep_off"), action: onDismiss)
                    }
                } else {
                    PermissionPage(
                        symbol: "camera.fill",
                        refused: false,
                        title: L("camera_rationale_title"),
                        detail: L("camera_rationale_body"),
                        points: [
                            ("lock.fill", L("camera_rationale_on_device")),
                            ("video.slash", L("camera_rationale_nothing_kept")),
                            ("slider.horizontal.3", L("camera_rationale_sensitivity")),
                        ]
                    ) {
                        PrimaryPermissionButton(symbol: "camera.fill", title: L("camera_allow")) {
                            AVCaptureDevice.requestAccess(for: .video) { granted in
                                DispatchQueue.main.async {
                                    if granted { onGranted() } else { refused = true }
                                }
                            }
                        }
                        TextPermissionButton(title: L("not_now"), action: onDismiss)
                    }
                }
            }
            .navigationTitle(L("wake_camera"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("back"), action: onDismiss) }
            }
        }
        .navigationViewStyle(.stack)
        // Back from the Settings app with access turned on there.
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
            if refused, AVCaptureDevice.authorizationStatus(for: .video) == .authorized { onGranted() }
        }
    }
}

/// The layout every permission screen shares, in the onboarding column: the art, the title, why,
/// the points that answer the usual worries, and the actions at the bottom.
private struct PermissionPage<Actions: View>: View {
    let symbol: String
    let refused: Bool
    let title: String
    let detail: String
    var points: [(String, String)] = []
    @ViewBuilder let actions: () -> Actions

    var body: some View {
        VStack(spacing: 0) {
            GeometryReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: FjordTokens.space4) {
                        art
                        Text(title)
                            .font(.pulse(role: FjordTokens.typeHeadline, relativeTo: .title))
                            .foregroundColor(Palette.textPrimary)
                            .accessibilityAddTraits(.isHeader)
                        Text(detail)
                            .font(.pulse(.body))
                            .foregroundColor(Palette.textSecondary)
                        ForEach(points, id: \.1) { point in
                            HStack(alignment: .top, spacing: FjordTokens.space3) {
                                Image(systemName: point.0)
                                    .foregroundColor(Palette.primary)
                                    .frame(width: 24)
                                    .accessibilityHidden(true)
                                Text(point.1)
                                    .font(.pulse(.subheadline))
                                    .foregroundColor(Palette.textPrimary)
                            }
                        }
                    }
                    .frame(maxWidth: OnboardingLayout.columnWidth)
                    .padding(.horizontal, FjordTokens.space4)
                    .padding(.vertical, FjordTokens.space4)
                    .frame(maxWidth: .infinity, minHeight: proxy.size.height)
                }
            }
            VStack(spacing: FjordTokens.space2, content: actions)
                .frame(maxWidth: OnboardingLayout.columnWidth)
                .padding(.horizontal, FjordTokens.space4)
                .padding(.vertical, FjordTokens.space4)
        }
        .background(Palette.background.ignoresSafeArea())
    }

    /// A tile with the feature's symbol: in the primary colours to ask, muted once refused.
    private var art: some View {
        RoundedRectangle(cornerRadius: FjordTokens.radiusXl)
            .fill(Palette.surfaceContainer)
            .aspectRatio(4 / 3, contentMode: .fit)
            .frame(maxWidth: 300)
            .overlay(
                Image(systemName: symbol)
                    .font(.system(size: 52, weight: .semibold))
                    .foregroundColor(refused ? Palette.outline : Palette.primary)
                    .frame(width: 124, height: 124)
                    .background(Circle().fill(refused ? Palette.surface : Palette.primaryContainer))
            )
            .accessibilityHidden(true)
    }
}

private struct PrimaryPermissionButton: View {
    let symbol: String
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label(title, systemImage: symbol)
                .font(.pulse(.headline))
                .foregroundColor(Palette.onPrimary)
                .frame(maxWidth: .infinity, minHeight: FjordTokens.touch)
                .background(Capsule().fill(Palette.primary))
        }
        .buttonStyle(.plain)
    }
}

private struct OutlinedPermissionButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.pulse(.headline))
                .foregroundColor(Palette.primary)
                .frame(maxWidth: .infinity, minHeight: FjordTokens.touch)
                .overlay(Capsule().strokeBorder(Palette.outline))
        }
        .buttonStyle(.plain)
    }
}

private struct TextPermissionButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.pulse(.headline))
                .foregroundColor(Palette.primary)
                .frame(maxWidth: .infinity, minHeight: FjordTokens.touch)
        }
        .buttonStyle(.plain)
    }
}
