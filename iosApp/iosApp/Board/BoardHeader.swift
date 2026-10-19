import Shared
import SwiftUI

struct BoardHeader: View {
    let state: StopBoardViewModel.UiState
    let onCycleDateStyle: () -> Void
    let onRefresh: () -> Void
    let onSearchRoute: () -> Void
    let onOpenSettings: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            HeaderClock(state: state)
                // The clock keeps its natural size; the weather strip takes what is left.
                .fixedSize()
                .layoutPriority(1)
                .contentShape(Rectangle())
                .onTapGesture(perform: onCycleDateStyle)
                .accessibilityAddTraits(.isButton)
                .accessibilityHint(L("change_date_style"))
            WeatherStrip(weather: state.weather)
                .frame(maxWidth: .infinity)
            HStack(spacing: 8) {
                Button(action: onSearchRoute) {
                    Image(systemName: "magnifyingglass")
                }
                .accessibilityLabel(L("search_route"))
                Button(action: onRefresh) {
                    Image(systemName: "arrow.clockwise")
                }
                .accessibilityLabel(L("refresh"))
                Button(action: onOpenSettings) {
                    Image(systemName: "gearshape.fill")
                }
                .accessibilityLabel(L("settings_title"))
            }
            .font(.title2)
            .foregroundColor(Palette.textPrimary)
            .fixedSize()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Palette.surface.ignoresSafeArea(edges: .top))
    }
}

/// The time with the date in the user's chosen style; the day of month always gets the emphasis.
private struct HeaderClock: View {
    let state: StopBoardViewModel.UiState

    private var timeText: some View {
        Text(state.currentTime)
            .font(.system(size: 34, weight: .bold))
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
        switch style {
        case .hidden:
            timeText
        case .beside:
            HStack(spacing: 6) {
                timeText
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
                timeText
                (Text(state.currentWeekday + " ")
                    + Text(state.currentDayOfMonth).font(.system(size: 18, weight: .bold)).foregroundColor(Palette.textPrimary)
                    + Text(" " + state.currentMonth))
                    .font(.subheadline)
                    .foregroundColor(Palette.textSecondary)
            }
        case .tile:
            HStack(spacing: 16) {
                timeText
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
                timeText
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
