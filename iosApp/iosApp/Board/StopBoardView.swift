import Shared
import SwiftUI

/// The departure board: a first, read-only version that proves the shared pipeline end to end.
struct StopBoardView: View {
    @StateObject private var model = StopBoardModel()

    var body: some View {
        let state = model.state
        VStack(spacing: 0) {
            BoardHeader(state: state)
            if state.favorites.isEmpty {
                Spacer()
                Text("No stops configured yet.")
                    .font(.title3)
                Spacer()
            } else {
                List {
                    ForEach(state.departures, id: \.stopId) { stop in
                        Section(stopName(stop.stopId, in: state.favorites)) {
                            ForEach(Array(stop.groups.enumerated()), id: \.offset) { _, group in
                                if let departure = group.departures.first {
                                    DepartureRow(departure: departure)
                                }
                            }
                        }
                    }
                }
                .listStyle(.insetGrouped)
            }
            BoardFooter(state: state)
        }
    }

    private func stopName(_ stopId: String, in favorites: [StopPlace]) -> String {
        favorites.first { $0.id == stopId }?.name ?? stopId
    }
}

private struct BoardHeader: View {
    let state: StopBoardViewModel.UiState

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text(state.currentTime)
                .font(.system(size: 48, weight: .bold, design: .rounded))
                .foregroundColor(.accentColor)
                .monospacedDigit()
            Text(state.currentDayOfMonth)
                .font(.system(size: 40, weight: .bold, design: .rounded))
            VStack(alignment: .leading) {
                Text(state.currentWeekday)
                Text(state.currentMonth)
            }
            .font(.subheadline)
            .foregroundColor(.secondary)
            Spacer()
        }
        .padding()
    }
}

private struct DepartureRow: View {
    let departure: Departure

    var body: some View {
        HStack(spacing: 12) {
            Text(departure.line)
                .font(.headline)
                .foregroundColor(.white)
                .frame(minWidth: 52)
                .padding(.vertical, 6)
                .background(RoundedRectangle(cornerRadius: 6).fill(Color.blue))
            Text(departure.destination)
                .font(.title3)
                .strikethrough(departure.isCancelled)
                .lineLimit(1)
            Spacer()
            if departure.delayMinutes > 0 {
                Text("+\(departure.delayMinutes)")
                    .font(.footnote.bold())
                    .foregroundColor(.red)
            }
            // Ticks on its own schedule. The shared state only changes about once a minute (equal
            // states are not re-emitted), and a row whose only input is the same Departure object
            // is not redrawn at all, so neither can be relied on to move "5m" towards "Now".
            TimelineView(.periodic(from: .now, by: 1)) { timeline in
                Text(countdownText(now: timeline.date))
                    .font(.title2.bold())
                    .monospacedDigit()
            }
        }
    }

    /// 24-hour, like the Android board, whatever the locale's default.
    private static let clockFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter
    }()

    private func countdownText(now: Date) -> String {
        let nowMillis = Int64(now.timeIntervalSince1970 * 1000)
        switch onEnum(of: DepartureCountdownKt.departureCountdown(expectedTime: departure.expectedTime, nowMillis: nowMillis)) {
        case .now:
            return "Now"
        case let .minutes(countdown):
            return "\(countdown.minutes)m"
        case let .clock(countdown):
            let date = Date(timeIntervalSince1970: TimeInterval(countdown.epochMillis) / 1000)
            let time = Self.clockFormatter.string(from: date)
            return countdown.dayOffset > 0 ? "\(time) +\(countdown.dayOffset)" : time
        case .unknown:
            return "?"
        }
    }
}

private struct BoardFooter: View {
    let state: StopBoardViewModel.UiState

    var body: some View {
        VStack(spacing: 4) {
            if let staleMinutes = state.staleAgeMinutes {
                Label("Offline — shown data is \(staleMinutes.intValue) min old", systemImage: "icloud.slash")
                    .font(.footnote.bold())
                    .foregroundColor(.red)
            }
            Text("Last updated: \(state.lastUpdated)")
                .font(.footnote)
                .foregroundColor(.secondary)
        }
        .padding(.vertical, 8)
    }
}
