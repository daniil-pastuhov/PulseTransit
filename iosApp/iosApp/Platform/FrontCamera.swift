import AVFoundation
import Shared
import UIKit

/// The front camera, used for motion wake and, while it runs, as a light meter.
///
/// It delivers luminance samples in the format `CameraFrameSource` documents; the motion decision
/// is the shared `MotionFrameDiff`. All session work happens on one serial queue.
final class FrontCamera: NSObject, CameraFrameSource, AVCaptureVideoDataOutputSampleBufferDelegate {
    static let shared = FrontCamera()

    private let queue = DispatchQueue(label: "PulseTransit.FrontCamera")
    private let session = AVCaptureSession()
    private var device: AVCaptureDevice?
    private var configured = false
    private var onFrame: ((KotlinByteArray) -> Void)?
    private var lastFrameTime: CFTimeInterval = 0
    /// Written on `queue` with each analysed frame, read from the main thread, hence the lock.
    private let luxLock = NSLock()
    private var latestLux: Double?

    private static let subsample = Int(MotionFrameDiff.companion.SUBSAMPLE)
    /// The area analysed, Android's analysis size, so a sensitivity level means the same on both.
    private static let analysisWidth = 320
    private static let analysisHeight = 240
    private static let minFrameInterval = CFTimeInterval(MotionFrameDiff.companion.MIN_FRAME_INTERVAL_MILLIS) / 1000

    func start(onFrame: @escaping (KotlinByteArray) -> Void) {
        queue.async { [self] in
            guard configure() else { return } // no front camera, e.g. in the simulator
            self.onFrame = onFrame
            lastFrameTime = 0
            if !session.isRunning { session.startRunning() }
        }
    }

    func stop() {
        queue.async { [self] in
            onFrame = nil
            if session.isRunning { session.stopRunning() }
            setLatestLux(nil)
        }
    }

    /// Scene brightness in lux estimated from the auto-exposure settings, or nil when the camera is
    /// not running. iOS has no public ambient light sensor; this is the closest substitute.
    /// Updated with each analysed frame, so reading it never waits on the camera queue.
    var estimatedLux: Double? {
        luxLock.lock()
        defer { luxLock.unlock() }
        return latestLux
    }

    private func setLatestLux(_ lux: Double?) {
        luxLock.lock()
        latestLux = lux
        luxLock.unlock()
    }

    /// Exposure value at ISO 100 from the auto-exposure settings, then the usual incident-light
    /// conversion (lux ≈ 2.5 · 2^EV). Must run on `queue`.
    private func exposureLux() -> Double? {
        guard let device else { return nil }
        let seconds = CMTimeGetSeconds(device.exposureDuration)
        let aperture = Double(device.lensAperture)
        let iso = Double(device.iso)
        guard seconds > 0, iso > 0, aperture > 0 else { return nil }
        let ev100 = log2(aperture * aperture / seconds) - log2(iso / 100)
        return 2.5 * pow(2, ev100)
    }

    /// Sets up the session once. Must run on `queue`.
    private func configure() -> Bool {
        if configured { return device != nil }
        configured = true
        guard let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front),
              let input = try? AVCaptureDeviceInput(device: camera) else { return false }

        session.beginConfiguration()
        // The smallest preset that covers the 320x240 analysis area.
        session.sessionPreset = session.canSetSessionPreset(.cif352x288) ? .cif352x288 : .low
        if session.canAddInput(input) { session.addInput(input) }
        let output = AVCaptureVideoDataOutput()
        output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_420YpCbCr8BiPlanarFullRange]
        output.alwaysDiscardsLateVideoFrames = true
        output.setSampleBufferDelegate(self, queue: queue)
        if session.canAddOutput(output) { session.addOutput(output) }
        session.commitConfiguration()
        device = camera
        return true
    }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard let onFrame else { return }
        let now = CACurrentMediaTime()
        guard now - lastFrameTime >= Self.minFrameInterval,
              let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        lastFrameTime = now
        setLatestLux(exposureLux())
        onFrame(Self.sampleLuminance(pixelBuffer))
    }

    /// Every `subsample`th pixel of the Y plane on both axes, row by row, within the centred
    /// analysis area.
    private static func sampleLuminance(_ pixelBuffer: CVPixelBuffer) -> KotlinByteArray {
        CVPixelBufferLockBaseAddress(pixelBuffer, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, .readOnly) }
        let planeWidth = CVPixelBufferGetWidthOfPlane(pixelBuffer, 0)
        let planeHeight = CVPixelBufferGetHeightOfPlane(pixelBuffer, 0)
        let areaWidth = min(planeWidth, analysisWidth)
        let areaHeight = min(planeHeight, analysisHeight)
        let originX = (planeWidth - areaWidth) / 2
        let originY = (planeHeight - areaHeight) / 2
        let width = areaWidth / subsample
        let height = areaHeight / subsample
        let rowStride = CVPixelBufferGetBytesPerRowOfPlane(pixelBuffer, 0)
        let samples = KotlinByteArray(size: Int32(width * height))
        guard let base = CVPixelBufferGetBaseAddressOfPlane(pixelBuffer, 0)?.assumingMemoryBound(to: Int8.self) else {
            return samples
        }
        var index: Int32 = 0
        for y in 0 ..< height {
            let row = base + (originY + y * subsample) * rowStride + originX
            for x in 0 ..< width {
                samples.set(index: index, value: row[x * subsample])
                index += 1
            }
        }
        return samples
    }
}
