import AppIntents
import Shared
import SwiftUI
import WidgetKit

// The home-screen widgets, the counterparts of Android's AtAGlanceWidget, DeparturesWidget and
// RouteWidget. Sizes follow the design (docs/design, Widgets): 2×2 is small, 4×2 medium, 4×4 large,
// and Android's 4×1 strip maps to the Lock Screen's rectangular accessory. Widgets on iOS 16 hold no
// buttons, so there is no refresh button: the timeline reloads itself every 15 minutes.
//
// From iOS 17 a Departures or Fastest route widget is set to a stop or destination in the system's
// edit sheet (App Intents); on iOS 16 it shows the first one, as Android's do until one is picked.

enum WidgetKind {
    static let glance = "AtAGlanceWidget"
    static let departures = "DeparturesWidget"
    static let route = "RouteWidget"
    /// The iOS 16 versions, which cannot be configured; a kind is unique within the bundle.
    static let departuresUnconfigurable = "DeparturesWidgetIOS16"
    static let routeUnconfigurable = "RouteWidgetIOS16"
}

/// [families] on iOS 16, none from 17, where the configurable version takes the widget's place.
func ios16Only(_ families: [WidgetFamily]) -> [WidgetFamily] {
    if #available(iOSApplicationExtension 17.0, *) { return [] }
    return families
}

// MARK: - At a glance

struct GlanceProvider: TimelineProvider {
    func placeholder(in context: Context) -> HomeWidgetEntry<GlanceContent> { HomeWidgetEntry(date: Date(), content: nil) }

    func getSnapshot(in context: Context, completion: @escaping (HomeWidgetEntry<GlanceContent>) -> Void) {
        Task { completion(HomeWidgetEntry(date: Date(), content: await HomeWidgetLoader.glance())) }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<HomeWidgetEntry<GlanceContent>>) -> Void) {
        Task { completion(minuteEntries(await HomeWidgetLoader.glance()) { $0.at($1) }) }
    }
}

struct AtAGlanceWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: WidgetKind.glance, provider: GlanceProvider()) { entry in
            GlanceWidgetView(entry: entry)
        }
        .configurationDisplayName(L("widget_gallery_glance"))
        .description(L("widget_gallery_glance_description"))
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .accessoryRectangular])
    }
}

// MARK: - Departures

struct DeparturesProvider: TimelineProvider {
    func placeholder(in context: Context) -> HomeWidgetEntry<DeparturesContent> { HomeWidgetEntry(date: Date(), content: nil) }

    func getSnapshot(in context: Context, completion: @escaping (HomeWidgetEntry<DeparturesContent>) -> Void) {
        Task { completion(HomeWidgetEntry(date: Date(), content: await HomeWidgetLoader.departures(stopId: nil))) }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<HomeWidgetEntry<DeparturesContent>>) -> Void) {
        Task { completion(minuteEntries(await HomeWidgetLoader.departures(stopId: nil)) { $0.at($1) }) }
    }
}

struct DeparturesWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: WidgetKind.departuresUnconfigurable, provider: DeparturesProvider()) { entry in
            DeparturesWidgetView(entry: entry)
        }
        .configurationDisplayName(L("widget_gallery_departures"))
        .description(L("widget_gallery_departures_description"))
        .supportedFamilies(ios16Only([.systemSmall, .systemMedium, .systemLarge]))
    }
}

@available(iOS 17.0, *)
struct ConfigurableDeparturesProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> HomeWidgetEntry<DeparturesContent> { HomeWidgetEntry(date: Date(), content: nil) }

    func snapshot(for configuration: DeparturesIntent, in context: Context) async -> HomeWidgetEntry<DeparturesContent> {
        HomeWidgetEntry(date: Date(), content: await HomeWidgetLoader.departures(stopId: configuration.stop?.id))
    }

    func timeline(for configuration: DeparturesIntent, in context: Context) async -> Timeline<HomeWidgetEntry<DeparturesContent>> {
        minuteEntries(await HomeWidgetLoader.departures(stopId: configuration.stop?.id)) { $0.at($1) }
    }
}

@available(iOS 17.0, *)
struct ConfigurableDeparturesWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: WidgetKind.departures, intent: DeparturesIntent.self, provider: ConfigurableDeparturesProvider()) { entry in
            DeparturesWidgetView(entry: entry)
        }
        .configurationDisplayName(L("widget_gallery_departures"))
        .description(L("widget_gallery_departures_description"))
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

// MARK: - Fastest route

struct RouteProvider: TimelineProvider {
    func placeholder(in context: Context) -> HomeWidgetEntry<RouteContent> { HomeWidgetEntry(date: Date(), content: nil) }

