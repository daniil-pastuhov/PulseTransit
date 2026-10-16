import Shared
import SwiftUI

/// One favourite stop's card: its name, then its departures, grouped by line and destination.
/// `groups` is nil until the stop's first load finishes, which shows a blurred placeholder;
/// `isLoading` on a stop that already has data shows a thin bar instead, so a refresh never blanks
/// a board that is being read. `isFailed` means the empty board is unknown rather than empty, and
/// shows why with a retry instead of "no departures". With `canLoadMore`, scrolling near the end of
/// the list calls `onLoadMore` for the next page; `isLoadingMore` is that page in flight, shown as a
/// spinner at the end of the list only.
struct StopPanel: View {
    let stop: StopPlace
    let groups: [DepartureGroup]?
    let showDelays: Bool
    let showMessages: Bool
    let isLoading: Bool
    let isExtended: Bool
    let isFailed: Bool
    let isOnline: Bool
    let canLoadMore: Bool
    let isLoadingMore: Bool
    let onFilterOut: (String) -> Void
    let onSearchFurtherAhead: () -> Void
    let onRetry: () -> Void
    let onLoadMore: () -> Void

    /// Whether the end-of-list sentinel is on screen; scrolling while it is asks for a page.
    @State private var isAtEnd = false

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
            // Same height either way, so the list below doesn't jump when a refresh starts.
            if isLoading && groups != nil {
                RefreshBar()
            } else {
                Divider().frame(height: 2)
            }
            // Overlaid, not stacked, so the placeholder and the list cross-fade in place.
            ZStack(alignment: .top) {
                if let groups = groups {
                    departures(groups)
                        .transition(.opacity)
                } else {
                    BlurredPlaceholder(label: L("loading_departures")) { LoadingPlaceholder() }
                        .transition(.opacity)
                }
            }
            .frame(maxHeight: .infinity, alignment: .top)
        }
        .animation(.easeInOut(duration: 0.3), value: groups == nil)
        .foregroundColor(Palette.textPrimary)
        .padding(12)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(RoundedRectangle(cornerRadius: 12).fill(Palette.surface).shadow(color: .black.opacity(0.08), radius: 2, y: 1))
        .padding(6)
    }

    @ViewBuilder
    private func departures(_ groups: [DepartureGroup]) -> some View {
        if groups.isEmpty && isFailed {
            LoadFailedMessage(isOnline: isOnline, onRetry: onRetry)
        } else if groups.isEmpty {
            NoDepartures(isExtended: isExtended, onSearchFurtherAhead: onSearchFurtherAhead)
        } else {
            list(groups)
        }
    }

    private func list(_ groups: [DepartureGroup]) -> some View {
        // Only the user's scrolling asks for a page, as on Android. A list that fits the card
        // never does: a single line folds every page into the same row, so it would keep fitting
        // and keep asking. Nor may a board left at its end on the wall grow on every refresh.
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
                if isLoadingMore {
                    ProgressView()
                        .padding(.vertical, 8)
                }
                // Marks the end of the list. Always present, never toggled by refreshes or
                // loading state; being on screen is not enough to load, the user must scroll.
                Color.clear
                    .frame(height: 1)
                    .onAppear { isAtEnd = true }
                    .onDisappear { isAtEnd = false }
            }
            .background(GeometryReader { content in
                Color.clear.preference(key: ScrollOffsetKey.self, value: content.frame(in: .named(Self.scrollSpace)).minY)
            })
        }
        .coordinateSpace(name: Self.scrollSpace)
        // The content only moves under the user's finger (or its momentum): a refresh changes rows,
        // not where the list starts. So a move with the end in sight is a scroll to the end.
        .onPreferenceChange(ScrollOffsetKey.self) { _ in
            if isAtEnd && canLoadMore && !isLoading && !isLoadingMore { onLoadMore() }
        }
    }

    private static let scrollSpace = "stopPanelScroll"
}

/// Where a list's content starts within its scroll view, which changes only as it is scrolled.
private struct ScrollOffsetKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
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
                    PlaceholderBlock(width: 48, height: 24, cornerRadius: 6, pulse: pulse)
                    PlaceholderBlock(height: 20, pulse: pulse)
                    PlaceholderBlock(width: 40, height: 20, pulse: pulse)
                }
                .padding(8)
                .background(RoundedRectangle(cornerRadius: 8).fill(Palette.card.opacity(0.4)))
            }
            Spacer()
        }
        .modifier(PlaceholderPulse(pulse: $pulse))
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
        // Only the next few: a frequent line would otherwise list a timetable's worth of times.
        let visible = group.visibleDepartures
        let hidden = Int(group.hiddenDepartureCount)
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
                        let times = DepartureTimes(departures: visible, now: timeline.date)
                        Text(times.primary)
                            .font(.title3.weight(.heavy))
                            .monospacedDigit()
                            .foregroundColor(first.isCancelled ? Palette.error : (times.primaryIsNow ? Palette.primary : Palette.textPrimary))
                    }
                }
                TimelineView(.periodic(from: .now, by: 1)) { timeline in
                    let secondary = DepartureTimes(departures: visible, now: timeline.date).secondary
                    if !secondary.isEmpty || hidden > 0 {
                        secondaryText(secondary, hidden: hidden)
                            .font(.body.weight(.medium))
                            .lineLimit(2) // a backstop: the capped times wrap at most once
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

/// The later times, then a muted "+K" for the departures left out. Concatenated `Text`s, so they
/// wrap as one line of text like Android's annotated string.
private func secondaryText(_ secondary: [String], hidden: Int) -> Text {
    var text = Text(secondary.joined(separator: "    ")).foregroundColor(Palette.textSecondary)
    if hidden > 0 {
        let more = Text(L("more_departures_hidden", hidden))
            .font(.subheadline.weight(.medium))
            .foregroundColor(Palette.textSecondary.opacity(0.6))
        text = secondary.isEmpty ? more : text + Text(verbatim: "    ") + more
    }
    return text
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
