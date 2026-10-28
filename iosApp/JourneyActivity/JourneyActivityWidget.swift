import ActivityKit
import SwiftUI
import WidgetKit

/// The JourneyActivity extension: the Live Activity of a followed journey, on the Lock Screen and
/// in the Dynamic Island. Content comes from the app (`JourneyLiveActivity`).
@main
struct JourneyActivityBundle: WidgetBundle {
    var body: some Widget {
        JourneyActivityWidget()
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
                    Text(context.state.detail)
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .lineLimit(2)
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
            Text(title)
                .font(.caption)
                .foregroundColor(.secondary)
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
