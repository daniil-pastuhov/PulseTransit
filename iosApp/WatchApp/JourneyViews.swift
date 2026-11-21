import SwiftUI

/// Fjord's dark theme (docs/design/tokens.json), as the Garmin app has it: blue.300 for rides.
private enum WatchPalette {
    static let ride = Color(red: 0x7F / 255, green: 0xB0 / 255, blue: 0xFF / 255)
    static let other = Color(red: 0xAE / 255, green: 0xB7 / 255, blue: 0xC4 / 255)
}

/// Seconds since 1970, as the plan counts.
private func seconds(_ date: Date) -> Int {
    Int(date.timeIntervalSince1970)
}

struct RootView: View {
    @EnvironmentObject private var journey: WatchJourney

    var body: some View {
        // Re-read every few seconds: the plan is walked through by the clock, not by updates.
        TimelineView(.periodic(from: .now, by: 5)) { context in
            let now = seconds(context.date)
            if let plan = journey.plan, !plan.isOver(at: now), let frame = plan.frame(at: now) {
                JourneyPages(plan: plan, frame: frame, now: now)
            } else {
                NoJourneyView()
            }
        }
    }
}

/// The journey now, what comes next, and "Stop following", one under the other.
private struct JourneyPages: View {
    let plan: JourneyPlan
    let frame: JourneyPlan.Frame
    let now: Int

    /// The page shown; debug builds start on `-PTPage 1` or `2`, to look at the others in the simulator.
    @State private var page = UserDefaults.standard.integer(forKey: "PTPage")

    var body: some View {
        TabView(selection: $page) {
            NowView(plan: plan, frame: frame, now: now).tag(0)
            ComingUpView(plan: plan, now: now).tag(1)
            StopView().tag(2)
        }
        .tabViewStyle(.verticalPage)
    }
}

/// What the iPhone's Live Activity says: the minutes to the step's next moment, what to do, its
/// facts, and the journey as a bar.
private struct NowView: View {
    let plan: JourneyPlan
    let frame: JourneyPlan.Frame
    let now: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(plan.title)
                .font(.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(1)
            Countdown(frame: frame, now: now)
            Text(frame.headline)
                .font(.headline)
                .lineLimit(2)
                .minimumScaleFactor(0.8)
            Text(frame.detail)
                .font(.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(3)
            Spacer(minLength: 2)
            JourneyBar(segments: plan.segments, now: now)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 4)
    }
}

/// Whole minutes to the frame's next moment, rounded up as the phone does, so "1 min" lasts until
/// the moment itself; "Now" once it has come; nothing for a frame with no moment.
private struct Countdown: View {
    let frame: JourneyPlan.Frame
    let now: Int

    var body: some View {
        if frame.nextAt > 0, !frame.hasArrived {
            let left = frame.nextAt - now
            if left <= 0 {
                Text("now").font(.title2.weight(.semibold))
            } else {
                let minutes = (left + 59) / 60
                HStack(alignment: .firstTextBaseline, spacing: 3) {
                    Text(minutes < 100 ? "\(minutes)" : "\(minutes / 60):" + String(format: "%02d", minutes % 60))
                        .font(.system(size: 40, weight: .semibold, design: .rounded).monospacedDigit())
                    if minutes < 100 {
                        Text("minutes").font(.caption).foregroundStyle(.secondary)
                    }
                }
                .accessibilityElement(children: .combine)
            }
        }
    }
}

/// The journey's walks, waits and rides in proportion to their time, rides in blue, each filled
/// as far as the journey has come.
private struct JourneyBar: View {
    let segments: [JourneyPlan.Segment]
    let now: Int

    var body: some View {
        if let first = segments.first, let last = segments.last, last.end > first.start {
            let total = Double(last.end - first.start)
            GeometryReader { proxy in
                let gaps = CGFloat(segments.count - 1) * 2
                HStack(spacing: 2) {
                    ForEach(Array(segments.enumerated()), id: \.offset) { _, segment in
                        let share = Double(max(segment.end - segment.start, 1)) / total
                        let color = segment.isRide ? WatchPalette.ride : WatchPalette.other
                        ZStack(alignment: .leading) {
                            Capsule().fill(color.opacity(0.4))
                            GeometryReader { bar in
                                Capsule().fill(color)
                                    .frame(width: bar.size.width * JourneyPlan.progress(of: segment, at: now))
                            }
                        }
                        .frame(width: max((proxy.size.width - gaps) * share, 3))
                    }
                }
            }
            .frame(height: 5)
            .accessibilityHidden(true)
        }
    }
}

/// The steps after the current one, with the times they start.
private struct ComingUpView: View {
    let plan: JourneyPlan
    let now: Int

    var body: some View {
        let frames = plan.frames(after: now)
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text("coming_up").font(.caption2).foregroundStyle(.secondary)
                if frames.isEmpty {
                    Text("nothing_more").font(.footnote)
                }
                ForEach(Array(frames.enumerated()), id: \.offset) { _, frame in
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text(Date(timeIntervalSince1970: TimeInterval(frame.from)), style: .time)
                            .font(.footnote.monospacedDigit())
                            .foregroundStyle(.secondary)
                        Text(frame.headline).font(.footnote)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 4)
        }
    }
}

private struct StopView: View {
    @EnvironmentObject private var journey: WatchJourney

    var body: some View {
        VStack(spacing: 8) {
            Button("stop_following", role: .destructive) { journey.stopFollowing() }
                .disabled(journey.stopQueued)
            if journey.stopQueued {
                Text("stop_sent_later").font(.caption2).foregroundStyle(.secondary).multilineTextAlignment(.center)
            }
        }
        .padding(.horizontal, 4)
    }
}

private struct NoJourneyView: View {
    var body: some View {
        VStack(spacing: 6) {
            Text("no_journey").font(.headline).multilineTextAlignment(.center)
            Text("no_journey_hint").font(.caption2).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }
        .padding(.horizontal, 4)
    }
}
