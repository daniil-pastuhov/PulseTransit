import Shared
import SwiftUI

/// One favourite stop's card, as on Android (StopBoardPanel.kt): its name and which directions it
/// shows, then a row per line and destination. `groups` is nil until the stop's first load finishes,
/// which shows skeleton rows under the name; `isLoading` on a stop that already has data shows a thin
/// bar instead, so a refresh never blanks a board that is being read. `isFailed` means the empty board
/// is unknown rather than empty, and says why instead of "no departures"; it retries by itself. With
/// `canLoadMore`, scrolling near the end of the list calls `onLoadMore` for the next page;
/// `isLoadingMore` is that page in flight, shown as a spinner at the end of the list only. Tapping a
/// row opens its line's details, which list every loaded departure the row leaves out.
struct StopPanel: View {
    let stop: StopPlace
    let groups: [DepartureGroup]?
    var directions: DirectionSummary?
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
    let onLoadMore: () -> Void
    /// The stops a departure's trip has ahead of it, for its details; empty when unavailable.
    let loadJourney: (Departure) async -> [JourneyStop]
    /// Replaces the stop's name and directions, as on-spot mode does with its stop picker.
    var header: AnyView?
    /// Told how many rows the list shows without scrolling, so the stop asks for enough to fill it.
    var onRowCapacity: (Int) -> Void = { _ in }
    /// Told when a departure's details open and close, so a board taking turns through its stops
    /// can hold the page they were opened from.
    var onDetailsOpenChange: (Bool) -> Void = { _ in }
    /// Shows a grip in the header, where the panel can be dragged to reorder (with a long press,
    /// as iOS lifts anything it drags).
    var showsDragHandle = false
    /// A pull on the list refreshes the board (iOS 16 and later; earlier, the board refreshes itself).
    var onPullToRefresh: (() async -> Void)?

