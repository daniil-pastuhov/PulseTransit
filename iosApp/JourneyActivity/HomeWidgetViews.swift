import Shared
import SwiftUI
import WidgetKit

// The home-screen widgets' layouts, by size, the counterparts of Android's widget composables.

// MARK: - Pieces

extension View {
    /// The widget's background: the system's container background from iOS 17 (which it requires),
    /// a plain one before.
    @ViewBuilder func widgetBackground(_ color: Color = Palette.surface) -> some View {
        if #available(iOSApplicationExtension 17.0, *) {
            containerBackground(color, for: .widget)
        } else {
            padding().background(color)
        }
    }
}

/// The widget's title with its kind's symbol, as Android's WidgetHeader.
private struct WidgetHeader: View {
    let title: String
    let symbol: String

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: symbol)
                .font(.pulse(.caption, weight: .semibold))
                .foregroundColor(Palette.primary)
                .accessibilityHidden(true)
            Text(title)
                .font(.pulse(.subheadline, weight: .semibold))
                .foregroundColor(Palette.textPrimary)
                .lineLimit(1)
        }
    }
}

/// A departure's time: realtime in the "now" accent, cancelled struck through.
private struct TimeText: View {
    let time: WidgetTime
    var emphasized = false

    var body: some View {
        Text(time.text)
            .font(emphasized ? .pulse(.subheadline, weight: .bold) : .pulse(.footnote, weight: .medium))
            .strikethrough(time.isCancelled)
            .foregroundColor(time.isCancelled ? Palette.textSecondary : (time.isRealtime ? Palette.now : Palette.textPrimary))
            .lineLimit(1)
            .fixedSize()
    }
}

/// A message in place of the data (T5).
private struct MessageBlock: View {
    let message: WidgetMessage
    var compact = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Image(systemName: message.symbol)
                .font(.pulse(compact ? .body : .title3))
                .foregroundColor(Palette.primary)
                .accessibilityHidden(true)
            Text(message.title)
                .font(.pulse(.subheadline, weight: .semibold))
                .foregroundColor(Palette.textPrimary)
                .lineLimit(compact ? 3 : 2)
            if let body = message.body, !compact {
                Text(body)
                    .font(.pulse(.caption))
                    .foregroundColor(Palette.textSecondary)
                    .lineLimit(2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The footer: when the data is from, in the warning colour when it is not live.
private struct StatusLine: View {
    let status: WidgetStatus

    var body: some View {
        HStack(spacing: 4) {
            if status.isWarning {
                Image(systemName: "icloud.slash").accessibilityHidden(true)
            }
            Text(status.text).lineLimit(1)
        }
        .font(.pulse(.caption2))
        .foregroundColor(status.isWarning ? Palette.onWarningContainer : Palette.textSecondary)
    }
}

/// A row on one line: pill, destination, platform when there is room, and the next times.
private struct DepartureLine: View {
    let row: WidgetRow
    let times: Int
    var showPlatform = false

    var body: some View {
        HStack(spacing: 8) {
            LinePill(line: row.line, mode: row.mode, cancelled: row.times.first?.isCancelled == true, small: true, minWidth: 40)
            Text(row.destination)
                .font(.pulse(.footnote))
                .foregroundColor(Palette.textPrimary)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            if showPlatform, let platform = row.platform {
                Text(platform)
                    .font(.pulse(.caption))
                    .foregroundColor(Palette.textSecondary)
                    .lineLimit(1)
            }
            ForEach(Array(row.times.prefix(times).enumerated()), id: \.offset) { index, time in
                TimeText(time: time, emphasized: index == 0)
            }
        }
        .accessibilityElement(children: .combine)
    }
}

/// A small widget's row: the pill and the next time, the destination under them.
private struct StackedDeparture: View {
    let row: WidgetRow

    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            HStack {
                LinePill(line: row.line, mode: row.mode, cancelled: row.times.first?.isCancelled == true, small: true)
                Spacer(minLength: 4)
                if let time = row.times.first { TimeText(time: time, emphasized: true) }
            }
            Text(row.destination)
                .font(.pulse(.caption))
                .foregroundColor(Palette.textSecondary)
                .lineLimit(1)
        }
        .accessibilityElement(children: .combine)
    }
}

// MARK: - At a glance

/// The board's clock in Norway time, with the date; "Norway time" when the device's clock reads otherwise.
private struct GlanceClock: View {
    let date: Date
    let size: CGFloat

