import Shared
import SwiftUI

// The board's chrome around the stops, as on Android (BoardChrome.kt).

/// The one lasting notice under the header (T4), chosen by priority so they never stack: stale data
/// (no internet, or Entur not answering) before a new version. Nothing shows while the board is fresh.
struct BoardNoticeSlot: View {
    let state: StopBoardViewModel.UiState
    let update: AppUpdate?
    let onRetry: () -> Void
    let onDismissUpdate: () -> Void

    var body: some View {
        if state.showStaleNotice, let age = state.staleAgeMinutes?.intValue {
            if state.isOnline {
                Callout(L("stale_entur_body", age), tone: .error, title: L("stale_entur_title"), symbol: loadFailureSymbol(isOnline: true)) {
                    Button(L("retry"), action: onRetry).buttonStyle(.borderless)
                }
            } else {
                Callout(L("stale_no_internet_body", age), tone: .warning, title: L("stale_no_internet_title"), symbol: loadFailureSymbol(isOnline: false))
            }
        } else if let update {
            Callout(L("update_available"), tone: .info, symbol: "arrow.down.app") {
                Button(L("dismiss_update"), action: onDismissUpdate).buttonStyle(.borderless)
                if let url = update.storeUrl.flatMap(URL.init(string:)) {
                    Button(L("update_app")) { UIApplication.shared.open(url) }.buttonStyle(.borderless)
                }
            }
        }
    }
}

/// "Updated 19:37 · Data from Entur · Weather from MET Norway", or "Updating…" during a refresh asked for.
struct BoardFooter: View {
    let state: StopBoardViewModel.UiState

    var body: some View {
        let parts = [
            state.isManualRefreshing ? L("updating") : L("footer_updated", state.lastUpdated),
            L("attribution_entur"),
            state.showWeather && !state.weather.isEmpty ? L("attribution_met") : nil,
        ].compactMap { $0 }
        Text(parts.joined(separator: " · "))
            .font(.pulse(.caption))
            .foregroundColor(Palette.textSecondary)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
    }
}

/// A board with no stops yet: what it is for, and the way to Settings. Clock and weather still run.
struct EmptyBoard: View {
    let onAddStops: () -> Void

    var body: some View {
        VStack(spacing: FjordTokens.space3) {
            Spacer(minLength: 0)
            EmptyBoardArt()
            Text(L("board_empty_title"))
                .font(.pulse(role: FjordTokens.typeTitle, relativeTo: .title3))
                .foregroundColor(Palette.textPrimary)
                .multilineTextAlignment(.center)
            Text(L("board_empty_body"))
                .font(.pulse(.body))
                .foregroundColor(Palette.textSecondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 360)
            Button(L("board_empty_action"), action: onAddStops)
                .buttonStyle(.borderedProminent)
                .tint(Palette.primary)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, FjordTokens.space6)
        .frame(maxWidth: .infinity)
    }
}

/// A small board with three line rows and a plus, drawn from the theme (the design's empty art).
private struct EmptyBoardArt: View {
    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 12).fill(Palette.surfaceContainer)
                .frame(width: 120, height: 76).offset(x: 20, y: 24)
            ForEach(Array(zip([Palette.transport("bus"), Palette.transport("metro"), Palette.transport("rail")], [56.0, 44.0, 50.0]).enumerated()), id: \.offset) { index, row in
                RoundedRectangle(cornerRadius: 3).fill(row.0).frame(width: 22, height: 12)
                    .offset(x: 32, y: 40 + CGFloat(index) * 20)
                RoundedRectangle(cornerRadius: 4).fill(Palette.outlineVariant).frame(width: row.1, height: 8)
                    .offset(x: 60, y: 42 + CGFloat(index) * 20)
            }
            Circle().fill(Palette.primary).frame(width: 32, height: 32).offset(x: 112, y: 10)
            Image(systemName: "plus").font(.system(size: 16, weight: .bold)).foregroundColor(Palette.onPrimary)
                .frame(width: 32, height: 32).offset(x: 112, y: 10)
        }
        .frame(width: 160, height: 120, alignment: .topLeading)
        .accessibilityHidden(true)
    }
}

/// The board's `BoardUndo` as a toast (T3) with Undo; it clears itself after a while, as Android's
/// snackbar does.
struct UndoToast: View {
    let pending: BoardUndo?
    let onUndo: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        if let pending {
            HStack(spacing: FjordTokens.space3) {
                Text(message(pending))
                    .font(.pulse(.subheadline))
                    .foregroundColor(Palette.onInverseSurface)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button(L("undo"), action: onUndo)
                    .font(.pulse(.subheadline, weight: .semibold))
                    .foregroundColor(Palette.inversePrimary)
                    .buttonStyle(.borderless)
            }
            .padding(.leading, FjordTokens.space4)
            .padding(.trailing, FjordTokens.space2)
            .frame(maxWidth: 560, minHeight: 48)
            .background(RoundedRectangle(cornerRadius: FjordTokens.radiusSm).fill(Palette.inverseSurface))
            .padding(FjordTokens.space4)
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .task(id: pending.serial) {
                try? await Task.sleep(nanoseconds: 8_000_000_000)
                guard !Task.isCancelled else { return }
                onDismiss()
            }
            .onAppear { UIAccessibility.post(notification: .announcement, argument: message(pending)) }
        }
    }

    private func message(_ undo: BoardUndo) -> String {
        switch onEnum(of: undo) {
        case let .lineHidden(hidden): return L("line_hidden", hidden.line)
        case let .destinationSaved(saved): return L("destination_saved", saved.place.name)
        }
    }
}
