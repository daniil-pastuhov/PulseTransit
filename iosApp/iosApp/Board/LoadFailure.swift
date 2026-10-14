import SwiftUI

/// Why a fetch failed, as far as the app can tell: no connection on the device at all, or a
/// connection that couldn't get an answer out of Entur. Mirrors Android's `loadFailureReason`.
func loadFailureReason(isOnline: Bool) -> String {
    L(isOnline ? "error_entur_unreachable" : "error_no_internet")
}

func loadFailureSymbol(isOnline: Bool) -> String {
    isOnline ? "icloud.slash" : "wifi.slash"
}

/// Stands in for a board whose data failed to load with nothing cached. It replaces "no
/// departures", which would wrongly tell someone reading the board that nothing is leaving.
struct LoadFailedMessage: View {
    let isOnline: Bool
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 4) {
            Spacer()
            Image(systemName: loadFailureSymbol(isOnline: isOnline))
                .font(.system(size: 28))
                .foregroundColor(Palette.error)
                .accessibilityHidden(true)
                .padding(.bottom, 4)
            Text(loadFailureReason(isOnline: isOnline))
                .font(.headline)
                .multilineTextAlignment(.center)
            Text(L("error_retrying_automatically"))
                .font(.subheadline)
                .foregroundColor(Palette.textSecondary)
                .multilineTextAlignment(.center)
            Button(L("retry"), action: onRetry)
                .buttonStyle(.bordered)
                .padding(.top, 4)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }
}

/// Keeps a destination's slot when its route failed to load, rather than silently dropping it.
struct FastestTripUnavailable: View {
    let destinationName: String
    let isOnline: Bool
    let isRetrying: Bool

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: loadFailureSymbol(isOnline: isOnline))
                .opacity(0.8)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(L("fastest_route_unavailable", destinationName))
                    .font(.subheadline.bold())
                    .lineLimit(1)
                Text(loadFailureReason(isOnline: isOnline))
                    .font(.caption)
                    .opacity(0.8)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .foregroundColor(Palette.textPrimary)
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Palette.primary.opacity(0.18))
        .overlay(alignment: .bottom) {
            if isRetrying { RefreshBar(color: Palette.textPrimary.opacity(0.6)) }
        }
        .accessibilityElement(children: .combine)
    }
}
