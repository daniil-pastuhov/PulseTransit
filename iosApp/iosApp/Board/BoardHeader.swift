import Shared
import SwiftUI

struct BoardHeader: View {
    let state: StopBoardViewModel.UiState
    let onCycleDateStyle: () -> Void
    let onRefresh: () -> Void
    let onSearchRoute: () -> Void
    let onOpenSettings: () -> Void

    /// The header's width, for whether its buttons fold into one menu.
    @State private var width: CGFloat = .infinity

    /// Below this the time cannot sit beside all three buttons, so they fold into a menu.
    private static let collapseActionsBelowWidth: CGFloat = 250

    var body: some View {
        let collapseActions = width < Self.collapseActionsBelowWidth
        HStack(spacing: 12) {
            // The buttons keep their room and the clock gets what they leave, stepping its date
            // down to fit; the weather strip takes what is left after both.
            HeaderClock(state: state)
                .layoutPriority(1)
                .contentShape(Rectangle())
                .onTapGesture(perform: onCycleDateStyle)
                .accessibilityAddTraits(.isButton)
                .accessibilityHint(L("change_date_style"))
            if state.showWeather {
                WeatherStrip(weather: state.weather)
                    .frame(maxWidth: .infinity)
            } else {
                Spacer(minLength: 0)
            }
            Group {
                if collapseActions {
                    Menu {
                        Button(action: onSearchRoute) { Label(L("search_route"), systemImage: "magnifyingglass") }
                        Button(action: onRefresh) { Label(L("refresh"), systemImage: "arrow.clockwise") }
                        Button(action: onOpenSettings) { Label(L("settings_title"), systemImage: "gearshape.fill") }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                    .accessibilityLabel(L("more_actions"))
                } else {
                    HStack(spacing: 8) {
                        BoardActionButtons(onRefresh: onRefresh, onSearchRoute: onSearchRoute, onOpenSettings: onOpenSettings)
                    }
                }
            }
            .font(.title2)
            .foregroundColor(Palette.textPrimary)
            .fixedSize()
            .layoutPriority(2)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(GeometryReader { geometry in
            Color.clear
                .onAppear { width = geometry.size.width }
                .onChange(of: geometry.size.width) { width = $0 }
        })
        .background(Palette.surface.ignoresSafeArea(edges: .top))
    }
}

/// Search, refresh and Settings: in the header, or as toolbar items in iPhone Duo's vertical bar.
/// `shift` moves the glyphs for burn-in protection in the bar, outside the board's own pixel shift.
struct BoardActionButtons: View {
    let onRefresh: () -> Void
    let onSearchRoute: () -> Void
    let onOpenSettings: () -> Void
    var shift = BurnInProtectionLevel.off

    var body: some View {
        Button(action: onSearchRoute) {
            Image(systemName: "magnifyingglass").pixelShift(shift)
        }
        .accessibilityLabel(L("search_route"))
        Button(action: onRefresh) {
            Image(systemName: "arrow.clockwise").pixelShift(shift)
        }
        .accessibilityLabel(L("refresh"))
        Button(action: onOpenSettings) {
            Image(systemName: "gearshape.fill").pixelShift(shift)
        }
        .accessibilityLabel(L("settings_title"))
    }
}

/// The board's header as iPhone Duo bar items, one each: in the side column, the date, then the
/// forecast an hour at a time, under the buttons; in the top row, the date and the forecast side
/// by side, left of the buttons and the status bar. The clock only with `showsClock`: otherwise
/// the status bar in the same bar shows the time, larger than a bar item leaves room for, but iOS
/// hides it in landscape (folded, on the outer display). A bar item cannot see the room it has,
/// and an overflowing bar collapses into one empty capsule, buttons and all, so `displaySize`
/// (the board's) sets what fits; the rest is left out. Each item shifts for burn-in on its own,
/// the bar being outside the board's pixel shift.
enum BarHeader {
    /// In the column: what the status bar and the buttons take, and the slots of the date, the
    /// clock and an hour.
    private static let columnButtonsHeight: CGFloat = 320
    private static let columnDateHeight: CGFloat = 80
    private static let columnClockHeight: CGFloat = 44
    private static let columnHourHeight: CGFloat = 95
    /// In the top row: what the status bar, the buttons and the date take, and an hour's width.
    private static let rowReservedWidth: CGFloat = 400
    private static let rowHourWidth: CGFloat = 64

    static func items(
        placement: BarPlacement,
        state: StopBoardViewModel.UiState,
        displaySize: CGSize,
        showsClock: Bool,
        onCycleDateStyle: @escaping () -> Void
    ) -> [AnyView] {
        switch placement {
        case .none:
            return []
        case .statusRow:
            var items = [date(state: state, axis: .horizontal, onCycleDateStyle: onCycleDateStyle)]
            let count = min(6, Int((displaySize.width - rowReservedWidth) / rowHourWidth))
            if state.showWeather && count > 0 {
                items += WeatherColumn.hours(state.weather, count: count, compact: true)
                    .map { AnyView($0.pixelShift(state.burnInLevel)) }
            }
            return items
        case .vertical:
            var items: [AnyView] = []
            var room = displaySize.height - columnButtonsHeight
            if showsClock && room >= columnClockHeight {
                room -= columnClockHeight
                // Cycles the date style when tapped, as the header's clock does, so the style stays
                // reachable where the column has no room left for the date.
                items.append(AnyView(
                    Button(action: onCycleDateStyle) {
                        Text(state.currentTime)
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(Palette.primary)
                            .monospacedDigit()
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                            .fixedSize()
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint(L("change_date_style"))
                    .pixelShift(state.burnInLevel)
                ))
            }
            guard room >= columnDateHeight else { return items }
            room -= columnDateHeight
            items.append(date(state: state, axis: .vertical, onCycleDateStyle: onCycleDateStyle))
            let count = min(6, Int(room / columnHourHeight))
            if state.showWeather && count > 0 {
                items += WeatherColumn.hours(state.weather, count: count, compact: false)
                    .map { AnyView($0.pixelShift(state.burnInLevel)) }
            }
            return items
        }
    }

    /// The weekday, day and month, stacked or in a line. Tapping cycles the style as the header's
    /// clock does, the only place it is set; a hidden date leaves a calendar to tap, so it can
    /// always be brought back.
    private static func date(
        state: StopBoardViewModel.UiState,
        axis: Axis,
        onCycleDateStyle: @escaping () -> Void
    ) -> AnyView {
        AnyView(
            Button(action: onCycleDateStyle) {
                if state.dateStyle == .hidden || state.currentDayOfMonth.isEmpty {
                    Image(systemName: "calendar")
                        .foregroundColor(Palette.textSecondary)
                } else {
                    let weekday = Text(state.currentWeekday)
                        .font(.caption2)
                        .foregroundColor(Palette.textSecondary)
                    let day = Text(state.currentDayOfMonth)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(Palette.textPrimary)
                    let month = Text(state.currentMonth)
                        .font(.caption2)
                        .foregroundColor(Palette.textSecondary)
                    Group {
                        if axis == .vertical {
                            VStack(spacing: 0) { weekday; day; month }
                        } else {
                            HStack(alignment: .firstTextBaseline, spacing: 4) { weekday; day; month }
                        }
                    }
                    .lineLimit(1)
                    .fixedSize()
                }
            }
            .buttonStyle(.plain)
            .accessibilityHint(L("change_date_style"))
            .pixelShift(state.burnInLevel)
        )
    }
}

/// The time with the date in the user's chosen style; the day of month always gets the emphasis.
private struct HeaderClock: View {
    let state: StopBoardViewModel.UiState

    private func timeText(_ size: CGFloat) -> some View {
        Text(state.currentTime)
            .font(.system(size: size, weight: .bold))
            .foregroundColor(Palette.primary)
            .monospacedDigit()
    }

    private func dayText(size: CGFloat) -> some View {
        Text(state.currentDayOfMonth)
            .font(.system(size: size, weight: .bold))
            .foregroundColor(Palette.textPrimary)
    }

    var body: some View {
        // The date strings arrive with the first clock tick; until then show the time alone.
        let style = state.currentDayOfMonth.isEmpty ? DateDisplayStyle.hidden : state.dateStyle
        if #available(iOS 16, *) {
            // The chosen style where it fits, then the time alone, then a smaller time, so the date
            // gives way rather than pushing the buttons out of the header.
            ViewThatFits(in: .horizontal) {
                clock(style: style).fixedSize()
                clock(style: .hidden).fixedSize()
                clock(style: .hidden, timeSize: 22).fixedSize()
            }
        } else {
            clock(style: style).fixedSize()
        }
    }

    @ViewBuilder
    private func clock(style: DateDisplayStyle, timeSize: CGFloat = 34) -> some View {
        switch style {
        case .hidden:
            timeText(timeSize)
        case .beside:
            HStack(spacing: 6) {
                timeText(timeSize)
                dayText(size: 34).padding(.leading, 10)
                VStack(alignment: .leading) {
                    Text(state.currentWeekday)
                    Text(state.currentMonth)
                }
                .font(.caption)
                .foregroundColor(Palette.textSecondary)
            }
        case .stacked:
            VStack(alignment: .leading, spacing: 0) {
                timeText(timeSize)
                (Text(state.currentWeekday + " ")
                    + Text(state.currentDayOfMonth).font(.system(size: 18, weight: .bold)).foregroundColor(Palette.textPrimary)
                    + Text(" " + state.currentMonth))
                    .font(.subheadline)
                    .foregroundColor(Palette.textSecondary)
            }
        case .tile:
            HStack(spacing: 16) {
                timeText(timeSize)
                VStack(spacing: 0) {
                    Text(state.currentMonth.uppercased())
                        .font(.caption2.bold())
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .background(Palette.primary)
                    dayText(size: 22)
                }
                .frame(width: 48)
                .clipShape(RoundedRectangle(cornerRadius: 6))
                .overlay(RoundedRectangle(cornerRadius: 6).stroke(Palette.textSecondary.opacity(0.5)))
            }
        case .long:
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                timeText(timeSize)
                Text(state.currentWeekdayLong)
                    .font(.headline)
                    .foregroundColor(Palette.textSecondary)
                    .padding(.leading, 10)
                dayText(size: 26)
                Text(state.currentMonthLong)
                    .font(.headline)
                    .foregroundColor(Palette.textSecondary)
            }
        }
    }
}
