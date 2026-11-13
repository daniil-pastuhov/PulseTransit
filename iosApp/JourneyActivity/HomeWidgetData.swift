import Foundation
import Shared
import WidgetKit

// What the home-screen widgets show, the counterpart of Android's WidgetViews.kt: loaded by the shared
// BoardWidgetSource (through PulseTransitWidgets, in this extension's own process) and turned into
// plain values with every string resolved, so the views only lay them out. Times are Norway's, as
// everywhere in the app.

/// How often a widget asks for fresh data; the system may stretch it. Android refreshes every 15 min.
let widgetReloadInterval: TimeInterval = 15 * 60

/// One departure time in a row.
struct WidgetTime: Hashable {
    let date: Date
    let text: String
    let isRealtime: Bool
    let isCancelled: Bool
}

/// One line-to-destination row with its next times, soonest first.
struct WidgetRow: Hashable, Identifiable {
    let line: String
    let mode: String?
    let destination: String
    let platform: String?
    let times: [WidgetTime]

    var id: String { "\(line)|\(destination)" }

    /// The row as it stands at [date]: times already gone dropped, nil when none is left.
    func at(_ date: Date) -> WidgetRow? {
        let left = times.filter { $0.date >= date }
        guard !left.isEmpty else { return nil }
        return WidgetRow(line: line, mode: mode, destination: destination, platform: platform, times: left)
    }
}

/// A message in place of a widget's data (T5): an SF Symbol, what happened, what to do.
struct WidgetMessage: Hashable {
    let symbol: String
    let title: String
    var body: String?
}

/// The footer: when the data is from; a warning when it is not live.
struct WidgetStatus: Hashable {
    let text: String
    let isWarning: Bool
}

struct DeparturesContent: Hashable {
    let title: String
    var rows: [WidgetRow] = []
    var message: WidgetMessage?
    var status: WidgetStatus?
}

struct RouteLegItem: Hashable {
    let line: String?
    let mode: String?
    /// "6 min" for a walk.
    let walk: String?
}

struct RouteContent: Hashable {
    let title: String
    var leaveAt: Date?
    var leaveAtText: String?
    var legs: [RouteLegItem] = []
    var walkOnly: String?
    var departs: String?
    var departsWithPlatform: String?
    var arrives: String?
    var duration: String?
    var message: WidgetMessage?
    var status: WidgetStatus?
}

struct GlanceContent: Hashable {
    var weatherSymbol: String?
    var temperature: String?
    var stopName: String?
    var rows: [WidgetRow] = []
    var message: WidgetMessage?
    /// Only when the data is not live: the clock already says the widget is current.
    var status: WidgetStatus?
}

/// What a widget draws at one moment of its timeline.
struct HomeWidgetEntry<Content: Hashable>: TimelineEntry {
    let date: Date
    let content: Content?
}

// MARK: - Loading

enum HomeWidgetLoader {
    static let maxRows: Int32 = 8
    static let maxTimes: Int32 = 3

    static func departures(stopId: String?) async -> DeparturesContent {
        guard let state = try? await PulseTransitWidgets.shared.departures(stopId: stopId, maxRows: maxRows, timesPerRow: maxTimes) else {
            return DeparturesContent(title: appName, message: unreachable(L("widget_retry_soon")))
        }
        switch onEnum(of: state) {
        case .noStops:
            return DeparturesContent(
                title: appName,
                message: WidgetMessage(symbol: "mappin.and.ellipse", title: L("widget_no_stops"), body: L("widget_no_stops_body"))
            )
        case let .board(board):
            let live = board.freshness.staleSinceMillis == nil
            let rows = board.rows.map { row($0, live: live) }
            var message: WidgetMessage?
            if rows.isEmpty {
                message = board.freshness.failed
                    ? unreachable(L("widget_retry_soon"))
                    : WidgetMessage(symbol: "clock", title: L("no_departures"))
            }
            return DeparturesContent(title: board.stop.name, rows: rows, message: message, status: status(board.freshness))
        }
    }

    static func route(destinationId: String?) async -> RouteContent {
        guard let state = try? await PulseTransitWidgets.shared.route(destinationId: destinationId) else {
            return RouteContent(title: appName, message: unreachable(L("widget_retry_soon")))
        }
        switch onEnum(of: state) {
        case .noDestinations:
            return RouteContent(
                title: appName,
                message: WidgetMessage(symbol: "mappin.and.ellipse", title: L("widget_no_destinations"), body: L("widget_no_destinations_body"))
            )
        case let .noOrigin(state):
            return RouteContent(
                title: L("trip_details_title", state.destination.name),
                message: WidgetMessage(symbol: "location.slash", title: L("widget_no_origin"))
            )
        case let .atDestination(state):
            return RouteContent(
                title: L("trip_details_title", state.destination.name),
                message: WidgetMessage(symbol: "checkmark.circle", title: L("widget_at_destination", state.destination.name))
            )
        case let .route(state):
            let title = L("trip_details_title", state.destination.name)
            if let route = state.route {
                return content(route, title: title, status: status(state.freshness))
            }
            if state.freshness.failed {
                return RouteContent(title: title, message: unreachable(L("widget_retry_soon")))
            }
            return RouteContent(
                title: title,
                message: WidgetMessage(symbol: "arrow.triangle.turn.up.right.diamond", title: L("widget_no_route"), body: L("widget_no_route_body")),
                status: status(state.freshness)
            )
        }
    }

