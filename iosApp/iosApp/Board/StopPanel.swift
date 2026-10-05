import Shared
import SwiftUI

/// One favourite stop's card: its name, then its departures, grouped by line and destination.
struct StopPanel: View {
    let stop: StopPlace
    let groups: [DepartureGroup]
    let showDelays: Bool
    let showMessages: Bool
    let isLoading: Bool
    let isExtended: Bool
    let onFilterOut: (String) -> Void
    let onSearchFurtherAhead: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            VStack(alignment: .leading, spacing: 0) {
                Text(stop.name)
                    .font(.title3.bold())
                    .lineLimit(1)
                if let locality = stop.locality {
                    Text(locality)
                        .font(.subheadline)
                        .foregroundColor(Palette.textSecondary)
                }
            }
            Divider()
            if isLoading && groups.isEmpty {
                LoadingPlaceholder()
            } else if groups.isEmpty {
                NoDepartures(isExtended: isExtended, onSearchFurtherAhead: onSearchFurtherAhead)
            } else {
                ScrollView {
                    LazyVStack(spacing: 8) {
                        // Identified by content, so a refresh that shifts the list gives a changed group
                        // fresh row state. Entur can return identical-looking journeys, so the index
                        // is part of the id to keep it unique, as on Android.
                        ForEach(Array(groups.enumerated()).map { (id: "\($0.offset)_\($0.element.rowID)", group: $0.element) }, id: \.id) { row in
                            DepartureGroupRow(
                                group: row.group,
                                showDelays: showDelays,
                                showMessages: showMessages,
                                onFilterOut: onFilterOut
                            )
                        }
                    }
                }
            }
        }
        .foregroundColor(Palette.textPrimary)
        .padding(12)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(RoundedRectangle(cornerRadius: 12).fill(Palette.surface).shadow(color: .black.opacity(0.08), radius: 2, y: 1))
        .padding(6)
    }
}

private struct NoDepartures: View {
    let isExtended: Bool
    let onSearchFurtherAhead: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            Spacer()
            Text(isExtended
                ? L("no_departures_extended", Int(EnturRepositoryCompanion.shared.EXTENDED_DEPARTURE_WINDOW_DAYS))
                : L("no_departures"))
                .foregroundColor(Palette.textSecondary)
                .multilineTextAlignment(.center)
            if !isExtended {
                Button(L("search_further_ahead"), action: onSearchFurtherAhead)
                    .buttonStyle(.bordered)
            }
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }
}

private struct LoadingPlaceholder: View {
    @State private var pulse = false

    var body: some View {
        VStack(spacing: 8) {
            ForEach(0 ..< 6, id: \.self) { _ in
                HStack(spacing: 10) {
                    RoundedRectangle(cornerRadius: 6).frame(width: 48, height: 24)
                    RoundedRectangle(cornerRadius: 4).frame(height: 20)
                    RoundedRectangle(cornerRadius: 4).frame(width: 40, height: 20)
                }
                .foregroundColor(Palette.textSecondary.opacity(pulse ? 0.3 : 0.1))
                .padding(8)
                .background(RoundedRectangle(cornerRadius: 8).fill(Palette.card.opacity(0.4)))
            }
            Spacer()
        }
        .onAppear {
            withAnimation(.linear(duration: 1).repeatForever(autoreverses: true)) { pulse = true }
        }
    }
}

/// Consecutive departures of one line to one destination: the next one large, later ones beside.
private struct DepartureGroupRow: View {
    let group: DepartureGroup
    let showDelays: Bool
    let showMessages: Bool
    let onFilterOut: (String) -> Void
    /// The line the user tapped, captured at the tap: the dialog acts on it even if a refresh
    /// changes this row's group while the dialog is open.
    @State private var lineToFilter: String?

