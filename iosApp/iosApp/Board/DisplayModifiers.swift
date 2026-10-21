import Shared
import SwiftUI
import UIKit

extension View {
    /// Slowly moves the whole board by a few points every minute to spread OLED wear, using the
    /// shared schedule so both apps shift by the same rules.
    func pixelShift(_ level: BurnInProtectionLevel) -> some View {
        modifier(PixelShift(level: level))
    }

    /// Keeps the screen from auto-locking while `enabled`, for the always-on display setting.
    func keepingScreenAwake(_ enabled: Bool) -> some View {
        onAppear { UIApplication.shared.isIdleTimerDisabled = enabled }
            .onChange(of: enabled) { UIApplication.shared.isIdleTimerDisabled = $0 }
            .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
    }

    /// Sizes a sheet's content as a page rather than iPad's default form sheet, a small fixed-size
    /// card in the middle of the display. Not a full-screen cover: that would take the board out of
    /// the hierarchy and run its `onDisappear`, letting the screen lock while Settings is open.
    /// iOS 15–17 have no SwiftUI API for it and keep the form sheet; iPhone is unaffected.
    @ViewBuilder
    func pageSizedSheet() -> some View {
        if #available(iOS 18, *) {
            presentationSizing(.page)
        } else {
            self
        }
    }

    /// Hides the home indicator in full-screen mode. iOS 15 has no SwiftUI API for it, so there it
    /// only hides the status bar.
    @ViewBuilder
    func hidingHomeIndicator(_ hidden: Bool) -> some View {
        if #available(iOS 16, *) {
            persistentSystemOverlays(hidden ? .hidden : .automatic)
        } else {
            self
        }
    }
}

/// Where the board's header goes on iPhone Duo, whose bars iOS 27.1 lays out differently.
enum BarPlacement {
    /// Not in a bar: the board shows its own header.
    case none
    /// The column iOS 27.1 shows beside the content, under the status bar (folded, or unfolded
    /// in landscape).
    case vertical
    /// The row the status bar shares with the toolbar along the top (unfolded, in portrait).
    case statusRow
}

/// Hosts the board so that on iPhone Duo its header becomes native toolbar items in the system's
/// bar, where iOS 27.1 leaves room for one anyway: `actions` (the buttons) and `info` (the date,
/// weather, and so on, one item each: the bar gives every item a slot of its own size and centres
/// anything larger on it). `content` is told where they went, and shows the header itself when
/// they did not: on other devices, before iOS 27.1, and while `allowsBar` is false (dimmed, where
/// bright icons would defeat the dim). There the navigation bar stays hidden, so the board looks
/// as it always has. With `isFullScreen` there is no navigation container at all: even hidden,
/// its bar keeps the column free, where full screen should fill it.
struct VerticalBarHost<Content: View, Actions: View>: View {
    let isFullScreen: Bool
    let allowsBar: Bool
    /// Changes when the board moves to a display of another size, e.g. on folding. While the
    /// header is in a bar, the board is rebuilt then (its open sheets close): an overflowing bar
    /// collapses into one empty capsule and stays collapsed once its items shrink back, and
    /// mid-fold it briefly holds the larger display's items. Without a bar nothing is rebuilt, so
    /// rotating any other phone keeps its sheets.
    let layoutID: AnyHashable
    @ViewBuilder let content: (_ placement: BarPlacement) -> Content
    @ViewBuilder let actions: () -> Actions
    let info: (_ placement: BarPlacement) -> [AnyView]

    var body: some View {
        if #available(iOS 27.1, *), !isFullScreen {
            NavigationView {
                VerticalBarReader(allowsBar: allowsBar, layoutID: layoutID, content: content, actions: actions, info: info)
            }
            .navigationViewStyle(.stack)
        } else {
            content(.none)
        }
    }
}

@available(iOS 27.1, *)
private struct VerticalBarReader<Content: View, Actions: View>: View {
    let allowsBar: Bool
    let layoutID: AnyHashable
    let content: (BarPlacement) -> Content
    let actions: () -> Actions
    let info: (BarPlacement) -> [AnyView]
    /// Set only where the system shows bars vertically.
    @Environment(\.toolbarVerticalEdge) private var verticalEdge
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.verticalSizeClass) private var verticalSizeClass

    private var placement: BarPlacement {
        guard allowsBar else { return .none }
        if verticalEdge != nil { return .vertical }
        // A phone regular both ways is iPhone Duo unfolded in portrait, the one place a top bar
        // shares the status bar's row instead of adding a row of its own below it. Every other
        // iPhone is compact one way or the other, and keeps the header. The idiom only tells it
        // from an iPad, where a top bar would add a row; it chooses the bar, not the layout.
        let isUnfoldedPortrait = UIDevice.current.userInterfaceIdiom == .phone
            && horizontalSizeClass == .regular && verticalSizeClass == .regular
        return isUnfoldedPortrait ? .statusRow : .none
    }

    var body: some View {
        let placement = placement
        content(placement)
            // The column is beside the board, but the container still insets it from the top as
            // if a bar were there; the status bar is in the column too, so nothing is above.
            .ignoresSafeArea(.container, edges: placement == .vertical ? .top : [])
            .toolbar {
                switch placement {
                case .vertical:
                    ToolbarItemGroup(placement: .primaryAction) { actions() }
                        .axisBehavior(.verticalPreferred)
                    ToolbarSpacer(.fixed, placement: .primaryAction)
                    let items = info(.vertical)
                    ForEach(items.indices, id: \.self) { index in
                        // Information, not controls, so without the glass the buttons sit on.
                        ToolbarItem(placement: .primaryAction) { items[index] }
                            .axisBehavior(.verticalPreferred)
                            .sharedBackgroundVisibility(.hidden)
                    }
                case .statusRow:
                    let items = info(.statusRow)
                    ForEach(items.indices, id: \.self) { index in
                        ToolbarItem(placement: .topBarLeading) { items[index] }
                            .sharedBackgroundVisibility(.hidden)
                    }
                    ToolbarItemGroup(placement: .primaryAction) { actions() }
                case .none:
                    ToolbarItemGroup(placement: .primaryAction) { EmptyView() }
                }
            }
            .navigationBarHidden(placement == .none)
            .id(placement == .none ? AnyHashable(BarPlacement.none) : layoutID)
    }
}

private struct PixelShift: ViewModifier {
    let level: BurnInProtectionLevel
    @State private var offset = CGSize.zero

    func body(content: Content) -> some View {
        content
            .offset(offset)
            .task(id: level) {
                guard level != .off else {
                    offset = .zero
                    return
                }
                let schedule = PixelShiftSchedule.shared
                while !Task.isCancelled {
                    let next = schedule.nextOffset(level: level)
                    withAnimation(.easeInOut(duration: Double(schedule.transitionMillis) / 1000)) {
                        offset = CGSize(width: CGFloat(next.xDp), height: CGFloat(next.yDp))
                    }
                    try? await Task.sleep(nanoseconds: UInt64(schedule.intervalMillis) * 1_000_000)
                }
            }
    }
}