    func getSnapshot(in context: Context, completion: @escaping (HomeWidgetEntry<RouteContent>) -> Void) {
        Task { completion(HomeWidgetEntry(date: Date(), content: await HomeWidgetLoader.route(destinationId: nil))) }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<HomeWidgetEntry<RouteContent>>) -> Void) {
        Task { completion(routeTimeline(await HomeWidgetLoader.route(destinationId: nil))) }
    }
}

struct RouteWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: WidgetKind.routeUnconfigurable, provider: RouteProvider()) { entry in
            RouteWidgetView(entry: entry)
        }
        .configurationDisplayName(L("widget_gallery_route"))
        .description(L("widget_gallery_route_description"))
        .supportedFamilies(ios16Only([.systemSmall, .systemMedium]))
    }
}

@available(iOS 17.0, *)
struct ConfigurableRouteProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> HomeWidgetEntry<RouteContent> { HomeWidgetEntry(date: Date(), content: nil) }

    func snapshot(for configuration: RouteIntent, in context: Context) async -> HomeWidgetEntry<RouteContent> {
        HomeWidgetEntry(date: Date(), content: await HomeWidgetLoader.route(destinationId: configuration.destination?.id))
    }

    func timeline(for configuration: RouteIntent, in context: Context) async -> Timeline<HomeWidgetEntry<RouteContent>> {
        routeTimeline(await HomeWidgetLoader.route(destinationId: configuration.destination?.id))
    }
}

@available(iOS 17.0, *)
struct ConfigurableRouteWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: WidgetKind.route, intent: RouteIntent.self, provider: ConfigurableRouteProvider()) { entry in
            RouteWidgetView(entry: entry)
        }
        .configurationDisplayName(L("widget_gallery_route"))
        .description(L("widget_gallery_route_description"))
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

/// A route is good until its leave time: then the widget asks again, rather than show a trip already
/// gone for the rest of the interval.
func routeTimeline(_ content: RouteContent, now: Date = Date()) -> Timeline<HomeWidgetEntry<RouteContent>> {
    var reload = now.addingTimeInterval(widgetReloadInterval)
    if let leave = content.leaveAt, leave > now, leave < reload { reload = leave.addingTimeInterval(30) }
    return Timeline(entries: [HomeWidgetEntry(date: now, content: content)], policy: .after(reload))
}

// MARK: - Configuration (iOS 17+)

/// A saved stop or destination, as the edit sheet lists it.
@available(iOS 17.0, *)
struct SavedPlace: AppEntity {
    let id: String
    let name: String

    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "widget_choose_stop")
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }
    static let defaultQuery = SavedStopQuery()
}

/// The favourite stops, in the board's order.
@available(iOS 17.0, *)
struct SavedStopQuery: EntityQuery {
    func entities(for identifiers: [SavedPlace.ID]) async throws -> [SavedPlace] {
        try await suggestedEntities().filter { identifiers.contains($0.id) }
    }

    func suggestedEntities() async throws -> [SavedPlace] {
        PulseTransitWidgets.shared.stops().map { SavedPlace(id: $0.id, name: $0.name) }
    }
}

/// A saved destination, as the edit sheet lists it.
@available(iOS 17.0, *)
struct SavedDestination: AppEntity {
    let id: String
    let name: String

    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "widget_choose_destination")
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }
    static let defaultQuery = SavedDestinationQuery()
}

@available(iOS 17.0, *)
struct SavedDestinationQuery: EntityQuery {
    func entities(for identifiers: [SavedDestination.ID]) async throws -> [SavedDestination] {
        try await suggestedEntities().filter { identifiers.contains($0.id) }
    }

    func suggestedEntities() async throws -> [SavedDestination] {
        PulseTransitWidgets.shared.destinations().map { SavedDestination(id: $0.id, name: $0.name) }
    }
}

@available(iOS 17.0, *)
struct DeparturesIntent: WidgetConfigurationIntent {
    static let title: LocalizedStringResource = "widget_gallery_departures"
    static let description = IntentDescription("widget_choose_hint")

    /// Unset shows the first favourite, as on Android.
    @Parameter(title: "widget_choose_stop")
    var stop: SavedPlace?
}

@available(iOS 17.0, *)
struct RouteIntent: WidgetConfigurationIntent {
    static let title: LocalizedStringResource = "widget_gallery_route"
    static let description = IntentDescription("widget_choose_hint")

    /// Unset shows the destination the board would show now.
    @Parameter(title: "widget_choose_destination")
    var destination: SavedDestination?
}