    private static let time: DateFormatter = formatter("HH:mm")
    private static let day: DateFormatter = formatter("EEE d MMM")

    /// The board's own patterns (as `SystemClockFormatter`), in the user's language and Norway's time.
    private static func formatter(_ pattern: String) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = .current
        formatter.dateFormat = pattern
        formatter.timeZone = .norway
        return formatter
    }

    private var differs: Bool { TimeZone.current.secondsFromGMT(for: date) != TimeZone.norway.secondsFromGMT(for: date) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(Self.time.string(from: date))
                .font(.pulse(fixedSize: size, weight: .bold))
                .foregroundColor(Palette.primary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(differs ? "\(Self.day.string(from: date)) · \(L("norway_time"))" : Self.day.string(from: date))
                .font(.pulse(.caption, weight: .medium))
                .foregroundColor(Palette.textSecondary)
                .lineLimit(1)
        }
    }
}

private struct GlanceWeather: View {
    let content: GlanceContent?

    var body: some View {
        if let symbol = content?.weatherSymbol, let temperature = content?.temperature {
            HStack(spacing: 4) {
                Image(systemName: symbol).foregroundColor(Palette.textPrimary).accessibilityHidden(true)
                Text(temperature).font(.pulse(.headline, weight: .bold)).foregroundColor(Palette.textPrimary)
            }
        }
    }
}

struct GlanceWidgetView: View {
    let entry: HomeWidgetEntry<GlanceContent>
    @Environment(\.widgetFamily) private var family

    var body: some View {
        content.widgetBackground()
    }

    @ViewBuilder private var content: some View {
        switch family {
        case .accessoryRectangular: accessory
        case .systemSmall: small
        default: wide
        }
    }

    /// The Lock Screen strip: the next departure, or the date.
    private var accessory: some View {
        VStack(alignment: .leading, spacing: 1) {
            if let row = entry.content?.rows.first, let time = row.times.first {
                Text("\(row.line) \(row.destination)").font(.pulse(.headline, weight: .semibold)).lineLimit(1)
                Text(time.text).font(.pulse(.title3, weight: .bold))
            } else {
                GlanceClock(date: entry.date, size: 24)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// 2×2: the clock, the weather beside the date, and the next departure in two lines.
    private var small: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                GlanceClock(date: entry.date, size: 34)
                Spacer(minLength: 0)
            }
            GlanceWeather(content: entry.content)
            Spacer(minLength: 0)
            next(compact: true)
            if let status = entry.content?.status { StatusLine(status: status) }
        }
    }

    /// 4×2 and 4×4: the clock beside the weather, the next departure on a tinted container, and at
    /// 4×4 the departures after it.
    private var wide: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                GlanceClock(date: entry.date, size: 44)
                Spacer()
                GlanceWeather(content: entry.content)
            }
            next(compact: false)
                .padding(10)
                .background(RoundedRectangle(cornerRadius: FjordTokens.radiusMd).fill(Palette.primaryContainer))
            if family == .systemLarge, let rows = entry.content?.rows.dropFirst() {
                ForEach(Array(rows.prefix(6))) { row in
                    DepartureLine(row: row, times: 1)
                }
            }
            Spacer(minLength: 0)
            if let status = entry.content?.status { StatusLine(status: status) }
        }
    }

    @ViewBuilder private func next(compact: Bool) -> some View {
        if let message = entry.content?.message {
            MessageBlock(message: message, compact: true)
        } else if let row = entry.content?.rows.first {
            VStack(alignment: .leading, spacing: 2) {
                if !compact, let stop = entry.content?.stopName {
                    Text(stop).font(.pulse(.caption, weight: .semibold)).foregroundColor(Palette.onPrimaryContainer).lineLimit(1)
                }
                if compact {
                    StackedDeparture(row: row)
                } else {
                    DepartureLine(row: row, times: 1)
                }
            }
        }
    }
}

