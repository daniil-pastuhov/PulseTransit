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
