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
    /// Bumped by "Try again" to ask Entur for the stops once more.
    @State private var stopsAttempt = 0

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
                    .task(id: "\(selected.serviceJourneyId ?? "")#\(stopsAttempt)") {
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
                    DetailsHeader(first: first, selected: selected, departures: departures, stopName: stopName, nowMillis: nowMillis, showMessages: showMessages)
                    if selected.isCancelled {
                        cancelledCallout(selected: selected, nowMillis: nowMillis)
                    }
                    let route = RouteColumn(departure: selected, stops: stops, nowMillis: nowMillis, onRetry: { stopsAttempt += 1 })
                    if geometry.size.width >= Self.twoColumnMinWidth {
                        HStack(alignment: .top, spacing: 24) {
                            ScrollView { departuresColumn(selected: selected, nowMillis: nowMillis) }
                            ScrollView { route }
                        }
                    } else {
                        ScrollView {
                            VStack(alignment: .leading, spacing: 16) {
                                departuresColumn(selected: selected, nowMillis: nowMillis)
                                route
                            }
                        }
                    }
                    // Hiding acts at once: the sheet closes and the board offers Undo.
                    VStack(alignment: .leading, spacing: 4) {
                        Button {
                            onFilterOut(first.line)
                            dismiss()
                        } label: {
                            Label(L("filter_out_line", first.line), systemImage: "line.3.horizontal.decrease.circle")
                        }
                        .buttonStyle(.bordered)
                        Text(L("filter_out_hint"))
                            .font(.pulse(.footnote))
                            .foregroundColor(Palette.textSecondary)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
            }
        }
    }

    /// A cancelled departure says so, with a way to the next one that runs (T4).
    @ViewBuilder
    private func cancelledCallout(selected: Departure, nowMillis: Int64) -> some View {
        let cancelledAt = clockText(selected.expectedTime, nowMillis: nowMillis)
        if let next = DepartureDetailsKt.nextRunningDeparture(departures: departures, cancelled: selected) {
            let nextAt = clockText(next.expectedTime, nowMillis: nowMillis)
            let nextIn = DepartureDetailsKt.minutesUntil(time: next.expectedTime, nowMillis: nowMillis).map { inTimeText($0.int64Value) } ?? ""
            Callout(L("details_cancelled_body", cancelledAt, nextAt, nextIn), tone: .error, title: L("details_cancelled_title")) {
                Button(L("details_show_departure", nextAt)) { selectedJourneyId = next.serviceJourneyId }
                    .buttonStyle(.borderless)
            }
        } else {
            Callout(L("details_cancelled_none", cancelledAt), tone: .error, title: L("details_cancelled_title"))
        }
    }

    private func departuresColumn(selected: Departure, nowMillis: Int64) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(L("details_departures")).font(.pulse(.subheadline, weight: .bold)).padding(.bottom, 6)
            ForEach(Array(departures.enumerated()), id: \.offset) { _, departure in
                DepartureDetailRow(departure: departure, isSelected: departure === selected, nowMillis: nowMillis, showDelays: showDelays)
                    .contentShape(Rectangle())
                    .onTapGesture { selectedJourneyId = departure.serviceJourneyId }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Line pill and destination (wrapping: details are where cut board text is read in full), where it
/// leaves from, then the selected departure large: countdown, clock time, Live, how often the line runs.
private struct DetailsHeader: View {
    let first: Departure
    let selected: Departure
    let departures: [Departure]
    let stopName: String
    let nowMillis: Int64
    let showMessages: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 12) {
                LinePill(line: first.line, mode: first.transportMode)
                VStack(alignment: .leading, spacing: 2) {
                    Text(first.destination)
                        .font(.pulse(role: FjordTokens.typeHeadline, relativeTo: .title2))
                        .fixedSize(horizontal: false, vertical: true)
                    Text([L("details_from", stopName), selected.platform.map { L("details_platform", $0) }].compactMap { $0 }.joined(separator: " · "))
                        .font(.pulse(.subheadline))
                        .foregroundColor(Palette.textSecondary)
                }
            }
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Text(countdown)
                    .font(.pulse(size: 32, weight: .bold, relativeTo: .largeTitle))
                    .foregroundColor(selected.isCancelled ? Palette.error : Palette.textPrimary)
                Text(clockText(selected.expectedTime, nowMillis: nowMillis))
                    .font(.pulse(.title3))
                    .strikethrough(selected.isCancelled)
                    .foregroundColor(Palette.textSecondary)
                if selected.isRealtime && !selected.isCancelled { LiveChip() }
                if let headway = DepartureDetailsKt.typicalHeadwayMinutes(departures: departures) {
                    Text(L("details_every", headway.intValue)).font(.pulse(.subheadline)).foregroundColor(Palette.textSecondary)
                }
            }
            let line = [first.lineName, first.authority, first.operator].compactMap { $0 }.uniqued().joined(separator: " · ")
            if !line.isEmpty {
                Text(line).font(.pulse(.footnote)).foregroundColor(Palette.textSecondary)
            }
            if showMessages {
                ForEach(departures.flatMap(\.notices).uniqued(), id: \.self) { notice in
                    NoticeText(notice: notice)
                }
            }
        }
    }

    private var countdown: String {
        if selected.isCancelled { return L("cancelled") }
        let countdown = DepartureCountdownKt.departureCountdown(expectedTime: selected.expectedTime, nowMillis: nowMillis)
        if countdown is DepartureCountdownNow { return L("now") }
        return DepartureDetailsKt.minutesUntil(time: selected.expectedTime, nowMillis: nowMillis).map { L("countdown_minutes", $0.intValue) } ?? ""
    }
}

