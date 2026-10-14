import Shared
import SwiftUI

/// The next fastest journey to one destination set in Settings; tap to show the full route.
struct FastestTripBanner: View {
    let trip: TripInfo
    var isRefreshing = false
    @State private var expanded = false

    var body: some View {
        HStack(spacing: 16) {
            Text(departureTime)
                .font(.title2.bold())
                .monospacedDigit()
            VStack(alignment: .leading, spacing: 2) {
                Text(L("fastest_route_to", trip.destinationName))
                    .font(.headline)
                Text(routeText)
                    .font(.subheadline)
                    .opacity(0.8)
                    .lineLimit(expanded ? nil : 1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Text(L("duration_min", Int(trip.durationMinutes)))
                .font(.headline)
            Image(systemName: expanded ? "chevron.up" : "chevron.down")
                .accessibilityLabel(L(expanded ? "collapse" : "expand"))
        }
        .foregroundColor(Palette.textPrimary)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(Palette.primary.opacity(0.18))
        .contentShape(Rectangle())
        .onTapGesture { withAnimation { expanded.toggle() } }
        .overlay(alignment: .bottom) {
            if isRefreshing { RefreshBar(color: Palette.textPrimary.opacity(0.6)) }
        }
    }

    /// "HH:mm" straight out of the ISO timestamp, which is already in local Oslo time.
    private var departureTime: String {
        let time = trip.expectedStartTime
        guard time.count >= 16 else { return "--:--" }
        let start = time.index(time.startIndex, offsetBy: 11)
        return String(time[start ..< time.index(start, offsetBy: 5)])
    }

    private var routeText: String {
        guard !trip.legs.isEmpty else { return L("fastest_route_info", trip.lineName, trip.originName) }
        return trip.legs.compactMap { leg -> String? in
            if let line = leg.lineName {
                let prefix = "\(Palette.transportEmoji(leg.mode)) \(line)"
                return leg.fromName.map { "\(prefix) \(L("leg_from", $0))" } ?? prefix
            }
            // Zero-minute foot legs are quay transfers, not worth a mention.
            return leg.durationMinutes > 0 ? L("leg_walk", Int(leg.durationMinutes)) : nil
        }
        .joined(separator: "  →  ")
    }
}

/// Stands in for a `FastestTripBanner` whose route hasn't arrived yet, blurred until it does.
struct FastestTripPlaceholder: View {
    let destinationName: String
    @State private var pulse = false

    var body: some View {
        BlurredPlaceholder(label: L("loading_fastest_route", destinationName), tint: Palette.textPrimary) {
            // Mirrors the banner's layout: departure time, title over route, duration.
            HStack(spacing: 16) {
                PlaceholderBlock(width: 64, height: 28, color: Palette.textPrimary, pulse: pulse)
                VStack(alignment: .leading, spacing: 6) {
                    PlaceholderBlock(width: 150, height: 18, color: Palette.textPrimary, pulse: pulse)
                    PlaceholderBlock(height: 14, color: Palette.textPrimary, pulse: pulse)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                PlaceholderBlock(width: 48, height: 20, color: Palette.textPrimary, pulse: pulse)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
        .background(Palette.primary.opacity(0.18))
        .modifier(PlaceholderPulse(pulse: $pulse))
    }
}
