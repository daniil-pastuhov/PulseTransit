import ActivityKit
import Shared
import SwiftUI
import WidgetKit

/// The JourneyActivity extension: the Live Activity of a followed journey, on the Lock Screen and
/// in the Dynamic Island (content from the app, `JourneyLiveActivity`), and the home-screen widgets
/// (`HomeWidgets.swift`), which load their own data through the shared code.
@main
struct JourneyActivityBundle: WidgetBundle {
    init() {
        registerPulseFonts()
        // Before any timeline is asked for, so the widgets never race to start Koin.
        PulseTransitWidgets.shared.start()
    }

    var body: some Widget {
        JourneyActivityWidget()
        AtAGlanceWidget()
        // From iOS 17 the system's edit sheet sets the stop or destination. A bundle cannot choose
        // between two widgets, so the iOS 16 ones are always listed but offer no sizes from 17.
        DeparturesWidget()
        RouteWidget()
        if #available(iOS 17.0, *) {
            ConfigurableDeparturesWidget()
            ConfigurableRouteWidget()
        }
    }
}

struct JourneyActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: JourneyActivityAttributes.self) { context in
            LockScreenView(title: context.attributes.title, state: context.state)
                .padding(16)
                .activityBackgroundTint(Color(.systemBackground).opacity(0.85))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Image(systemName: context.state.symbol)
                        .font(.title2)
                        .padding(.leading, 4)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Countdown(to: context.state.nextAt)
                        .font(.title3.monospacedDigit())
                        .padding(.trailing, 4)
                }
                DynamicIslandExpandedRegion(.center) {
                    Text(context.state.headline)
                        .font(.headline)
                        .lineLimit(1)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(context.state.detail)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .lineLimit(2)
                        JourneyBar(start: context.state.startAt, end: context.state.endAt, segments: context.state.segments)
                    }
                }
            } compactLeading: {
                Image(systemName: context.state.symbol)
            } compactTrailing: {
                Countdown(to: context.state.nextAt)
                    .monospacedDigit()
                    .frame(maxWidth: 52)
            } minimal: {
                Image(systemName: context.state.symbol)
            }
        }
    }
}

private struct LockScreenView: View {
    let title: String
    let state: JourneyActivityAttributes.ContentState

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 10) {
                Image(systemName: state.symbol)
                    .font(.title2)
                Text(state.headline)
                    .font(.headline)
                    .lineLimit(2)
                Spacer(minLength: 8)
                Countdown(to: state.nextAt)
                    .font(.title2.monospacedDigit().bold())
            }
            Text(state.detail)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .lineLimit(2)
            JourneyBar(start: state.startAt, end: state.endAt, segments: state.segments)
            Text(title)
                .font(.caption)
                .foregroundColor(.secondary)
        }
    }
}

/// How far along the journey is, filling by itself from its start to the arrival: leg by leg when
/// the segments are known (each fills over its own time, rides in the accent, walks and waits
/// muted, as Android 16's segmented bar), else as one bar; nothing without both times.
private struct JourneyBar: View {
    let start: Date?
    let end: Date?
    var segments: [JourneyActivityAttributes.Segment]?

    var body: some View {
        if let segments, segments.count > 1, let first = segments.first, let last = segments.last, last.endAt > first.startAt {
            let total = last.endAt.timeIntervalSince(first.startAt)
            GeometryReader { proxy in
                let gaps = CGFloat(segments.count - 1) * 2
                HStack(spacing: 2) {
                    ForEach(Array(segments.enumerated()), id: \.offset) { _, segment in
                        let share = max(segment.endAt.timeIntervalSince(segment.startAt), 1) / total
                        ProgressView(timerInterval: segment.startAt...max(segment.endAt, segment.startAt.addingTimeInterval(1)), countsDown: false) {
                            EmptyView()
                        } currentValueLabel: {
                            EmptyView()
                        }
                        .progressViewStyle(.linear)
                        .tint(segment.isRide ? Color.accentColor : Color.secondary)
                        .frame(width: max((proxy.size.width - gaps) * share, 4))
                    }
                }
            }
            .frame(height: 6)
            .accessibilityHidden(true)
        } else if let start, let end, end > start {
            ProgressView(timerInterval: start...end, countsDown: false) {
                EmptyView()
            } currentValueLabel: {
                EmptyView()
            }
            .progressViewStyle(.linear)
        }
    }
}

/// Counts down to `date` by itself, without the app updating it; nothing once it has passed.
private struct Countdown: View {
    let date: Date?

    init(to date: Date?) {
        self.date = date
    }

    var body: some View {
        if let date, date > Date() {
            Text(timerInterval: Date()...date, countsDown: true)
                .multilineTextAlignment(.trailing)
        }
    }
}