/// "Live", on the success colours: the time comes from the vehicle, not the timetable.
struct LiveChip: View {
    var body: some View {
        Text(L("details_live"))
            .font(.pulse(.caption, weight: .bold))
            .foregroundColor(Palette.onSuccessContainer)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(RoundedRectangle(cornerRadius: FjordTokens.radiusSm).fill(Palette.successContainer))
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
                        .font(.pulse(.headline))
                        .monospacedDigit()
                        .strikethrough(departure.isCancelled)
                        .foregroundColor(departure.isCancelled ? Palette.textSecondary : Palette.textPrimary)
                    if isDelayed {
                        Text(clockText(departure.aimedTime, nowMillis: nowMillis))
                            .font(.pulse(.subheadline))
                            .monospacedDigit()
                            .strikethrough()
                            .foregroundColor(Palette.textSecondary)
                        DelayChip(minutes: Int(departure.delayMinutes))
                    }
                }
                facts
            }
            Spacer()
            countdown
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(RoundedRectangle(cornerRadius: FjordTokens.radiusMd).fill(isSelected ? Palette.primaryContainer : Color.clear))
    }

    /// Only live ones are marked: departures further out have no live data yet, as is normal.
    @ViewBuilder
    private var facts: some View {
        let platform = departure.platform.map { L("details_platform", $0) }
        let isLive = departure.isRealtime && !departure.isCancelled
        if platform != nil || isLive {
            HStack(spacing: 8) {
                if isLive { LiveChip() }
                if let platform { Text(platform).font(.pulse(.caption)).foregroundColor(Palette.textSecondary) }
            }
        }
    }

    @ViewBuilder
    private var countdown: some View {
        if departure.isCancelled {
            Text(L("cancelled")).font(.pulse(.subheadline, weight: .bold)).foregroundColor(Palette.error)
        } else if case .now = onEnum(of: DepartureCountdownKt.departureCountdown(expectedTime: departure.expectedTime, nowMillis: nowMillis)) {
            Text(L("now")).font(.pulse(.subheadline, weight: .bold)).foregroundColor(Palette.now)
        } else if let minutes = DepartureDetailsKt.minutesUntil(time: departure.expectedTime, nowMillis: nowMillis) {
            Text(inTimeText(minutes.int64Value)).font(.pulse(.subheadline, weight: .bold)).monospacedDigit()
        }
    }
}