    /// Whether the end-of-list sentinel is on screen; scrolling while it is asks for a page.
    @State private var isAtEnd = false
    /// The tapped row, captured at the tap. The details follow refreshes through `departuresLike`,
    /// which gathers the line's current departures and falls back to these once they have all gone.
    @State private var detailsGroup: DepartureGroup?
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center, spacing: FjordTokens.space2) {
                if let header {
                    header
                } else {
                    HStack(alignment: .firstTextBaseline, spacing: FjordTokens.space2) {
                        Text(stop.name)
                            .font(.pulse(role: FjordTokens.typeTitle, relativeTo: .title3))
                            .foregroundColor(Palette.textPrimary)
                            .lineLimit(1)
                            .layoutPriority(1)
                        if let subtitle = stopSubtitle(locality: stop.locality, directions: directions) {
                            Text(subtitle)
                                .font(.pulse(.subheadline))
                                .foregroundColor(Palette.textSecondary)
                                .lineLimit(1)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityElement(children: .combine)
                    .accessibilityAddTraits(.isHeader)
                }
                if showsDragHandle {
                    Image(systemName: "line.3.horizontal")
                        .foregroundColor(Palette.textSecondary)
                        .accessibilityLabel(L("drag_to_reorder"))
                }
            }
            .padding(.bottom, FjordTokens.space2)
            // Same height either way, so the list below doesn't jump when a refresh starts.
            if isLoading && groups != nil {
                RefreshBar().frame(height: 1)
            } else {
                Palette.outlineVariant.frame(height: 1)
            }
            // Overlaid, not stacked, so the skeleton and the list cross-fade in place.
            ZStack(alignment: .top) {
                if let groups = groups {
                    departures(groups)
                        .transition(.opacity)
                } else {
                    LoadingPlaceholder()
                        .transition(.opacity)
                }
            }
            .frame(maxHeight: .infinity, alignment: .top)
            .background(GeometryReader { geometry in
                Color.clear
                    .onAppear { onRowCapacity(Int(BoardLayout.shared.rowCapacity(listHeightDp: Float(geometry.size.height)))) }
                    .onChange(of: geometry.size.height) { onRowCapacity(Int(BoardLayout.shared.rowCapacity(listHeightDp: Float($0)))) }
            })
        }
        .animation(.easeInOut(duration: 0.3), value: groups == nil)
        .padding(.horizontal, FjordTokens.space4)
        .padding(.top, FjordTokens.space3)
        .padding(.bottom, FjordTokens.space2)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        // Light: a soft shadow lifts the card off the grey; dark: a hairline ring on black.
        .background(
            RoundedRectangle(cornerRadius: FjordTokens.radiusLg)
                .fill(Palette.surface)
                .shadow(color: colorScheme == .dark ? .clear : .black.opacity(0.08), radius: 2, y: 1)
        )
        .overlay(
            RoundedRectangle(cornerRadius: FjordTokens.radiusLg)
                .stroke(colorScheme == .dark ? Color.white.opacity(0.06) : .clear)
        )
        .onChange(of: detailsGroup != nil) { onDetailsOpenChange($0) }
        .sheet(isPresented: Binding(get: { detailsGroup != nil }, set: { if !$0 { detailsGroup = nil } })) {
            if let group = detailsGroup {
                DepartureDetailsView(
                    stopName: stop.name,
                    departures: group.departuresLike(groups: groups ?? []),
                    showDelays: showDelays,
                    showMessages: showMessages,
                    onFilterOut: onFilterOut,
                    loadJourney: loadJourney
                )
            }
        }
    }

    @ViewBuilder
    private func departures(_ groups: [DepartureGroup]) -> some View {
        if groups.isEmpty && isFailed {
            PanelMessage(
                symbol: loadFailureSymbol(isOnline: isOnline),
                title: L(isOnline ? "panel_failed_title" : "stale_no_internet_title"),
                detail: L(isOnline ? "panel_failed_body" : "panel_offline_body")
            )
        } else if groups.isEmpty {
            PanelMessage(
                symbol: "calendar.badge.exclamationmark",
                title: isExtended
                    ? L("no_departures_extended", Int(EnturRepositoryCompanion.shared.EXTENDED_DEPARTURE_WINDOW_DAYS))
                    : L("no_departures"),
                detail: isExtended
                    ? nil
                    : L("no_departures_body", Int(EnturRepositoryCompanion.shared.DEFAULT_DEPARTURE_WINDOW_SECONDS / 3600)),
                action: isExtended ? nil : (L("search_further_ahead"), onSearchFurtherAhead)
            )
        } else {
            list(groups)
        }
    }

    private func list(_ groups: [DepartureGroup]) -> some View {
        // Only the user's scrolling asks for a page, as on Android. A list that fits the card
        // never does: a single line folds every page into the same row, so it would keep fitting
        // and keep asking. Nor may a board left at its end on the wall grow on every refresh.
        GeometryReader { geometry in
            let isWide = BoardLayout.shared.isWideRow(panelWidthDp: Float(geometry.size.width))
            let showPlatform = BoardLayout.shared.showsPlatform(groups: groups)
            // A frequent line's departures get the panel's free rows; only its last folds, as on Android.
            let rows = PanelRows.shared.fill(
                groups: groups,
                rows: PanelRows.shared.capacity(listHeightDp: Float(geometry.size.height)),
                showMessages: showMessages
            )
            ScrollView {
                LazyVStack(spacing: 0) {
                    // Identified by content, so a refresh that shifts the list gives a changed group
                    // fresh row state. Entur can return identical-looking journeys, so the index
                    // is part of the id to keep it unique, as on Android.
                    ForEach(
                        Array(rows.enumerated()).map { (id: "\($0.offset)_\($0.element.rowID)", index: $0.offset, group: $0.element) },
                        id: \.id
                    ) { row in
                        if row.index > 0 { Palette.outlineVariant.frame(height: 1) }
                        DepartureGroupRow(
                            group: row.group,
                            showDelays: showDelays,
                            showMessages: showMessages,
                            isWide: isWide,
                            showPlatform: showPlatform,
                            onTap: { detailsGroup = row.group }
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
            .pullToRefresh(onPullToRefresh)
            // The content only moves under the user's finger (or its momentum): a refresh changes rows,
            // not where the list starts. So a move with the end in sight is a scroll to the end.
            .onPreferenceChange(ScrollOffsetKey.self) { _ in
                if isAtEnd && canLoadMore && !isLoading && !isLoadingMore { onLoadMore() }
            }
        }
    }

    private static let scrollSpace = "stopPanelScroll"
}

private extension View {
    /// `refreshable` where a scroll view supports it (iOS 16); nothing before.
    @ViewBuilder
    func pullToRefresh(_ action: (() async -> Void)?) -> some View {
        if #available(iOS 16, *), let action {
            refreshable { await action() }
        } else {
            self
        }
    }
}

/// "Oslo · all directions", "Platforms 2–4" or "3 of 7 directions"; nil with nothing to say.
private func stopSubtitle(locality: String?, directions: DirectionSummary?) -> String? {
    guard let directions else { return locality }
    switch onEnum(of: directions) {
    case .all:
        return [locality, L("directions_all")].compactMap { $0 }.joined(separator: " · ")
    case let .platforms(platforms):
        return platforms.count == 1 ? L("platform_label", platforms.codes) : L("directions_platforms", platforms.codes)
    case let .some(some):
        if let total = some.total { return L("directions_some", Int(some.count), total.intValue) }
        return L("directions_count", Int(some.count))
    }
}

/// Where a list's content starts within its scroll view, which changes only as it is scrolled.
private struct ScrollOffsetKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

/// A panel's own message in place of its rows: a symbol, a title, a line of explanation, an action.
private struct PanelMessage: View {
    let symbol: String
    let title: String
    var detail: String?
    var action: (String, () -> Void)?

    var body: some View {
        VStack(spacing: FjordTokens.space2) {
            Spacer(minLength: 0)
            Image(systemName: symbol)
                .font(.system(size: 28))
                .foregroundColor(Palette.textSecondary)
                .accessibilityHidden(true)
            Text(title)
                .font(.pulse(.callout, weight: .semibold))
                .foregroundColor(Palette.textPrimary)
                .multilineTextAlignment(.center)
            if let detail {
                Text(detail)
                    .font(.pulse(.subheadline))
                    .foregroundColor(Palette.textSecondary)
                    .multilineTextAlignment(.center)
            }
            if let action {
                Button(action.0, action: action.1)
                    .font(.pulse(.subheadline, weight: .semibold))
                    .buttonStyle(.borderless)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, FjordTokens.space4)
        .frame(maxWidth: .infinity)
    }
}

/// Skeleton rows while a stop's first departures load: no spinner, no label (the name says which).
private struct LoadingPlaceholder: View {
    @State private var pulse = false

    var body: some View {
        VStack(spacing: 0) {
            ForEach(0 ..< 5, id: \.self) { index in
                if index > 0 { Palette.outlineVariant.frame(height: 1) }
                HStack(spacing: FjordTokens.space2) {
                    PlaceholderBlock(width: FjordTokens.pillW, height: 28, pulse: pulse)
                    PlaceholderBlock(height: 14, pulse: pulse)
                    PlaceholderBlock(width: 48, height: 18, pulse: pulse)
                }
                .frame(height: FjordTokens.row)
            }
            Spacer(minLength: 0)
        }
        .modifier(PlaceholderPulse(pulse: $pulse))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L("loading_departures"))
    }
}

/// One line to one destination: pill, mode, destination with a detail line under it, then the delay
/// and the time of its next departure. Later departures are in the row's details.
private struct DepartureGroupRow: View {
    let group: DepartureGroup
    let showDelays: Bool
    let showMessages: Bool
    var isWide = false
    var showPlatform = false
    let onTap: () -> Void

    var body: some View {
        if let first = group.departures.first {
            let delayed = !first.isCancelled && showDelays && first.delayMinutes > 0
            let notices = showMessages ? group.departures.flatMap(\.notices).uniqued() : []
            let sub = subLines(first)
            HStack(spacing: FjordTokens.space2) {
                LinePill(line: first.line, mode: first.transportMode, cancelled: first.isCancelled)
                    .opacity(first.isCancelled ? 0.55 : 1)
                TransportIcon(mode: first.transportMode).font(.system(size: 18)).frame(width: 20)
                VStack(alignment: .leading, spacing: 0) {
                    Text(first.destination)
                        .font(.pulse(size: isWide ? 19 : 16, weight: .medium, relativeTo: .body))
                        .strikethrough(first.isCancelled)
                        .foregroundColor(first.isCancelled ? Palette.textSecondary : Palette.textPrimary)
                        .lineLimit(1)
                    if !sub.isEmpty {
                        Text(sub.joined(separator: " · "))
                            .font(.pulse(.caption))
                            .foregroundColor(Palette.textSecondary)
                            .lineLimit(1)
                    }
                    ForEach(notices, id: \.self) { notice in
                        Label(notice, systemImage: "info.circle")
                            .font(.pulse(.caption))
                            .foregroundColor(Palette.textPrimary)
                            .lineLimit(2)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if delayed {
                    DelayChip(minutes: Int(first.delayMinutes))
                }
                // Ticks on its own: the shared state only changes about once a minute, and a row
                // whose input is the same Departure object would not be redrawn at all.
                TimelineView(.periodic(from: .now, by: 1)) { timeline in
                    DepartureTimeText(departure: first, delayed: delayed, isWide: isWide, now: timeline.date)
                }
            }
            .padding(.vertical, FjordTokens.space1)
            .frame(minHeight: FjordTokens.row)
            .contentShape(Rectangle())
            .onTapGesture(perform: onTap)
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isButton)
        }
    }

    /// "Platform 4", "Every ~8 min", "Every ~5 min · +4 more": what else to know about the row.
    private func subLines(_ first: Departure) -> [String] {
        var lines: [String] = []
        if showPlatform, let platform = first.platform { lines.append(L("platform_label", platform)) }
        if let headway = group.headwayMinutes { lines.append(L("details_every", headway.intValue)) }
        if let more = group.moreDepartures { lines.append(L("more_departures_hidden", more.intValue)) }
        return lines
    }
}

/// The time column: "Now", "4 min" or "19:56"; "Cancelled"; and for a late departure shown as a
/// clock time, the planned time struck through before the new one in the late colour.
private struct DepartureTimeText: View {
    let departure: Departure
    let delayed: Bool
    let isWide: Bool
    let now: Date

    var body: some View {
        if departure.isCancelled {
            Text(L("cancelled"))
                .font(.pulse(size: isWide ? 17 : 14, weight: .semibold, relativeTo: .subheadline))
                .foregroundColor(Palette.error)
                .lineLimit(1)
                .fixedSize()
        } else {
            let nowMillis = Int64(now.timeIntervalSince1970 * 1000)
            let countdown = DepartureCountdownKt.departureCountdown(expectedTime: departure.expectedTime, nowMillis: nowMillis)
            let time = DepartureTimes.text(countdown)
            let font = Font.pulse(size: isWide ? 24 : FjordTokens.typeCountdown.size, weight: .bold, relativeTo: .title3)
            Group {
                if delayed && countdown is DepartureCountdownClock {
                    Text(DepartureTimes.clock(for: departure.aimedTime))
                        .font(.pulse(.subheadline))
                        .strikethrough()
                        .foregroundColor(Palette.textSecondary)
                        + Text(" ")
                        + Text(time).font(font).foregroundColor(Palette.late)
                } else {
                    Text(time)
                        .font(font)
                        .foregroundColor(countdown is DepartureCountdownNow ? Palette.now : Palette.textPrimary)
                }
            }
            .lineLimit(1)
            .fixedSize()
        }
    }
}

/// A departure's time texts, worded like the Android board.
enum DepartureTimes {
    /// The departure's "HH:mm", or empty when its timestamp cannot be read.
    static func clock(for isoTime: String) -> String {
        guard let date = isoFormatter.date(from: isoTime) else { return "" }
        return clockFormatter.string(from: date)
    }

    /// "Now", "5 min" up to the shared cutoff, then the clock time, marked "⁺1" beyond today.
    static func text(_ countdown: DepartureCountdown) -> String {
        switch onEnum(of: countdown) {
        case .now:
            return L("now")
        case let .minutes(minutes):
            return L("countdown_minutes", Int(minutes.minutes))
        case let .clock(clock):
            let time = clockFormatter.string(from: Date(timeIntervalSince1970: TimeInterval(clock.epochMillis) / 1000))
            // Beyond today only when the search was widened; marked like a printed timetable.
            return clock.dayOffset > 0 ? "\(time)⁺\(clock.dayOffset)" : time
        case .unknown:
            return "?"
        }
    }

    private static let isoFormatter = ISO8601DateFormatter()

    /// 24-hour in Norway time, like the Android board, whatever the locale's default.
    private static let clockFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        formatter.timeZone = .norway
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
