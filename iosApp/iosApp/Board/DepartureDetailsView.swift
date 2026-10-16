import Shared
import SwiftUI

/// Every loaded departure of one line to one destination at a stop, opened by tapping its row: the
/// board shows only the next few. The header names the line, who runs it and how often; each
/// departure has its platform and countdown; and the selected one's remaining stops are fetched
/// with `loadJourney` while the sheet is open. Filtering the line out lives here too, as on Android.
struct DepartureDetailsView: View {
    let stopName: String
    let departures: [Departure]
    let showDelays: Bool
    let showMessages: Bool
    let onFilterOut: (String) -> Void
    let loadJourney: (Departure) async -> [JourneyStop]

    @Environment(\.dismiss) private var dismiss
    /// Kept by trip rather than position, so a refresh that drops the departures already gone
    /// leaves the same one selected.
    @State private var selectedJourneyId: String?
    /// Nil while loading.
    @State private var stops: [JourneyStop]?

    /// How long the details stay open untouched: a wall display left showing them would never
    /// return to the board.
    private static let autoCloseNanoseconds: UInt64 = 120 * 1_000_000_000
    /// Wide enough for the departures and the selected trip's stops side by side.
    private static let twoColumnMinWidth: CGFloat = 520

    private var selected: Departure? {
        departures.first { $0.serviceJourneyId == selectedJourneyId && selectedJourneyId != nil }
            ?? departures.first { !$0.isCancelled }
            ?? departures.first
    }

    var body: some View {
        NavigationView {
            if let first = departures.first, let selected = selected {
                content(first: first, selected: selected)
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .confirmationAction) {
                            Button(L("back")) { dismiss() }
                        }
                    }
                    // Keyed by trip, so a refresh of the same departure keeps its stops.
                    .task(id: selected.serviceJourneyId) {
                        stops = nil
                        let loaded = await loadJourney(selected)
                        // A load superseded by picking another departure must not overwrite its stops.
                        if !Task.isCancelled { stops = loaded }
                    }
            }
        }
        .navigationViewStyle(.stack)
        // Restarted by picking a departure, so only an untouched sheet closes itself.
        .task(id: selectedJourneyId) {
            try? await Task.sleep(nanoseconds: Self.autoCloseNanoseconds)
            if !Task.isCancelled { dismiss() }
        }
    }

    private func content(first: Departure, selected: Departure) -> some View {
        TimelineView(.periodic(from: .now, by: 1)) { timeline in
            let nowMillis = Int64(timeline.date.timeIntervalSince1970 * 1000)
            GeometryReader { geometry in
                VStack(alignment: .leading, spacing: 16) {
                    DetailsHeader(first: first, departures: departures, stopName: stopName, showMessages: showMessages)
                    if geometry.size.width >= Self.twoColumnMinWidth {
                        HStack(alignment: .top, spacing: 24) {
                            ScrollView { departuresColumn(selected: selected, nowMillis: nowMillis) }
                            ScrollView { RouteColumn(departure: selected, stops: stops, nowMillis: nowMillis) }
                        }
                    } else {
                        ScrollView {
                            VStack(alignment: .leading, spacing: 16) {
                                departuresColumn(selected: selected, nowMillis: nowMillis)
                                RouteColumn(departure: selected, stops: stops, nowMillis: nowMillis)
                            }
                        }
                    }
                    Button(L("filter_out_line", first.line)) {
                        onFilterOut(first.line)
                        dismiss()
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
            }
        }
    }

    private func departuresColumn(selected: Departure, nowMillis: Int64) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(L("details_departures")).font(.subheadline.bold()).padding(.bottom, 6)
            ForEach(Array(departures.enumerated()), id: \.offset) { _, departure in
                DepartureDetailRow(departure: departure, isSelected: departure === selected, nowMillis: nowMillis, showDelays: showDelays)
                    .contentShape(Rectangle())
                    .onTapGesture { selectedJourneyId = departure.serviceJourneyId }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Line badge and destination, then the line's full name, who runs it, the stop and how often.
private struct DetailsHeader: View {
    let first: Departure
    let departures: [Departure]
    let stopName: String
    let showMessages: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 12) {
                Text(first.line)
                    .font(.headline)
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(RoundedRectangle(cornerRadius: 6).fill(Palette.transport(first.transportMode)))
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(Palette.transportEmoji(first.transportMode))  \(first.destination)")
                        .font(.title3.bold())
                        .lineLimit(1)
                    let line = [first.lineName, first.authority, first.operator].compactMap { $0 }.uniqued().joined(separator: " · ")
                    if !line.isEmpty {
                        Text(line).font(.subheadline).foregroundColor(Palette.textSecondary).lineLimit(1)
                    }
                    let headway = DepartureDetailsKt.typicalHeadwayMinutes(departures: departures).map { L("details_every", $0.intValue) }
                    Text([stopName, headway].compactMap { $0 }.joined(separator: " · "))
                        .font(.subheadline)
                        .foregroundColor(Palette.textSecondary)
                }
            }
            let notices = departures.flatMap(\.notices).uniqued()
            if showMessages {
                ForEach(notices, id: \.self) { notice in
                    Text("⚠️ \(notice)").font(.footnote).foregroundColor(Palette.error)
                }
            }
        }
    }
}

private struct DepartureDetailRow: View {
    let departure: Departure
    let isSelected: Bool
    let nowMillis: Int64
    let showDelays: Bool