// MARK: - Departures

struct DeparturesWidgetView: View {
    let entry: HomeWidgetEntry<DeparturesContent>
    @Environment(\.widgetFamily) private var family

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            WidgetHeader(title: entry.content?.title ?? "PulseTransit", symbol: "bus.fill")
            if let message = entry.content?.message {
                MessageBlock(message: message, compact: family == .systemSmall)
            } else if let rows = entry.content?.rows {
                switch family {
                case .systemSmall:
                    ForEach(Array(rows.prefix(3))) { StackedDeparture(row: $0) }
                case .systemMedium:
                    ForEach(Array(rows.prefix(4))) { DepartureLine(row: $0, times: 2, showPlatform: false) }
                default:
                    ForEach(Array(rows.prefix(8))) { row in
                        DepartureLine(row: row, times: 3, showPlatform: true)
                        if row != rows.prefix(8).last { Divider() }
                    }
                }
            }
            Spacer(minLength: 0)
            // At 2×2 the footer gives way unless the data is not live: then it is the only hint.
            if let status = entry.content?.status, family != .systemSmall || status.isWarning {
                StatusLine(status: status)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .widgetBackground()
    }
}

// MARK: - Fastest route

struct RouteWidgetView: View {
    let entry: HomeWidgetEntry<RouteContent>
    @Environment(\.widgetFamily) private var family

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            WidgetHeader(title: entry.content?.title ?? "PulseTransit", symbol: "arrow.triangle.turn.up.right.diamond.fill")
            if let content = entry.content {
                if let message = content.message {
                    MessageBlock(message: message, compact: family == .systemSmall)
                } else {
                    route(content)
                }
                Spacer(minLength: 0)
                if let status = content.status, family != .systemSmall || status.isWarning {
                    StatusLine(status: status)
                }
            } else {
                Spacer(minLength: 0)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .widgetBackground()
    }

    @ViewBuilder private func route(_ content: RouteContent) -> some View {
        if let leave = content.leaveAtText {
            Text(leave)
                .font(.pulse(family == .systemSmall ? .title3 : .title2, weight: .bold))
                .foregroundColor(Palette.primary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        if let walkOnly = content.walkOnly {
            Label(walkOnly, systemImage: "figure.walk").font(.pulse(.footnote)).foregroundColor(Palette.textPrimary)
        } else {
            legs(content.legs)
        }
        let departs = family == .systemSmall ? content.departs : (content.departsWithPlatform ?? content.departs)
        if let departs {
            Text(departs).font(.pulse(.caption)).foregroundColor(Palette.textSecondary).lineLimit(1)
        }
        if let arrives = content.arrives {
            Text([arrives, family == .systemSmall ? nil : content.duration].compactMap { $0 }.joined(separator: " · "))
                .font(.pulse(.caption))
                .foregroundColor(Palette.textSecondary)
                .lineLimit(1)
        }
    }

    /// The legs in order: line pills, and walks with their minutes, joined by arrows.
    private func legs(_ legs: [RouteLegItem]) -> some View {
        HStack(spacing: 4) {
            ForEach(Array(legs.prefix(family == .systemSmall ? 3 : 5).enumerated()), id: \.offset) { index, leg in
                if index > 0 {
                    Image(systemName: "chevron.right").font(.pulse(.caption2)).foregroundColor(Palette.textSecondary).accessibilityHidden(true)
                }
                if let line = leg.line {
                    LinePill(line: line, mode: leg.mode, small: true)
                } else if let walk = leg.walk {
                    HStack(spacing: 2) {
                        Image(systemName: "figure.walk").accessibilityHidden(true)
                        Text(walk).lineLimit(1)
                    }
                    .font(.pulse(.caption))
                    .foregroundColor(Palette.textSecondary)
                }
            }
        }
    }
}
