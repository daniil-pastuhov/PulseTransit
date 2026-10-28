import Foundation
import Shared
import UIKit
import UserNotifications

/// Journey alerts as iOS notifications, and their actions: "Skip next alerts", "Show journey" and,
/// on the walk to the destination, "Navigate me". The progress goes to the Live Activity.
///
/// iOS suspends the app unless the journey reads the location, so every alert
/// still to come is also scheduled with the system, under the alert's own id. The tracker shows an
/// alert as it falls due when the app runs; one the system has already delivered is not repeated.
final class JourneyNotifications: NSObject, JourneyNotifier, UNUserNotificationCenterDelegate {
    static let shared = JourneyNotifications()

    private enum Category {
        static let alert = "JOURNEY_ALERT"
        static let finalWalk = "JOURNEY_FINAL_WALK"
        static let info = "JOURNEY_INFO"
    }

    private enum Action {
        static let skip = "JOURNEY_SKIP"
        static let show = "JOURNEY_SHOW"
        static let navigate = "JOURNEY_NAVIGATE"
    }

    private static let prefix = "journey."
    private let center = UNUserNotificationCenter.current()
    private let clock = SystemClockFormatter()
    /// What is scheduled now, by notification id, so an unchanged schedule is not redone each tick.
    private var scheduled: [String: Int64] = [:]

    override private init() {
        super.init()
    }

    /// Call at launch, before the app finishes launching, so a tap that launched it is delivered.
    func register() {
        center.delegate = self
        let skip = UNNotificationAction(identifier: Action.skip, title: L("journey_action_skip"))
        let show = UNNotificationAction(identifier: Action.show, title: L("journey_action_show"), options: [.foreground])
        let navigate = UNNotificationAction(identifier: Action.navigate, title: L("journey_action_navigate"), options: [.foreground])
        center.setNotificationCategories([
            UNNotificationCategory(identifier: Category.alert, actions: [skip, show], intentIdentifiers: []),
            UNNotificationCategory(identifier: Category.finalWalk, actions: [navigate, show, skip], intentIdentifiers: []),
            UNNotificationCategory(identifier: Category.info, actions: [show], intentIdentifiers: []),
        ])
    }

    // MARK: JourneyNotifier

    func show(journey: TrackedJourney, alert: any JourneyAlert) {
        let id = Self.identifier(journey, alert)
        // Scheduled with the system for a time now passed: the system shows it, or already has, and
        // the user may have opened or cleared it since. Either way it is not posted again.
        if let at = scheduled[id], at <= Int64(Date().timeIntervalSince1970 * 1000) { return }
        let content = content(for: alert)
        center.getDeliveredNotifications { [center] delivered in
            // Delivered by the system while the app was suspended: already seen.
            guard !delivered.contains(where: { $0.request.identifier == id }) else { return }
            center.removePendingNotificationRequests(withIdentifiers: [id])
            center.add(UNNotificationRequest(identifier: id, content: content, trigger: nil))
        }
    }