/// The selected departure's stops from here to the end of the line, on a rail in the line's colour.
/// Only this section waits for Entur: skeleton rows while it loads, a note with "Try again" when no
/// stops come (T4: the rest of the sheet is fine).
private struct RouteColumn: View {
    let departure: Departure
    let stops: [JourneyStop]?
    let nowMillis: Int64
    let onRetry: () -> Void
    @State private var pulse = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(L("details_route", clockText(departure.expectedTime, nowMillis: nowMillis)))
                .font(.pulse(.subheadline, weight: .bold))
            if let ride = ride {
                Text(ride).font(.pulse(.caption)).foregroundColor(Palette.textSecondary)
            }
            Spacer().frame(height: 8)
            if let stops = stops {
                if stops.isEmpty {
                    Callout(L("details_stops_failed"), tone: .warning) {
                        Button(L("try_again"), action: onRetry).buttonStyle(.borderless)
                    }
                } else {
                    let rail = Palette.transport(departure.transportMode)
                    let boardHere = [departure.platform.map { L("details_platform", $0) }, L("details_board_here")].compactMap { $0 }.joined(separator: " · ")
                    ForEach(Array(stops.enumerated()), id: \.offset) { index, stop in
                        RouteStopRow(
                            stop: stop,
                            nowMillis: nowMillis,
                            isFirst: index == 0,
                            isLast: index == stops.count - 1,
                            railColor: rail,
                            label: index == 0 ? boardHere : (index == stops.count - 1 ? L("details_last_stop") : nil)
                        )
                    }
                }
            } else {
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(0 ..< 5, id: \.self) { _ in
                        HStack(spacing: 12) {
                            PlaceholderBlock(width: 40, height: 14, pulse: pulse)
                            PlaceholderBlock(width: 10, height: 10, cornerRadius: 5, pulse: pulse)
                            PlaceholderBlock(height: 14, pulse: pulse)
                        }
                    }
                }
                .modifier(PlaceholderPulse(pulse: $pulse))
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

struct RouteStopRow: View {
    let stop: JourneyStop
    let nowMillis: Int64
    let isFirst: Bool
    let isLast: Bool
    var railColor: Color = Palette.primary
    var label: String?

    var body: some View {
        let isEnd = isFirst || isLast
        HStack(spacing: 8) {
            Text(clockText(stop.expectedTime, nowMillis: nowMillis))
                .font(.pulse(.subheadline, weight: isEnd ? .bold : .regular))
                .monospacedDigit()
                .strikethrough(stop.isCancelled)
                .foregroundColor(Palette.textSecondary)
                .reservingWidth(of: widestClockText)
                // Measured in bold on every row, so the rail stays straight past the regular ones.
                .font(.pulse(.subheadline, weight: .bold))
            // The timeline: a rail through every stop, with a larger dot at both ends.
            ZStack {
                VStack(spacing: 0) {
                    Rectangle().fill(isFirst ? Color.clear : railColor).frame(width: 3)
                    Rectangle().fill(isLast ? Color.clear : railColor).frame(width: 3)
                }
                Circle()
                    .fill(isEnd ? railColor : Palette.surface)
                    .overlay(Circle().stroke(railColor, lineWidth: 2))
                    .frame(width: isEnd ? 12 : 8, height: isEnd ? 12 : 8)
            }
            .frame(width: 20)
            VStack(alignment: .leading, spacing: 0) {
                Text(stop.name)
                    .font(.pulse(.subheadline, weight: isEnd ? .semibold : .regular))
                    .strikethrough(stop.isCancelled)
                    .fixedSize(horizontal: false, vertical: true)
                if let label {
                    Text(label).font(.pulse(.caption)).foregroundColor(Palette.textSecondary)
                }
            }
            .padding(.vertical, 6)
        }
        .frame(minHeight: 36)
    }
}

/// "in 14 min", or "in 1 h 5 min" from an hour out.
func inTimeText(_ minutes: Int64) -> String {
    minutes < 60
        ? L("details_in_minutes", Int(minutes))
        : L("details_in_hours", Int(minutes / 60), Int(minutes % 60))
}

/// "HH:mm", with the board's "⁺1" when it falls on a later day.
func clockText(_ time: String, nowMillis: Int64) -> String {
    guard let clock = DepartureCountdownKt.departureClock(time: time, nowMillis: nowMillis) else { return "?" }
    let text = clockFormatter.string(from: Date(timeIntervalSince1970: TimeInterval(clock.epochMillis) / 1000))
    return clock.dayOffset > 0 ? "\(text)⁺\(clock.dayOffset)" : text
}

/// The widest `clockText`, for the columns of times that line up down a list.
let widestClockText = "00:00⁺1"

extension View {
    /// Keeps this view on one line in a column as wide as `widest`, or wider if it needs to be.
    /// The column is measured in the font set on the result, so set the font after this call: a
    /// width in points fits one text size only, and at a larger one "23:33" broke into "23:3" and
    /// "3", or was cut to "23:…".
    func reservingWidth(of widest: String, alignment: Alignment = .leading) -> some View {
        ZStack(alignment: alignment) {
            Text(widest).monospacedDigit().hidden()
            lineLimit(1)
        }
        .fixedSize(horizontal: true, vertical: false)
    }
}

/// 24-hour in Norway time, like the board, whatever the locale's default.
private let clockFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateFormat = "HH:mm"
    formatter.timeZone = .norway
    return formatter
}()
