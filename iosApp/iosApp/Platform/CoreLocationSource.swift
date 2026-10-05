import CoreLocation
import Foundation
import Shared

/// One-shot device location through CoreLocation, asking for when-in-use permission the first time.
final class CoreLocationSource: NSObject, LocationSource, CLLocationManagerDelegate {
    /// How long to wait for a fix before reporting none, like the fused provider's own timeout.
    private static let timeout: TimeInterval = 15

    private let manager = CLLocationManager()
    private var pending: ((any LocationResult) -> Void)?
    private var timeoutWork: DispatchWorkItem?

    override init() {
        super.init()
        manager.delegate = self
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
            manager.stopUpdatingLocation()
        }
    }

    private func proceed() {
        switch manager.authorizationStatus {
        case .notDetermined:
            manager.requestWhenInUseAuthorization() // continues in locationManagerDidChangeAuthorization
        case .denied, .restricted:
            finish(LocationResultPermissionDenied.shared)
        default:
            // The timeout starts only now, so time spent on the permission prompt does not count
            // against the fix, just as Android asks for permission before requesting a location.
            let work = DispatchWorkItem { [weak self] in self?.finish(LocationResultUnavailable.shared) }
            timeoutWork?.cancel()
            timeoutWork = work
            DispatchQueue.main.asyncAfter(deadline: .now() + Self.timeout, execute: work)
            manager.requestLocation()
        }
    }

    private func finish(_ result: (any LocationResult)?) {
        timeoutWork?.cancel()
        timeoutWork = nil
        guard let callback = pending else { return }
        pending = nil
        if let result { callback(result) }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard pending != nil, manager.authorizationStatus != .notDetermined else { return }
        proceed()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        finish(LocationResultFix(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude))
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        let denied = (error as? CLError)?.code == .denied
        finish(denied ? LocationResultPermissionDenied.shared : LocationResultUnavailable.shared)
    }
}