    var body: some View {
        let departures = group.departures
        if let first = departures.first {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 10) {
                    Text(first.line)
                        .font(.subheadline.bold())
                        .strikethrough(first.isCancelled)
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .frame(width: 48)
                        .padding(.vertical, 4)
                        .background(RoundedRectangle(cornerRadius: 6).fill(first.isCancelled ? Color.gray : Palette.transport(first.transportMode)))
                    Text("\(Palette.transportEmoji(first.transportMode))  \(first.destination)")
                        .font(.body.weight(.medium))
                        .strikethrough(first.isCancelled)
                        .foregroundColor(first.isCancelled ? Palette.textSecondary.opacity(0.6) : Palette.textPrimary)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    if !first.isCancelled && showDelays && first.delayMinutes > 0 {
                        Text("+\(first.delayMinutes)")
                            .font(.caption.bold())
                            .foregroundColor(Palette.error)
                    }
                    // Ticks on its own: the shared state only changes about once a minute, and a row
                    // whose input is the same Departure object would not be redrawn at all.
                    TimelineView(.periodic(from: .now, by: 1)) { timeline in
                        let times = DepartureTimes(departures: departures, now: timeline.date)
                        Text(times.primary)
                            .font(.title3.weight(.heavy))
                            .monospacedDigit()
                            .foregroundColor(first.isCancelled ? Palette.error : (times.primaryIsNow ? Palette.primary : Palette.textPrimary))
                    }
                }
                TimelineView(.periodic(from: .now, by: 1)) { timeline in
                    let secondary = DepartureTimes(departures: departures, now: timeline.date).secondary
                    if !secondary.isEmpty {
                        Text(secondary.joined(separator: "    "))
                            .font(.body.weight(.medium))
                            .foregroundColor(Palette.textSecondary)
                            .padding(.leading, 58)
                    }
                }
                let notices = departures.flatMap(\.notices).uniqued()
                if showMessages && !notices.isEmpty {
                    ForEach(notices, id: \.self) { notice in
                        HStack(spacing: 4) {
                            Text("⚠️").font(.system(size: 10))
                            Text(notice)
                                .font(.system(size: 11))
                                .foregroundColor(Palette.error)
                                .lineLimit(2)
                        }
                    }
                }
            }
            .padding(8)
            .background(RoundedRectangle(cornerRadius: 8).fill(Palette.card.opacity(first.isCancelled ? 0.2 : 0.5)))
            .contentShape(Rectangle())
            .onTapGesture { lineToFilter = first.line }
            .confirmationDialog(
                lineToFilter ?? first.line,
                isPresented: Binding(get: { lineToFilter != nil }, set: { if !$0 { lineToFilter = nil } }),
                presenting: lineToFilter
            ) { line in
                Button(L("filter_out_line", line)) { onFilterOut(line) }
            }
        }
    }
}

/// The time column's texts for a group, de-duplicated so two departures in the same minute show once.
private struct DepartureTimes {
    let primary: String
    let primaryIsNow: Bool
    let secondary: [String]

    init(departures: [Departure], now: Date) {
        let nowMillis = Int64(now.timeIntervalSince1970 * 1000)
        let texts = departures.map { departure in
            departure.isCancelled ? L("cancelled") : Self.text(for: departure.expectedTime, nowMillis: nowMillis)
        }
        .uniqued()
        primary = texts.first ?? "?"
        primaryIsNow = primary == L("now")
        secondary = Array(texts.dropFirst())
    }

    private static func text(for expectedTime: String, nowMillis: Int64) -> String {
        switch onEnum(of: DepartureCountdownKt.departureCountdown(expectedTime: expectedTime, nowMillis: nowMillis)) {
        case .now:
            return L("now")
        case let .minutes(countdown):
            return "\(countdown.minutes)\(L("unit_minute"))"
        case let .clock(countdown):
            let time = clockFormatter.string(from: Date(timeIntervalSince1970: TimeInterval(countdown.epochMillis) / 1000))
            // Beyond today only when the search was widened; marked like a printed timetable.
            return countdown.dayOffset > 0 ? "\(time)⁺\(countdown.dayOffset)" : time
        case .unknown:
            return "?"
        }
    }

    /// 24-hour, like the Android board, whatever the locale's default.
    private static let clockFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter
    }()
}

private extension DepartureGroup {
    /// Line, destination, time and quay of the group's first departure, like Android's row key.
    var rowID: String {
        guard let first = departures.first else { return "empty" }
        return "\(first.line)_\(first.destination)_\(first.expectedTime)_\(first.quayId ?? "")"
    }
}

extension Array where Element: Hashable {
    /// The elements in order, without repeats.
    func uniqued() -> [Element] {
        var seen = Set<Element>()
        return filter { seen.insert($0).inserted }
    }
}
