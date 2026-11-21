import ConnectIQ
import Foundation
import Shared
import UIKit

/// The Garmin watch the user chose in Settings, and the PulseTransit watch app on it (`garminApp/`),
/// through Garmin's Connect IQ SDK.
///
/// Unlike Android, the SDK here only learns of a watch through a round trip: `choose()` opens
/// Garmin Connect, the user picks a watch, and Garmin Connect opens PulseTransit again with it in a
/// URL (`handle(url:)`). The watch is saved, and from then on the SDK talks to it over Bluetooth
/// itself. The SDK is not started until a watch has been chosen: starting it asks for Bluetooth,
/// which a rider without a Garmin watch must never be asked for.
final class GarminWatch: NSObject, ObservableObject, WatchSource, IQDeviceEventDelegate, IQAppMessageDelegate {
    static let shared = GarminWatch()

    /// The URL scheme Garmin Connect answers on (CFBundleURLTypes in iosApp-Info.plist).
    static let urlScheme = "pulsetransit-ciq"

    /// The watch app's id: `garminApp/manifest.xml`, also `GarminWatchLink.APP_ID` on Android.
    private static let appId = UUID(uuidString: "4748155F-3AA5-4026-85A2-B17D28EBBFE4")!
    private static let savedKey = "garminWatch.device"

    enum Status: Equatable {
        /// No watch has been chosen.
        case none
        case notConnected(name: String)
        /// Connected, without the PulseTransit watch app.
        case appMissing(name: String)
        case ready(name: String)
    }

    /// What Settings shows.
    @Published private(set) var status: Status = .none

    private var device: IQDevice?
    private var connected = false
    /// Whether the watch app is installed; nil while that is not known.
    private var installed: Bool?
    private var sdkStarted = false
    private var onAvailability: ((WatchAvailability) -> Void)?
    private var onMessage: (([String: Any]) -> Void)?

    /// The app's id in the Connect IQ Store, once it is listed (`GarminWatchApp.STORE_ID`).
    private static let storeId = UUID(uuidString: GarminWatchApp.shared.STORE_ID)

    private var app: IQApp? {
        device.map { IQApp(uuid: Self.appId, store: Self.storeId ?? Self.appId, device: $0) }
    }

    /// Whether Garmin Connect is on this iPhone (its URL scheme is in LSApplicationQueriesSchemes).
    /// Without it there is no watch to choose, so Settings offers none.
    var isGarminConnectInstalled: Bool {
        URL(string: "gcm-ciq://").map(UIApplication.shared.canOpenURL) ?? false
    }

    /// Whether "Get the watch app" has a store page to open.
    var canOpenStore: Bool { Self.storeId != nil }

    /// Call at launch: takes up the watch chosen before, if any.
    func activate() {
        guard let saved = UserDefaults.standard.dictionary(forKey: Self.savedKey) as? [String: String],
              let id = saved["id"].flatMap(UUID.init(uuidString:)) else { return }
        use(IQDevice(id: id, modelName: saved["model"] ?? "", friendlyName: saved["name"] ?? ""))
    }

    /// "Connect Garmin watch": Garmin Connect lets the user pick one and answers through `handle(url:)`.
    func choose() {
        startSdk()
        ConnectIQ.sharedInstance()?.showDeviceSelection()
    }

    /// Garmin Connect's answer to `choose()`. False for a URL that is not one.
    @discardableResult
    func handle(url: URL) -> Bool {
        guard url.scheme == Self.urlScheme else { return false }
        startSdk()
        let chosen = ConnectIQ.sharedInstance()?.parseDeviceSelectionResponse(from: url) as? [IQDevice]
        guard let device = chosen?.first else { return true }
        UserDefaults.standard.set(
            ["id": device.uuid.uuidString, "model": device.modelName ?? "", "name": device.friendlyName ?? ""],
            forKey: Self.savedKey
        )
        use(device)
        return true
    }

    /// "Remove watch": journeys no longer go to it.
    func remove() {
        if sdkStarted {
            ConnectIQ.sharedInstance()?.unregister(forAllDeviceEvents: self)
            ConnectIQ.sharedInstance()?.unregister(forAllAppMessages: self)
        }
        UserDefaults.standard.removeObject(forKey: Self.savedKey)
        device = nil
        connected = false
        installed = nil
        publish()
    }

    private func startSdk() {
        guard !sdkStarted else { return }
        sdkStarted = true
        ConnectIQ.sharedInstance()?.initialize(withUrlScheme: Self.urlScheme, uiOverrideDelegate: nil)
    }

