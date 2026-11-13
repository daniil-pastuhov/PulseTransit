import Shared
import SwiftUI

// The fastest-route cards above the stops, as on Android (RouteBanners.kt): "Leave 19:40 | Fastest
// to X, walk 9 min → FLY2 from Oslo S | 28 min, arr. 20:08", in the `route` colours, which stay
// dark on the wall at night. A route that failed or found nothing keeps its slot as a muted card.

/// A route card's frame: the route colours (or muted), rounded, at least a touch target high.
private struct RouteCardFrame: ViewModifier {
    var muted = false

    func body(content: Content) -> some View {
        content
            .padding(.horizontal, FjordTokens.space4)
            .padding(.vertical, FjordTokens.space3)
            .frame(maxWidth: .infinity, minHeight: FjordTokens.touch, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: FjordTokens.radiusLg)
                    .fill(muted ? Palette.surfaceContainer : Palette.route)
            )
    }
}

/// The next fastest journey to one destination; tap to open its details.
struct FastestTripBanner: View {
    let trip: TripInfo
    var isRefreshing = false
    let onTap: () -> Void

    /// The card's width, for whether it stacks its title (`BoardLayout.isCompactBanner`).
    @State private var width: CGFloat = .infinity

    private var leave: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(L("route_leave"))
                .font(.pulse(.caption))
                .foregroundColor(Palette.onRoute)
            Text(isoClockTime(trip.expectedStartTime))
                .font(.pulse(size: 26, weight: .bold, relativeTo: .title2))
                .foregroundColor(Palette.routeAccent)
                .lineLimit(1)
        }
    }

    private var titleAndLegs: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(L("fastest_route_to", trip.destinationName))
                .font(.pulse(.callout, weight: .semibold))
                .lineLimit(2)
            TripLegs(trip: trip, color: Palette.onRoute)
        }
        .foregroundColor(Palette.onRoute)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var duration: some View {
        VStack(alignment: .trailing, spacing: 0) {
            Text(tripDuration(trip.durationMinutes))
                .font(.pulse(.subheadline, weight: .semibold))
            if let arrival = TripArrivalKt.tripArrival(trip: trip) {
                let time = arrival.dayOffset > 0 ? "\(arrival.time)⁺\(arrival.dayOffset)" : arrival.time
                Text(L("route_arrival", time))
                    .font(.pulse(.caption))
            }
        }
        .foregroundColor(Palette.onRoute)
        .lineLimit(1)
        .fixedSize()
    }

    var body: some View {
        Group {
            if BoardLayout.shared.isCompactBanner(widthDp: Float(width)) {
                // Too narrow for three columns: the title and legs go under the times.
                VStack(alignment: .leading, spacing: FjordTokens.space2) {
                    HStack(alignment: .top) {
                        leave
                        Spacer()
                        duration
                    }
                    titleAndLegs
                }
            } else {
                HStack(spacing: FjordTokens.space3) {
                    leave
                    titleAndLegs
                    duration
                }
            }
        }
        .modifier(RouteCardFrame())
        .background(GeometryReader { geometry in
            Color.clear
                .onAppear { width = geometry.size.width }
                .onChange(of: geometry.size.width) { width = $0 }
        })
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
        // Read as one button that opens the route's details, by VoiceOver and the UI tests.
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
        .accessibilityIdentifier("fastestTrip")
        .overlay(alignment: .bottom) {
            if isRefreshing {
                RefreshBar(color: Palette.onRoute.opacity(0.6)).padding(.horizontal, FjordTokens.radiusLg)
            }
        }
    }
}

/// A trip's legs as a chain: "[walk] 9 min → [FLY2] from Oslo S", line pills in their colours, or
/// its first line and origin if Entur sent no legs. Zero-minute foot legs are quay transfers.
struct TripLegs: View {
    let trip: TripInfo
    var color: Color = Palette.textPrimary