    static func glance() async -> GlanceContent {
        guard let state = try? await PulseTransitWidgets.shared.glance() else {
            return GlanceContent(message: unreachable(nil))
        }
        let live = state.freshness.staleSinceMillis == nil
        var content = GlanceContent()
        if let weather = state.weather {
            content.weatherSymbol = WeatherSymbolKt.parseWeatherSymbol(symbolCode: weather.symbolCode).sfSymbolName
            content.temperature = temperatureText(weather.temperature)
        }
        content.stopName = state.stop?.name
        // Each row has one time here: the glance lists departures, not lines.
        content.rows = state.upcoming.map { row($0, live: live) }
        if state.stop == nil {
            content.message = WidgetMessage(symbol: "mappin.and.ellipse", title: L("widget_no_stops"))
        } else if content.rows.isEmpty {
            content.message = state.freshness.failed ? unreachable(nil) : WidgetMessage(symbol: "clock", title: L("no_departures"))
        }
        content.status = status(state.freshness).flatMap { $0.isWarning ? $0 : nil }
        return content
    }

    // MARK: Helpers

    private static var appName: String { "PulseTransit" }

    private static func unreachable(_ body: String?) -> WidgetMessage {
        WidgetMessage(symbol: "icloud.slash", title: L("widget_unreachable"), body: body)
    }

    private static func row(_ row: WidgetDepartureRow, live: Bool) -> WidgetRow {
        let times = zip(row.departures, row.departureMillis).map { departure, millis in
            // A cached prediction is no longer live, so it loses the realtime accent.
            WidgetTime(
                date: date(millis.int64Value),
                text: clock(millis.int64Value),
                isRealtime: departure.isRealtime && live,
                isCancelled: departure.isCancelled
            )
        }
        return WidgetRow(line: row.line, mode: row.transportMode, destination: row.destination, platform: row.platform, times: times)
    }

    private static func content(_ route: WidgetRoute, title: String, status: WidgetStatus?) -> RouteContent {
        var content = RouteContent(title: title, status: status)
        content.leaveAt = date(route.leaveAtMillis)
        content.leaveAtText = L("widget_leave_at", clock(route.leaveAtMillis))
        content.legs = route.legs.map { leg in
            if leg.isWalk, let minutes = leg.walkMinutes {
                return RouteLegItem(line: nil, mode: leg.mode, walk: L("leg_walk", minutes.intValue))
            }
            return RouteLegItem(line: leg.line, mode: leg.mode, walk: nil)
        }
        if route.isWalkOnly { content.walkOnly = L("widget_walk_only") }
        if let boardAt = route.boardAtMillis?.int64Value {
            content.departs = L("widget_board_at", clock(boardAt))
            if let platform = route.platform { content.departsWithPlatform = L("widget_board_at_platform", clock(boardAt), platform) }
        }
        if let arriveAt = route.arriveAtMillis?.int64Value { content.arrives = L("widget_arrive_at", clock(arriveAt)) }
        content.duration = tripDuration(route.durationMinutes)
        return content
    }

    /// The footer: "Offline · data from 08:12" for cached data, "Updated 08:14" when live; nil
    /// when nothing could be fetched (the message already says so).
    private static func status(_ freshness: WidgetFreshness) -> WidgetStatus? {
        if freshness.failed { return nil }
        if let stale = freshness.staleSinceMillis?.int64Value {
            return WidgetStatus(text: L("widget_offline_since", clock(stale)), isWarning: true)
        }
        return WidgetStatus(text: L("widget_updated", clock(Int64(Date().timeIntervalSince1970 * 1000))), isWarning: false)
    }

    private static func clock(_ millis: Int64) -> String { TripTimeKt.clockTimeText(atMillis: millis) }

    private static func date(_ millis: Int64) -> Date { Date(timeIntervalSince1970: TimeInterval(millis) / 1000) }
}

// MARK: - Timelines

/// Entries for the next reload interval, one a minute: each drops the times already gone, and the
/// glance's clock ticks with them. Entries cost nothing against the reload budget.
func minuteEntries<Content: Hashable>(_ content: Content, from now: Date = Date(), drop: (Content, Date) -> Content) -> Timeline<HomeWidgetEntry<Content>> {
    let start = Calendar.current.dateInterval(of: .minute, for: now)?.start ?? now
    let minutes = Int(widgetReloadInterval / 60)
    let entries = (0..<minutes).map { offset -> HomeWidgetEntry<Content> in
        let date = offset == 0 ? now : start.addingTimeInterval(TimeInterval(offset * 60))
        return HomeWidgetEntry(date: date, content: drop(content, date))
    }
    return Timeline(entries: entries, policy: .after(now.addingTimeInterval(widgetReloadInterval)))
}

extension DeparturesContent {
    func at(_ date: Date) -> DeparturesContent {
        guard message == nil else { return self }
        var copy = self
        copy.rows = rows.compactMap { $0.at(date) }
        if copy.rows.isEmpty { copy.message = WidgetMessage(symbol: "clock", title: L("no_departures")) }
        return copy
    }
}

extension GlanceContent {
    func at(_ date: Date) -> GlanceContent {
        guard message == nil else { return self }
        var copy = self
        copy.rows = rows.compactMap { $0.at(date) }
        if copy.rows.isEmpty { copy.message = WidgetMessage(symbol: "clock", title: L("no_departures")) }
        return copy
    }
}
