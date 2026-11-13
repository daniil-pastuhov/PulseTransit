import Shared
import SwiftUI

/// The board's header, as on Android (BoardHeader.kt). On a phone: the clock with the buttons beside
/// it, and the weather in a card below. From 700 pt (iPad, a wide window) one row: a larger clock,
/// the weather, the buttons.
struct BoardHeader: View {
    let state: StopBoardViewModel.UiState
    let wide: Bool
    let onCycleDateStyle: () -> Void
    let onSearchRoute: () -> Void
    let onOpenSettings: () -> Void

    /// The header's width, for whether its buttons fold into one menu.
    @State private var width: CGFloat = .infinity

    /// Below this the time cannot sit beside both buttons, so they fold into a menu.
    private static let collapseActionsBelowWidth: CGFloat = 170

    var body: some View {
        let weather = state.showWeather ? state.weather : []
        Group {
            if wide {
                HStack(spacing: FjordTokens.space6) {
                    clock
                    if weather.isEmpty { Spacer(minLength: 0) } else { WeatherStrip(weather: weather, wide: true) }
                    actions(collapsed: false)
                }
            } else {
                VStack(spacing: FjordTokens.space2) {
                    HStack(spacing: FjordTokens.space2) {
                        clock
                        Spacer(minLength: 0)
                        actions(collapsed: width < Self.collapseActionsBelowWidth)
                    }
                    if !weather.isEmpty { WeatherStrip(weather: weather) }
                }
            }
        }
        .background(GeometryReader { geometry in
            Color.clear
                .onAppear { width = geometry.size.width }
                .onChange(of: geometry.size.width) { width = $0 }
        })
    }

    private var clock: some View {
        HeaderClock(state: state, wide: wide)
            .layoutPriority(1)
            .contentShape(Rectangle())
            .onTapGesture(perform: onCycleDateStyle)
            .accessibilityAddTraits(.isButton)
            .accessibilityHint(L("change_date_style"))
    }

    @ViewBuilder
    private func actions(collapsed: Bool) -> some View {
        Group {
            if collapsed {
                Menu {
                    Button(action: onSearchRoute) { Label(L("search_route"), systemImage: "magnifyingglass") }
                    Button(action: onOpenSettings) { Label(L("settings_title"), systemImage: "gearshape") }
                } label: {
                    Image(systemName: "ellipsis.circle").frame(width: 44, height: 44)
                }
                .accessibilityLabel(L("more_actions"))
            } else {
                HStack(spacing: 0) {
                    BoardActionButtons(onSearchRoute: onSearchRoute, onOpenSettings: onOpenSettings)
                }
            }
        }
        .font(.pulse(.title3))
        .foregroundColor(Palette.textPrimary)
        .fixedSize()
        .layoutPriority(2)
    }
}

/// Search and Settings: in the header, or as toolbar items in iPhone Duo's vertical bar. Refreshing
/// is automatic (a pull refreshes on demand), so it has no button. `shift` moves the glyphs for
/// burn-in protection in the bar, outside the board's own pixel shift.
struct BoardActionButtons: View {
    let onSearchRoute: () -> Void
    let onOpenSettings: () -> Void
    var shift = BurnInProtectionLevel.off

