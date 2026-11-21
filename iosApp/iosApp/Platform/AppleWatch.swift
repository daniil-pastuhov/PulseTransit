import Foundation
import Shared
import WatchConnectivity

/// The paired Apple Watch and its PulseTransit app (`iosApp/WatchApp`), through WatchConnectivity.
///
/// The watch is kept up to date and takes nothing over. A watch app is suspended when the wrist
/// drops, so it cannot show alerts of its own; iOS brings this app's notifications to the wrist
/// instead. So the journey goes over as the session's application context: the system delivers the
/// latest one when the watch can take it, also to a watch app that is not running, and keeps
/// nothing but the latest. The watch app's "Stop following" comes back as a message, which wakes
/// this app when it is not running.
final class AppleWatch: NSObject, WatchMirrorSource, WCSessionDelegate {
    static let shared = AppleWatch()

    private var onMessage: (([String: Any]) -> Void)?
    /// What the watch app sent before the shared code listened: a stop that launched the app.
    private var early: [[String: Any]] = []
    /// The state to send once the session is active.
    private var pending: [String: Any]?

    /// Call at launch, so a message that launched the app has a session to arrive on.
    func activate() {
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    // MARK: WatchMirrorSource

    func start(onMessage: @escaping ([String: Any]) -> Void) {
        self.onMessage = onMessage
        early.forEach(onMessage)
        early = []
    }

    func update(message: [String: Any]) {
        pending = Self.plain(message) as? [String: Any]
        flush()
    }

    private func flush() {
        guard WCSession.isSupported(), let pending else { return }
        let session = WCSession.default
        // Without a watch, or without the app on it, there is nobody to tell; the state is kept
        // for when there is (the delegate hears of both).
        guard session.activationState == .activated, session.isPaired, session.isWatchAppInstalled else { return }
        do {
            try session.updateApplicationContext(pending)
            self.pending = nil
        } catch {
            // Kept, and tried again with the next state or change of the session.
        }
    }

    private func received(_ message: [String: Any]) {
        DispatchQueue.main.async { [self] in
            if let onMessage { onMessage(message) } else { early.append(message) }
        }
    }

    /// The message as property-list values, which is all a session carries: Kotlin's collections
    /// and boxed numbers arrive as its own subclasses of them.
    private static func plain(_ value: Any) -> Any {
        switch value {
        case let dictionary as [String: Any]: return dictionary.mapValues(plain)
        case let list as [Any]: return list.map(plain)
        case let text as String: return String(text)
        case let number as NSNumber: return Int(number.int32Value)
        default: return value
        }
    }

    // MARK: WCSessionDelegate

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        DispatchQueue.main.async { self.flush() }
    }

    func sessionWatchStateDidChange(_ session: WCSession) {
        DispatchQueue.main.async { self.flush() }
    }

    func sessionDidBecomeInactive(_ session: WCSession) {}

    /// Another watch was switched to: the session must be activated again for it.
    func sessionDidDeactivate(_ session: WCSession) {
        session.activate()
    }

    func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        received(message)
    }

    func session(_ session: WCSession, didReceiveMessage message: [String: Any], replyHandler: @escaping ([String: Any]) -> Void) {
        received(message)
        replyHandler([:])
    }

    /// The watch app's fallback when the phone could not be reached at once.
    func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any] = [:]) {
        received(userInfo)
    }
}