    private func use(_ device: IQDevice) {
        startSdk()
        let connectIq = ConnectIQ.sharedInstance()
        connectIq?.unregister(forAllDeviceEvents: self)
        connectIq?.unregister(forAllAppMessages: self)
        self.device = device
        connected = false
        installed = nil
        connectIq?.register(forDeviceEvents: device, delegate: self)
        if let status = connectIq?.getDeviceStatus(device) { deviceStatusChanged(device, status: status) }
        publish()
    }

    private func publish() {
        let name = device.map { $0.friendlyName?.isEmpty == false ? $0.friendlyName! : ($0.modelName ?? "Garmin") }
        let availability: WatchAvailability
        if let name {
            if !connected {
                status = .notConnected(name: name)
                availability = .none
            } else if installed == true {
                status = .ready(name: name)
                availability = .ready
            } else if installed == false {
                status = .appMissing(name: name)
                availability = .appMissing
            } else {
                status = .notConnected(name: name)
                availability = .none
            }
        } else {
            status = .none
            availability = .none
        }
        onAvailability?(availability)
    }

    // MARK: IQDeviceEventDelegate

    func deviceStatusChanged(_ device: IQDevice!, status: IQDeviceStatus) {
        DispatchQueue.main.async { [self] in
            guard let current = self.device, current.uuid == device?.uuid else { return }
            let nowConnected = status == .connected
            guard nowConnected != connected || (nowConnected && installed == nil) else { return }
            connected = nowConnected
            installed = nil
            publish()
            guard nowConnected, let app else { return }
            ConnectIQ.sharedInstance()?.getAppStatus(app) { [weak self] appStatus in
                DispatchQueue.main.async {
                    guard let self, self.connected, self.device?.uuid == current.uuid else { return }
                    self.installed = appStatus?.isInstalled ?? false
                    if self.installed == true {
                        ConnectIQ.sharedInstance()?.register(forAppMessages: app, delegate: self)
                    }
                    self.publish()
                }
            }
        }
    }

    // MARK: IQAppMessageDelegate

    func receivedMessage(_ message: Any!, from app: IQApp!) {
        // The watch app sends dictionaries; the SDK may hand them over in a list.
        let messages = (message as? [Any]) ?? [message as Any]
        DispatchQueue.main.async { [self] in
            for case let dictionary as [String: Any] in messages { onMessage?(dictionary) }
        }
    }

    // MARK: WatchSource

    func start(onAvailability: @escaping (WatchAvailability) -> Void, onMessage: @escaping ([String: Any]) -> Void) {
        self.onAvailability = onAvailability
        self.onMessage = onMessage
        publish()
    }

    func openApp() {
        guard installed == true, let app else { return }
        ConnectIQ.sharedInstance()?.openAppRequest(app) { _ in }
    }

    /// The watch app's page in Garmin Connect's Connect IQ Store, which installs it on the watch.
    func openStore() {
        guard canOpenStore, let app else { return }
        ConnectIQ.sharedInstance()?.showStore(for: app)
    }

    func send(message: [String: Any], onResult: @escaping (KotlinBoolean) -> Void) {
        guard connected, installed == true, let app else {
            onResult(false)
            return
        }
        ConnectIQ.sharedInstance()?.sendMessage(Self.plain(message), to: app, progress: nil) { result in
            DispatchQueue.main.async { onResult(KotlinBoolean(bool: result == .success)) }
        }
    }

    /// The message as plain Foundation values, which is what the SDK serializes: Kotlin's
    /// collections and boxed numbers arrive as its own subclasses of them.
    private static func plain(_ value: Any) -> Any {
        switch value {
        case let dictionary as [String: Any]: return dictionary.mapValues(plain) as NSDictionary
        case let list as [Any]: return list.map(plain) as NSArray
        case let text as String: return text as NSString
        case let number as NSNumber: return NSNumber(value: number.int32Value)
        default: return value
        }
    }
}

/// Words a journey for the watch as the notifications and the Live Activity word it.
final class JourneyWatchWording: WatchWordingSource {
    private let clock: ClockFormatter

    init(clock: ClockFormatter) {
        self.clock = clock
    }

    func title(progress: JourneyProgress) -> String {
        L("journey_progress_title", progress.destinationName)
    }

    func progress(progress: JourneyProgress) -> WatchText {
        WatchText(
            title: JourneyProgressText.headline(progress),
            text: progress.details.map { JourneyProgressText.detail($0, clock: clock) }.joined(separator: " · ")
        )
    }

    func alert(alert: any JourneyAlert) -> WatchText {
        let content = JourneyNotifications.shared.content(for: alert)
        return WatchText(title: content.title, text: content.body.isEmpty ? nil : content.body)
    }
}