    var body: some View {
        let legs = trip.legs.filter { $0.lineName != nil || $0.durationMinutes > 0 }
        if legs.isEmpty {
            Text(L("fastest_route_info", trip.lineName, trip.originName))
                .font(.pulse(.subheadline))
                .foregroundColor(color)
                .lineLimit(2)
        } else if #available(iOS 16, *) {
            // Where the chain is too long for the line, the boarding stops go first.
            ViewThatFits(in: .horizontal) {
                chain(legs, showsFrom: true)
                chain(legs, showsFrom: false)
            }
        } else {
            chain(legs, showsFrom: true)
        }
    }

    private func chain(_ legs: [TripLeg], showsFrom: Bool) -> some View {
        HStack(spacing: FjordTokens.space1) {
            ForEach(Array(legs.enumerated()), id: \.offset) { index, leg in
                if index > 0 {
                    Image(systemName: "arrow.right").font(.pulse(.caption)).accessibilityHidden(true)
                }
                if let line = leg.lineName {
                    LinePill(line: line, mode: leg.mode, small: true)
                    if showsFrom, let from = leg.fromName {
                        Text(L("leg_from", from)).lineLimit(1).fixedSize()
                    }
                } else {
                    Image(systemName: "figure.walk").font(.pulse(.caption)).accessibilityHidden(true)
                    Text(L("leg_walk", Int(leg.durationMinutes))).lineLimit(1).fixedSize()
                }
            }
        }
        .font(.pulse(.subheadline))
        .foregroundColor(color)
    }
}

/// "HH:mm" straight out of an ISO timestamp, which is already in local Oslo time.
func isoClockTime(_ iso: String) -> String {
    guard iso.count >= 16 else { return "--:--" }
    let start = iso.index(iso.startIndex, offsetBy: 11)
    return String(iso[start ..< iso.index(start, offsetBy: 5)])
}

/// A muted card in a route's slot, so the layout doesn't jump when the route comes back.
private struct RouteMessage: View {
    let symbol: String
    let title: String
    var detail: String?
    var isRetrying = false

    var body: some View {
        HStack(spacing: FjordTokens.space3) {
            Image(systemName: symbol)
                .font(.pulse(.title2))
                .foregroundColor(Palette.textSecondary)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.pulse(.callout, weight: .semibold))
                    .foregroundColor(Palette.textPrimary)
                if let detail {
                    Text(detail)
                        .font(.pulse(.subheadline))
                        .foregroundColor(Palette.textSecondary)
                }
            }
        }
        .modifier(RouteCardFrame(muted: true))
        .overlay(alignment: .bottom) {
            if isRetrying { RefreshBar().padding(.horizontal, FjordTokens.radiusLg) }
        }
        .accessibilityElement(children: .combine)
    }
}

/// Keeps a destination's slot when its route failed to load, rather than silently dropping it.
struct FastestTripUnavailable: View {
    let destinationName: String
    let isOnline: Bool
    let isRetrying: Bool

    var body: some View {
        RouteMessage(
            symbol: loadFailureSymbol(isOnline: isOnline),
            title: L("fastest_route_unavailable", destinationName),
            detail: L("error_retrying_automatically"),
            isRetrying: isRetrying
        )
    }
}

/// Says that Entur found no route to a destination, e.g. none arriving by the time asked for,
/// rather than leaving a placeholder that never fills. As Android's FastestTripNone.
struct FastestTripNone: View {
    let destinationName: String
    let time: TripTime

    var body: some View {
        let arriveBy = time as? TripTimeArriveBy
        RouteMessage(
            symbol: arriveBy != nil ? "clock" : "magnifyingglass",
            title: arriveBy.map { L("no_route_to_arriving", destinationName, tripClockText($0.atMillis)) }
                ?? L("no_route_to", destinationName)
        )
    }
}

/// Stands in for a `FastestTripBanner` whose route hasn't arrived yet: its title, and skeletons.
struct FastestTripPlaceholder: View {
    let destinationName: String
    @State private var pulse = false

    var body: some View {
        let label = L("loading_fastest_route", destinationName)
        HStack(spacing: FjordTokens.space3) {
            PlaceholderBlock(width: 52, height: 36, pulse: pulse)
            VStack(alignment: .leading, spacing: 6) {
                Text(label)
                    .font(.pulse(.callout, weight: .semibold))
                    .foregroundColor(Palette.textPrimary)
                    .lineLimit(2)
                PlaceholderBlock(width: 140, height: 12, pulse: pulse)
            }
        }
        .modifier(RouteCardFrame(muted: true))
        .modifier(PlaceholderPulse(pulse: $pulse))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
    }
}
