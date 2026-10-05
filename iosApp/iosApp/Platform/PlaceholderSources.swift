import Foundation
import Shared

// Stand-ins until the real CoreLocation, light and AVFoundation sources exist. Each one behaves
// like a device without that capability, which the shared code already handles.

final class UnavailableLocationSource: NSObject, LocationSource {
    func requestLocation(accuracy: LocationAccuracy, onResult: @escaping (any LocationResult) -> Void) {
        onResult(LocationResultUnavailable.shared)
    }

    func cancel() {}
}

final class SilentLightSource: NSObject, LightSource {
    func start(onLux: @escaping (KotlinDouble) -> Void) {}

    func stop() {}
}

final class SilentMotionSource: NSObject, MotionSource {
    func start(onMotion: @escaping () -> Void) {}

    func stop() {}
}
