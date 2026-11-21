import Foundation

/// The followed journey as the iPhone sent it: a "plan" message of the format the shared code's
/// WatchMessages (shared/.../data/journey/watch/WatchMessages.kt) writes, the same one the Garmin
/// watch app reads (garminApp/source/JourneyPlan.mc). Times are seconds since 1970; texts come
/// worded, in the phone's language.
struct JourneyPlan: Equatable {
    /// What to show from when: `[from, phase, headline, detail, nextAt]`.
    struct Frame: Equatable {
        let from: Int
        let phase: Int
        let headline: String
        let detail: String
        /// The moment counted down to; 0 when there is none.
        let nextAt: Int

        var hasArrived: Bool { phase == JourneyPlan.phaseArrived }
    }

    /// A walk, wait or ride on the journey's bar: `[kind, start, end]`.
    struct Segment: Equatable {
        let isRide: Bool
        let start: Int
        let end: Int
    }

    /// The message format this app reads; the phone's WatchMessages.VERSION.
    static let version = 1
    static let phaseArrived = 3
    private static let segmentRide = 2
    /// A journey this long past its arrival is no longer shown.
    private static let lingerSeconds = 600

    let id: String
    let title: String
    /// When the journey arrives; 0 when not known.
    let arrival: Int
    let frames: [Frame]
    let segments: [Segment]

    /// Nil for a message that is not a plan this version can read.
    init?(message: [String: Any]) {
        guard message["t"] as? String == "plan",
              (message["v"] as? Int ?? 0) <= Self.version,
              let id = message["id"] as? String else { return nil }
        self.id = id
        title = message["title"] as? String ?? ""
        arrival = message["arr"] as? Int ?? 0
        frames = (message["f"] as? [[Any]] ?? []).compactMap { row in
            guard row.count >= 5, let from = row[0] as? Int, let phase = row[1] as? Int,
                  let headline = row[2] as? String, let detail = row[3] as? String, let nextAt = row[4] as? Int else { return nil }
            return Frame(from: from, phase: phase, headline: headline, detail: detail, nextAt: nextAt)
        }
        segments = (message["s"] as? [[Any]] ?? []).compactMap { row in
            guard row.count >= 3, let kind = row[0] as? Int, let start = row[1] as? Int, let end = row[2] as? Int else { return nil }
            return Segment(isRide: kind == Self.segmentRide, start: start, end: end)
        }
    }

    /// The index of the frame to show at `now`: the last that has started, the first before any has.
    func frameIndex(at now: Int) -> Int {
        var current = 0
        for (index, frame) in frames.enumerated().dropFirst() {
            if frame.from > now { break }
            current = index
        }
        return current
    }

    /// The frame to show at `now`; nil for a plan without frames.
    func frame(at now: Int) -> Frame? {
        frames.isEmpty ? nil : frames[frameIndex(at: now)]
    }

    /// The frames after the one shown at `now`.
    func frames(after now: Int) -> [Frame] {
        Array(frames.dropFirst(frameIndex(at: now) + 1))
    }

    /// Whether the journey ended long enough ago that it is no longer worth showing.
    func isOver(at now: Int) -> Bool {
        let end = arrival > 0 ? arrival : (frames.last?.from ?? 0)
        return now > end + Self.lingerSeconds
    }

    /// How far along the journey is at `now`, from 0 to 1, within one segment.
    static func progress(of segment: Segment, at now: Int) -> Double {
        guard segment.end > segment.start else { return now >= segment.end ? 1 : 0 }
        return min(max(Double(now - segment.start) / Double(segment.end - segment.start), 0), 1)
    }
}