    var body: some View {
        Button(action: onSearchRoute) {
            Image(systemName: "magnifyingglass").frame(width: 44, height: 44).pixelShift(shift)
        }
        .accessibilityLabel(L("search_route"))
        Button(action: onOpenSettings) {
            Image(systemName: "gearshape").frame(width: 44, height: 44).pixelShift(shift)
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
    private static let columnButtonsHeight: CGFloat = 260
    private static let columnDateHeight: CGFloat = 80
    private static let columnClockHeight: CGFloat = 44
    private static let columnHourHeight: CGFloat = 95
    /// In the top row: what the status bar, the buttons and the date take, and an hour's width.
    private static let rowReservedWidth: CGFloat = 360
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
                            .font(.pulse(fixedSize: 17, weight: .bold))
                            .foregroundColor(Palette.primary)
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
                        .font(.pulse(.caption, weight: .semibold))
                        .foregroundColor(Palette.textPrimary)
                    let day = Text(state.currentDayOfMonth)
                        .font(.pulse(fixedSize: 18, weight: .bold))
                        .foregroundColor(Palette.textPrimary)
                    let month = Text(state.currentMonth)
                        .font(.pulse(.caption))
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

/// The time with the date in the user's chosen style; the weekday gets the emphasis, as in the design.
private struct HeaderClock: View {
    let state: StopBoardViewModel.UiState
    let wide: Bool

    private var timeSize: CGFloat { wide ? 64 : FjordTokens.typeClock.size }

    private func timeText(_ size: CGFloat) -> some View {
        let time = Text(state.currentTime)
            .font(.pulse(fixedSize: size, weight: .bold))
            .foregroundColor(Palette.primary)
        // When the device's clock reads otherwise, "Norway time" after the clock says whose time every time is.
        guard state.isNorwayTimeLabelled else { return time }
        return time + Text(" " + L("norway_time"))
            .font(.pulse(.caption, weight: .semibold))
            .foregroundColor(Palette.textSecondary)
    }

    /// "**Fri** 13 Nov": weekday strong, day and month muted.
    private var shortDate: Text {
        Text(state.currentWeekday).fontWeight(.semibold).foregroundColor(Palette.textPrimary)
            + Text(" \(state.currentDayOfMonth) \(state.currentMonth)").foregroundColor(Palette.textSecondary)
    }

    private var dateFont: Font { wide ? .pulse(role: FjordTokens.typeTitle, relativeTo: .title3) : .pulse(.subheadline) }

    var body: some View {
        // The date strings arrive with the first clock tick; until then show the time alone.
        let style = state.currentDayOfMonth.isEmpty ? DateDisplayStyle.hidden : state.dateStyle
        if #available(iOS 16, *) {
            // The chosen style where it fits, then the time alone, then a smaller time, so the date
            // gives way rather than pushing the buttons out of the header.
            ViewThatFits(in: .horizontal) {
                clock(style: style, size: timeSize).fixedSize()
                clock(style: .hidden, size: timeSize).fixedSize()
                clock(style: .hidden, size: timeSize * 0.7).fixedSize()
            }
        } else {
            clock(style: style, size: timeSize).fixedSize()
        }
    }

    @ViewBuilder
    private func clock(style: DateDisplayStyle, size: CGFloat) -> some View {
        switch style {
        case .hidden:
            timeText(size)
        case .beside:
            HStack(alignment: .firstTextBaseline, spacing: FjordTokens.space2) {
                timeText(size)
                shortDate.font(dateFont)
            }
        case .stacked:
            VStack(alignment: .leading, spacing: 0) {
                timeText(size)
                shortDate.font(dateFont)
            }
        case .tile:
            HStack(spacing: FjordTokens.space4) {
                timeText(size)
                VStack(spacing: 0) {
                    Text(state.currentMonth.uppercased())
                        .font(.pulse(.caption2, weight: .bold))
                        .foregroundColor(Palette.onPrimary)
                        .frame(maxWidth: .infinity)
                        .background(Palette.primary)
                    Text(state.currentDayOfMonth)
                        .font(.pulse(fixedSize: 22, weight: .bold))
                        .foregroundColor(Palette.textPrimary)
                }
                .frame(width: 48)
                .clipShape(RoundedRectangle(cornerRadius: FjordTokens.radiusSm))
                .overlay(RoundedRectangle(cornerRadius: FjordTokens.radiusSm).stroke(Palette.outlineVariant))
            }
        case .long:
            HStack(alignment: .firstTextBaseline, spacing: FjordTokens.space2) {
                timeText(size)
                (Text(state.currentWeekdayLong).fontWeight(.semibold).foregroundColor(Palette.textPrimary)
                    + Text(" \(state.currentDayOfMonth). \(state.currentMonthLong)").foregroundColor(Palette.textSecondary))
                    .font(wide ? dateFont : .pulse(.callout))
            }
        }
    }
}