    var body: some View {
        let isDelayed = showDelays && !departure.isCancelled && departure.delayMinutes > 0
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(clockText(departure.expectedTime, nowMillis: nowMillis))
                        .font(.headline)
                        .monospacedDigit()
                        .strikethrough(departure.isCancelled)
                        .foregroundColor(departure.isCancelled ? Palette.textSecondary : Palette.textPrimary)
                    if isDelayed {
                        Text(clockText(departure.aimedTime, nowMillis: nowMillis))
                            .font(.subheadline)
                            .monospacedDigit()
                            .strikethrough()
                            .foregroundColor(Palette.textSecondary)
                        Text("+\(departure.delayMinutes)")
                            .font(.caption.bold())
                            .foregroundColor(Palette.error)
                    }
                }
                facts
            }
            Spacer()
            countdown
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(RoundedRectangle(cornerRadius: 8).fill(isSelected ? Palette.primary.opacity(0.12) : Color.clear))
    }

    /// Only live ones are marked: departures further out have no live data yet, as is normal.
    @ViewBuilder
    private var facts: some View {
        let platform = departure.platform.map { L("details_platform", $0) }
        let isLive = departure.isRealtime && !departure.isCancelled
        if platform != nil || isLive {
            let live = isLive ? Text("● \(L("details_live"))").bold().foregroundColor(Palette.primary) : Text(verbatim: "")
            let separator = isLive && platform != nil ? Text(verbatim: " · ") : Text(verbatim: "")
            (live + separator + Text(platform ?? ""))
                .font(.caption)
                .foregroundColor(Palette.textSecondary)
        }
    }

    @ViewBuilder
    private var countdown: some View {
        if departure.isCancelled {
            Text(L("cancelled")).font(.subheadline.bold()).foregroundColor(Palette.error)
        } else if case .now = onEnum(of: DepartureCountdownKt.departureCountdown(expectedTime: departure.expectedTime, nowMillis: nowMillis)) {
            Text(L("now")).font(.subheadline.bold()).foregroundColor(Palette.primary)
        } else if let minutes = DepartureDetailsKt.minutesUntil(time: departure.expectedTime, nowMillis: nowMillis) {
            Text(inTimeText(minutes.int64Value)).font(.subheadline.bold()).monospacedDigit()
        }
    }
}

/// The selected departure's stops from here to the end of the line, as a timeline.
private struct RouteColumn: View {
    let departure: Departure
    let stops: [JourneyStop]?
    let nowMillis: Int64

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(L("details_route", clockText(departure.expectedTime, nowMillis: nowMillis)))
                .font(.subheadline.bold())
            if let ride = ride {
                Text(ride).font(.caption).foregroundColor(Palette.textSecondary)
            }
            Spacer().frame(height: 8)
            if let stops = stops {
                if stops.isEmpty {
                    Text(L("details_route_unavailable")).font(.subheadline).foregroundColor(Palette.textSecondary)
                } else {
                    ForEach(Array(stops.enumerated()), id: \.offset) { index, stop in
                        RouteStopRow(stop: stop, nowMillis: nowMillis, isFirst: index == 0, isLast: index == stops.count - 1)
                    }
                }
            } else {
                ProgressView().frame(maxWidth: .infinity).padding()
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var ride: String? {
        guard let stops = stops, stops.count > 1, let from = stops.first, let to = stops.last,
              let minutes = DepartureDetailsKt.minutesBetween(fromTime: from.expectedTime, toTime: to.expectedTime)
        else { return nil }
        return L("details_ride", minutes.intValue, to.name)
    }
}

private struct RouteStopRow: View {
    let stop: JourneyStop
    let nowMillis: Int64
    let isFirst: Bool
    let isLast: Bool

    var body: some View {
        let isEnd = isFirst || isLast
        HStack(spacing: 8) {
            Text(clockText(stop.expectedTime, nowMillis: nowMillis))
                .font(.subheadline.weight(isEnd ? .bold : .regular))
                .monospacedDigit()
                .strikethrough(stop.isCancelled)
                .foregroundColor(Palette.textSecondary)
                .frame(width: 52, alignment: .leading)
            // The timeline: a rail through every stop, with a larger dot at both ends.
            ZStack {
                VStack(spacing: 0) {
                    Rectangle().fill(isFirst ? Color.clear : Palette.primary).frame(width: 2)
                    Rectangle().fill(isLast ? Color.clear : Palette.primary).frame(width: 2)
                }
                Circle().fill(Palette.primary).frame(width: isEnd ? 12 : 8, height: isEnd ? 12 : 8)
            }
            .frame(width: 20)
            Text(stop.name)
                .font(.subheadline.weight(isEnd ? .bold : .regular))
                .strikethrough(stop.isCancelled)
                .lineLimit(1)
        }
        .frame(height: 32)
    }
}

/// "in 14 min", or "in 1 h 5 min" from an hour out.
private func inTimeText(_ minutes: Int64) -> String {
    minutes < 60
        ? L("details_in_minutes", Int(minutes))
        : L("details_in_hours", Int(minutes / 60), Int(minutes % 60))
}

/// "HH:mm", with the board's "⁺1" when it falls on a later day.
private func clockText(_ time: String, nowMillis: Int64) -> String {
    guard let clock = DepartureCountdownKt.departureClock(time: time, nowMillis: nowMillis) else { return "?" }
    let text = clockFormatter.string(from: Date(timeIntervalSince1970: TimeInterval(clock.epochMillis) / 1000))
    return clock.dayOffset > 0 ? "\(text)⁺\(clock.dayOffset)" : text
}

/// 24-hour, like the board, whatever the locale's default.
private let clockFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateFormat = "HH:mm"
    return formatter
}()
