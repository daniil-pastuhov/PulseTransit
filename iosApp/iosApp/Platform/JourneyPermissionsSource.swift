import CoreLocation
import Foundation
import Shared
import UserNotifications

/// Journey alerts' permissions on iOS, and their prompts. iOS reports "never asked" itself
/// (`notDetermined`), so nothing needs remembering. The notification setting can only be read
/// asynchronously, so the last answer is cached and refreshed when the app becomes active.
final class JourneyPermissionsSource: NSObject, JourneyPermissions, CLLocationManagerDelegate {
    static let shared = JourneyPermissionsSource()

    /// How long to wait for the location prompt's answer. iOS drops a prompt asked for while another
    /// is up, with no callback, so the wait must end on its own.
    private static let locationPromptTimeout: UInt64 = 60 * 1_000_000_000

    private let manager = CLLocationManager()
    private var notificationStatus: UNAuthorizationStatus = .notDetermined
    private var locationAnswer: CheckedContinuation<Void, Never>?

    override private init() {
        super.init()
        manager.delegate = self
        Task { await refresh() }
    }

    let isSupported = true

    func notificationState() -> PermissionState {
        switch notificationStatus {
        case .notDetermined: return .notAsked
        case .denied: return .denied
        default: return .granted
        }
    }

    func locationState() -> PermissionState {
        switch manager.authorizationStatus {
        case .notDetermined: return .notAsked
        case .denied, .restricted: return .denied
        default: return .granted
        }
    }

    func markAsked(notifications _: Bool, location _: Bool) {}

    /// Re-reads the notification setting, which may have changed in Settings.
    @MainActor
    func refresh() async {
        notificationStatus = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
    }

    /// Shows the prompts asked for, one after the other as iOS requires, and returns what is granted.
    @MainActor
    func request(notifications: Bool, location: Bool) async -> (notifications: Bool, location: Bool) {
        if notifications {
            _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
        }
        await refresh()
        if location, manager.authorizationStatus == .notDetermined {
            await withCheckedContinuation { continuation in
                locationAnswer = continuation
                manager.requestWhenInUseAuthorization()
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: Self.locationPromptTimeout)
                    self.answerLocation()
                }
            }
        }
        return (notificationState() == .granted, locationState() == .granted)
    }

    private func answerLocation() {
        locationAnswer?.resume()
        locationAnswer = nil
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard manager.authorizationStatus != .notDetermined else { return }
        DispatchQueue.main.async { self.answerLocation() }
    }
}
