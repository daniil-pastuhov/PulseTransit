import CoreLocation
import Foundation
import Shared
import UIKit

/// Device location through CoreLocation, one-shot or continuous, asking for when-in-use permission
/// the first time.
///
/// With `backgroundUpdates`, continuous updates go on while the app is in the background (journey
/// alerts): iOS allows that on a when-in-use grant for updates started in the foreground, and shows
/// the blue location indicator meanwhile. The app declares the `location` background mode for it.
final class CoreLocationSource: NSObject, LocationSource, CLLocationManagerDelegate {
    /// How long to wait for a fix before reporting none, like the fused provider's own timeout.
    private static let timeout: TimeInterval = 15
    /// How long to wait for an answer to the permission prompt before giving up, so a prompt that
    /// never appeared can't leave the screen saying "Requesting…" forever.
    private static let authorizationTimeout: TimeInterval = 60

    private let manager = CLLocationManager()
    private var pending: ((any LocationResult) -> Void)?
    private var timeoutWork: DispatchWorkItem?
    /// Receives every fix while continuous updates run (on-spot mode).
    private var updates: ((any LocationResult) -> Void)?

    private let backgroundUpdates: Bool

    init(backgroundUpdates: Bool = false) {
        self.backgroundUpdates = backgroundUpdates
        super.init()
        manager.delegate = self
        if backgroundUpdates {
            manager.activityType = .otherNavigation
            manager.pausesLocationUpdatesAutomatically = false
        }
        // iOS shows one system prompt at a time. A permission request made while another prompt is
        // up (the camera's, say) is dropped without an answer, so ask again once the app is active,
        // which is when that other prompt has closed.
        NotificationCenter.default.addObserver(
            self, selector: #selector(appDidBecomeActive), name: UIApplication.didBecomeActiveNotification, object: nil
        )
    }

    @objc private func appDidBecomeActive() {
        guard pending != nil || updates != nil, manager.authorizationStatus == .notDetermined else { return }
        manager.requestWhenInUseAuthorization()
    }

    func requestLocation(accuracy: LocationAccuracy, onResult: @escaping (any LocationResult) -> Void) {
        DispatchQueue.main.async { [self] in
            finish(nil) // a new request replaces any still in flight
            pending = onResult
            manager.desiredAccuracy = accuracy == .precise ? kCLLocationAccuracyBest : kCLLocationAccuracyHundredMeters
            proceed()
        }
    }

    func cancel() {
        DispatchQueue.main.async { [self] in
            pending = nil
            timeoutWork?.cancel()
            if updates == nil { manager.stopUpdatingLocation() }
        }
    }

    func startUpdates(
        accuracy: LocationAccuracy, distanceFilterMeters: Double, onResult: @escaping (any LocationResult) -> Void
    ) {
        DispatchQueue.main.async { [self] in
            updates = onResult
            manager.desiredAccuracy = accuracy == .precise ? kCLLocationAccuracyBest : kCLLocationAccuracyHundredMeters
            manager.distanceFilter = distanceFilterMeters
            if backgroundUpdates {
                manager.allowsBackgroundLocationUpdates = true
                manager.showsBackgroundLocationIndicator = true
            }
            proceedUpdates()
        }
    }

    func stopUpdates() {
        DispatchQueue.main.async { [self] in
            updates = nil
            manager.distanceFilter = kCLDistanceFilterNone
            manager.stopUpdatingLocation()
            if backgroundUpdates { manager.allowsBackgroundLocationUpdates = false }
        }
    }

    private func proceedUpdates() {
        switch manager.authorizationStatus {
        case .notDetermined:
            manager.requestWhenInUseAuthorization() // continues in locationManagerDidChangeAuthorization
        case .denied, .restricted:
            let callback = updates
            updates = nil
            callback?(LocationResultPermissionDenied.shared)
        default:
            manager.startUpdatingLocation()
        }
    }

    private func proceed() {
        switch manager.authorizationStatus {
        case .notDetermined:
            startTimeout(Self.authorizationTimeout)
            manager.requestWhenInUseAuthorization() // continues in locationManagerDidChangeAuthorization
        case .denied, .restricted:
            finish(LocationResultPermissionDenied.shared)
        default:
            // The fix timeout starts only now, so time spent on the permission prompt does not count
            // against it, just as Android asks for permission before requesting a location.
            startTimeout(Self.timeout)
            manager.requestLocation()
        }
    }

    /// Reports "no location" after `seconds` unless the request finishes first. Replaces any timeout
    /// already running.
    private func startTimeout(_ seconds: TimeInterval) {
        let work = DispatchWorkItem { [weak self] in self?.finish(LocationResultUnavailable.shared) }
        timeoutWork?.cancel()
        timeoutWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + seconds, execute: work)
    }

    private func finish(_ result: (any LocationResult)?) {
        timeoutWork?.cancel()
        timeoutWork = nil
        guard let callback = pending else { return }
        pending = nil
        if let result { callback(result) }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard manager.authorizationStatus != .notDetermined else { return }
        if pending != nil { proceed() }
        if updates != nil { proceedUpdates() }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        let fix = LocationResultFix(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude)
        updates?(fix)
        finish(fix)
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        let denied = (error as? CLError)?.code == .denied
        let result: any LocationResult = denied ? LocationResultPermissionDenied.shared : LocationResultUnavailable.shared
        if let callback = updates {
            // A transient "location unknown" keeps updates running; a denial ends them.
            if denied { updates = nil }
            callback(result)
        }
        finish(result)
    }
}
