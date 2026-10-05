import Foundation
import Shared
import UIKit

/// Ambient light for the AUTO theme. iOS has no public lux sensor, so this estimates it: from the
/// front camera's exposure while motion wake has the camera running, otherwise from the screen
/// brightness, which follows ambient light when the user has auto-brightness on.
final class EstimatedLightSource: NSObject, LightSource {
    /// How often a new estimate is taken; the theme's hysteresis does not need it any faster.
    private static let interval: TimeInterval = 2

    private var timer: Timer?

    func start(onLux: @escaping (KotlinDouble) -> Void) {
        DispatchQueue.main.async { [self] in
            timer?.invalidate()
            let emit = { onLux(KotlinDouble(value: Self.estimate())) }
            emit()
            timer = Timer.scheduledTimer(withTimeInterval: Self.interval, repeats: true) { _ in emit() }
        }
    }

    func stop() {
        DispatchQueue.main.async { [self] in
            timer?.invalidate()
            timer = nil
        }
    }

    private static func estimate() -> Double {
        if let lux = FrontCamera.shared.estimatedLux { return lux }
        // Brightness 0…1 mapped onto roughly 5…2000 lux on a log scale, so the default thresholds
        // (dark below 10, light above 100) fall where auto-brightness dims and brightens.
        return 5 * pow(10, Double(UIScreen.main.brightness) * 2.6)
    }
}
