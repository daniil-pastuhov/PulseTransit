import Shared
import SwiftUI

/// Opens the app: the first-launch choice of mode until one is made, then the board.
struct RootView: View {
    @StateObject private var model = ModeSelectionModel()

    var body: some View {
        if model.state.mode == nil {
            ModeSelectionView(onChoose: { model.viewModel.choose(mode: $0) })
        } else {
            StopBoardView()
        }
    }
}

/// The first-launch choice between following the device and the wall board, the counterpart of
/// Android's ModeSelectionScreen.
struct ModeSelectionView: View {
    let onChoose: (AppMode) -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                Text(L("mode_choose_title"))
                    .font(.title2.bold())
                    .multilineTextAlignment(.center)
                ModeCard(
                    symbol: "location.fill",
                    title: L("mode_on_spot"),
                    description: L("mode_on_spot_description"),
                    action: { onChoose(.onSpot) }
                )
                ModeCard(
                    symbol: "tv",
                    title: L("mode_board"),
                    description: L("mode_board_description"),
                    action: { onChoose(.board) }
                )
                Text(L("mode_choose_hint"))
                    .font(.subheadline)
                    .foregroundColor(Palette.textSecondary)
                    .multilineTextAlignment(.center)
            }
            .foregroundColor(Palette.textPrimary)
            .frame(maxWidth: 480)
            .padding(24)
            .frame(maxWidth: .infinity)
        }
        .background(Palette.background.ignoresSafeArea())
    }
}

private struct ModeCard: View {
    let symbol: String
    let title: String
    let description: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Image(systemName: symbol)
                    .font(.system(size: 28))
                    .foregroundColor(Palette.primary)
                    .frame(width: 36)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.title3.bold())
                    Text(description)
                        .font(.subheadline)
                        .multilineTextAlignment(.leading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .foregroundColor(Palette.textPrimary)
            .padding(20)
            .background(RoundedRectangle(cornerRadius: 16).fill(Palette.surface))
        }
        .buttonStyle(.plain)
    }
}
