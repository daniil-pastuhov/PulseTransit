import CoreLocation
import Shared
import SwiftUI

/// Opens the app: first launch as the shared `ModeSelectionViewModel` steps through it (the choice
/// of mode, the intro to it and, for on the spot, the ask for location), then the board.
struct RootView: View {
    @StateObject private var model = ModeSelectionModel()

    var body: some View {
        let state = model.state
        switch state.screen {
        case .mode:
            ModeSelectionView(
                selected: state.choosingAgain ? state.mode : nil,
                onChoose: { model.viewModel.choose(mode: $0) }
            )
        case .intro:
            IntroView(
                pages: state.intro,
                onFinish: { model.viewModel.finishIntro(locationGranted: LocationPermission.isGranted) },
                onBack: { model.viewModel.chooseAgain() }
            )
            // Another choice starts its intro on the first page.
            .id(state.mode)
        case .location:
            LocationAccessView(
                onDone: { model.viewModel.finishLocation() },
                onUseBoard: { model.viewModel.choose(mode: .board) }
            )
        case .none:
            StopBoardView()
        }
    }
}

/// The first-launch choice between following the device and the wall board, the counterpart of
/// Android's ModeSelectionScreen. Tapping a card chooses it; there is no Continue. `selected` marks
/// the mode chosen before, when the user came back from the intro. From 600 pt the cards sit side
/// by side.
struct ModeSelectionView: View {
    var selected: AppMode?
    let onChoose: (AppMode) -> Void

    var body: some View {
        GeometryReader { proxy in
            let sideBySide = proxy.size.width >= 600
            ScrollView {
                VStack(alignment: .leading, spacing: FjordTokens.space4) {
                    HStack(spacing: FjordTokens.space2) {
                        Image(systemName: "tram.fill")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(Palette.onPrimary)
                            .frame(width: 40, height: 40)
                            .background(RoundedRectangle(cornerRadius: FjordTokens.radiusMd).fill(Palette.primary))
                            .accessibilityHidden(true)
                        Text(L("app_name_brand"))
                            .font(.pulse(role: FjordTokens.typeTitleSm, relativeTo: .headline))
                            .foregroundColor(Palette.primary)
                    }
                    Text(L("mode_choose_title"))
                        .font(.pulse(role: FjordTokens.typeHeadline, relativeTo: .title))
                        .foregroundColor(Palette.textPrimary)
                        .accessibilityAddTraits(.isHeader)
                    let cards = Group {
                        ModeCard(
                            symbol: "location.fill",
                            title: L("mode_on_spot"),
                            description: L("mode_on_spot_description"),
                            selected: selected == .onSpot,
                            tall: sideBySide,
                            action: { onChoose(.onSpot) }
                        )
                        ModeCard(
                            symbol: "rectangle.grid.2x2",
                            title: L("mode_board"),
                            description: L("mode_board_description"),
                            selected: selected == .board,
                            tall: sideBySide,
                            action: { onChoose(.board) }
                        )
                    }
                    if sideBySide {
                        HStack(alignment: .top, spacing: FjordTokens.space3) { cards }
                    } else {
                        VStack(spacing: FjordTokens.space3) { cards }
                    }
                    Label(L("mode_choose_hint"), systemImage: "info.circle")
                        .font(.pulse(.subheadline))
                        .foregroundColor(Palette.textSecondary)
                }
                .frame(maxWidth: OnboardingLayout.columnWidth)
                .padding(.horizontal, FjordTokens.space4)
                .padding(.vertical, FjordTokens.space6)
                .frame(maxWidth: .infinity, minHeight: proxy.size.height)
            }
        }
        .background(Palette.background.ignoresSafeArea())
    }
}

/// One mode: its symbol, name and what it does. Selected, it is outlined in the primary colour
/// with a check. `tall` stacks the symbol above the text, for the side-by-side layout.
private struct ModeCard: View {
    let symbol: String
    let title: String
    let description: String
    let selected: Bool
    let tall: Bool
    let action: () -> Void

    private var tile: some View {
        Image(systemName: symbol)
            .font(.system(size: 24, weight: .semibold))
            .foregroundColor(selected ? Palette.onPrimary : Palette.onPrimaryContainer)
            .frame(width: 56, height: 56)
            .background(RoundedRectangle(cornerRadius: FjordTokens.radiusMd).fill(selected ? Palette.primary : Palette.primaryContainer))
            .accessibilityHidden(true)
    }

    private var text: some View {
        VStack(alignment: .leading, spacing: FjordTokens.space1) {
            Text(title)
                .font(.pulse(role: FjordTokens.typeTitle, relativeTo: .title3))
                .foregroundColor(Palette.textPrimary)
            Text(description)
                .font(.pulse(.subheadline))
                .foregroundColor(selected ? Palette.onPrimaryContainer : Palette.textSecondary)
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    var body: some View {
        Button(action: action) {
            Group {
                if tall {
                    VStack(alignment: .leading, spacing: FjordTokens.space3) { tile; text }
                        .padding(FjordTokens.space5)
                        .frame(maxWidth: .infinity, minHeight: 200, alignment: .topLeading)
                } else {
                    HStack(spacing: FjordTokens.space3) { tile; text }
                        .padding(.vertical, FjordTokens.space4)
                        .padding(.leading, FjordTokens.space4)
                        .padding(.trailing, FjordTokens.space8)
                        .frame(maxWidth: .infinity, minHeight: 96, alignment: .leading)
                }
            }
            .background(
                RoundedRectangle(cornerRadius: FjordTokens.radiusLg)
                    .fill(selected ? Palette.primaryContainer : Palette.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: FjordTokens.radiusLg)
                    .strokeBorder(selected ? Palette.primary : Palette.outlineVariant, lineWidth: selected ? 2 : 1)
            )
            .overlay(alignment: .topTrailing) {
                if selected {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 22))
                        .foregroundColor(Palette.primary)
                        .padding(FjordTokens.space3)
                        .accessibilityHidden(true)
                }
            }
            .contentShape(RoundedRectangle(cornerRadius: FjordTokens.radiusLg))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}

/// The onboarding screens' content column, centred at this width on wide windows.
enum OnboardingLayout {
    static let columnWidth: CGFloat = 560
}

/// Whether the app may use the location: when in use, or always.
enum LocationPermission {
    static var isGranted: Bool {
        switch CLLocationManager().authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways: return true
        default: return false
        }
    }
}