    func updateProgress(progress: JourneyProgress) {
        if #available(iOS 16.2, *) {
            JourneyLiveActivity.shared.update(progress: progress, clock: clock)
        }
    }

    func scheduleUpcoming(journey: TrackedJourney, upcoming: [ScheduledAlert]) {
        let wanted = Dictionary(upcoming.map { (Self.identifier(journey, $0.alert), $0.atMillis) }, uniquingKeysWith: { first, _ in first })
        guard wanted != scheduled else { return }
        // Those gone or moved by a delay; the rest stay scheduled as they are.
        let changed = scheduled.filter { wanted[$0.key] != $0.value }.map(\.key)
        center.removePendingNotificationRequests(withIdentifiers: changed)
        let now = Date().timeIntervalSince1970
        for item in upcoming {
            let id = Self.identifier(journey, item.alert)
            guard scheduled[id] != item.atMillis else { continue }
            let seconds = max(1, Double(item.atMillis) / 1000 - now)
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: seconds, repeats: false)
            center.add(UNNotificationRequest(identifier: id, content: content(for: item.alert), trigger: trigger))
        }
        scheduled = wanted
    }

    func end(journeyId _: String) {
        center.getPendingNotificationRequests { [center] requests in
            center.removePendingNotificationRequests(
                withIdentifiers: requests.map(\.identifier).filter { $0.hasPrefix(Self.prefix) }
            )
        }
        scheduled = [:]
        if #available(iOS 16.2, *) {
            JourneyLiveActivity.shared.end()
        }
    }

    /// One per alert of one followed journey: following the same trip again later is a new journey,
    /// whose alerts must not be taken for the ones already delivered.
    private static func identifier(_ journey: TrackedJourney, _ alert: any JourneyAlert) -> String {
        "\(prefix)\(journey.startedAtMillis).\(alert.id)"
    }

    // MARK: Content

    private func content(for alert: any JourneyAlert) -> UNMutableNotificationContent {
        let content = UNMutableNotificationContent()
        content.sound = .default
        content.categoryIdentifier = Category.alert
        switch onEnum(of: alert) {
        case let .departingSoon(departing):
            // Clock times, not "in 9 minutes": a delivered notification is never updated.
            content.title = L("journey_alert_departing", departing.lineName, clockTime(departing.departsAtMillis))
            content.body = [
                departing.stopName.map { L("journey_alert_from", $0) },
                departing.platform.map { L("journey_alert_platform", $0) },
                departing.destination.map { L("journey_alert_towards", $0) },
            ].compactMap { $0 }.joined(separator: " · ")
        case let .nextStopIsYours(next):
            content.title = L("journey_alert_next_stop", next.stopName)
            content.body = L("journey_alert_next_stop_detail")
        case let .transfer(transfer):
            content.title = L("journey_alert_transfer", transfer.lineName)
            content.body = [
                L("journey_alert_leaves_at", clockTime(transfer.departsAtMillis)),
                transfer.platform.map { L("journey_alert_platform", $0) },
            ].compactMap { $0 }.joined(separator: " · ")
        case let .walkTo(walk):
            content.title = L("journey_alert_walk", walk.name)
            content.body = L("journey_alert_walk_detail", Int(walk.minutes))
            if walk.canNavigate, let latitude = walk.latitude, let longitude = walk.longitude {
                content.categoryIdentifier = Category.finalWalk
                content.userInfo = ["latitude": latitude.doubleValue, "longitude": longitude.doubleValue]
            }
        case .missedJourney:
            content.title = L("journey_alert_missed")
            content.body = L("journey_alert_missed_detail")
            content.categoryIdentifier = Category.info
        case let .arrived(arrived):
            content.title = L("journey_alert_arrived", arrived.destinationName)
            content.categoryIdentifier = Category.info
        }
        return content
    }

    private func clockTime(_ epochMillis: Int64) -> String {
        clock.clockText(epochMillis: epochMillis).time
    }

    // MARK: UNUserNotificationCenterDelegate

    /// Alerts show as banners while the app is open too: the user may be looking at another screen.
    func userNotificationCenter(
        _: UNUserNotificationCenter,
        willPresent _: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(
        _: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let info = response.notification.request.content.userInfo
        DispatchQueue.main.async {
            switch response.actionIdentifier {
            case Action.skip:
                PulseTransitIos.shared.skipJourneyAlerts()
            case Action.navigate:
                if let latitude = info["latitude"] as? Double, let longitude = info["longitude"] as? Double,
                   let url = URL(string: "https://maps.apple.com/?daddr=\(latitude),\(longitude)&dirflg=w") {
                    UIApplication.shared.open(url)
                }
            case UNNotificationDismissActionIdentifier:
                break
            default:
                // "Show journey", or a tap on the notification itself.
                PulseTransitIos.shared.requestOpenJourney()
            }
            completionHandler()
        }
    }
}
