import Foundation
import WatchConnectivity

/// The journey followed on the iPhone, as this watch knows it. The iPhone keeps it up to date
/// through the session's application context, which the system delivers when it can and keeps
/// between launches, so the journey is here when the app opens, with or without the phone in
/// reach. The watch walks through the plan by its own clock.
///
/// The alerts stay with the iPhone: watchOS suspends this app when the wrist drops, and brings the
/// iPhone's notifications to the wrist by itself.
final class WatchJourney: NSObject, ObservableObject, WCSessionDelegate {
    @Published private(set) var plan: JourneyPlan?
    /// "Stop following" could not reach the iPhone at once and was left for it to pick up.
    @Published private(set) var stopQueued = false

    func activate() {
        #if DEBUG
        // -PTDemo YES: a made-up journey starting now, to look at the screens in the simulator,
        // which has no iPhone to send one.
        if UserDefaults.standard.bool(forKey: "PTDemo") {
            plan = JourneyPlan(message: Self.demoPlan(now: Int(Date().timeIntervalSince1970)))
            return
        }
        #endif
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    /// Asks the iPhone to stop following, which wakes PulseTransit there when it is not running.
    /// Out of reach, the request is queued for when the iPhone is back.
    func stopFollowing() {
        guard let plan else { return }
        let message: [String: Any] = ["t": "stop", "id": plan.id]
        let session = WCSession.default
        if session.isReachable {
            session.sendMessage(message, replyHandler: { [weak self] _ in
                DispatchQueue.main.async { self?.plan = nil }
            }, errorHandler: { [weak self] _ in
                DispatchQueue.main.async { self?.queueStop(message) }
            })
        } else {
            queueStop(message)
        }
    }

    private func queueStop(_ message: [String: Any]) {
        WCSession.default.transferUserInfo(message)
        stopQueued = true
    }

    /// The iPhone's word on the journey: a plan, its end, or that there is none.
    private func apply(_ context: [String: Any]) {
        DispatchQueue.main.async { [self] in
            switch context["t"] as? String {
            case "plan":
                guard let new = JourneyPlan(message: context) else { return }
                if new.id != plan?.id { stopQueued = false }
                plan = new
            case "end":
                guard context["id"] as? String == plan?.id else { return }
                // A journey that arrived stays to say so; one that was stopped goes.
                if context["arrived"] as? Int != 1 { plan = nil }
                stopQueued = false
            case "none":
                plan = nil
                stopQueued = false
            default:
                break
            }
        }
    }

    #if DEBUG
    /// The Garmin demo's journey (garminApp/source/Demo.mc): a walk, metro 5, bus 20 and the walk home.
    private static func demoPlan(now: Int) -> [String: Any] {
        let m = 60
        return [
            "t": "plan", "v": 1, "id": "demo", "title": "Journey to Home", "arr": now + 29 * m,
            "f": [
                [now, 2, "Walking to Jernbanetorget", "Then 5 at 09:06, platform 2 · Arrives 09:31", now + 4 * m],
                [now + 2 * m, 0, "5 towards Vestli", "Leaves 09:06, 2 min late · Platform 2 · From Jernbanetorget · Arrives 09:31", now + 4 * m],
                [now + 4 * m, 1, "On 5 to Majorstuen", "Next stop: Stortinget · 3 stops left · Then 20 at 09:18, platform B", now + 12 * m],
                [now + 12 * m, 0, "20 towards Skøyen", "Leaves 09:18 · Platform B · Arrives 09:31", now + 16 * m],
                [now + 16 * m, 1, "On 20 to Skøyen", "2 stops left · Arrives 09:31", now + 24 * m],
                [now + 24 * m, 2, "Walking to Home", "Arrives 09:31", now + 29 * m],
                [now + 29 * m, 3, "Arrived at Home", "Arrived 09:31", 0],
            ],
            "s": [[0, now - 2 * m, now + 2 * m], [1, now + 2 * m, now + 4 * m], [2, now + 4 * m, now + 12 * m],
                  [1, now + 12 * m, now + 16 * m], [2, now + 16 * m, now + 24 * m], [0, now + 24 * m, now + 29 * m]],
            "a": [],
        ]
    }
    #endif

    // MARK: WCSessionDelegate

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        // What the iPhone last said, kept by the system while this app was not running.
        apply(session.receivedApplicationContext)
    }

    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        apply(applicationContext)
    }
}
