import SwiftUI

/// A rounded skeleton block standing in for a piece of text or a badge: a solid surface-container
/// block that breathes (a translucent tint vanished on white cards), as on Android.
struct PlaceholderBlock: View {
    var width: CGFloat?
    var height: CGFloat
    var cornerRadius: CGFloat = FjordTokens.radiusSm
    var color: Color = Palette.surfaceContainer
    let pulse: Bool

    var body: some View {
        RoundedRectangle(cornerRadius: cornerRadius)
            .fill(color.opacity(pulse ? 1 : 0.7))
            .frame(width: width, height: height)
    }
}

/// Drives a skeleton's pulse; start it from `onAppear`.
struct PlaceholderPulse: ViewModifier {
    @Binding var pulse: Bool

    func body(content: Content) -> some View {
        content.onAppear {
            withAnimation(.linear(duration: 0.9).repeatForever(autoreverses: true)) { pulse = true }
        }
    }
}

/// Thin indeterminate bar over content that is being refreshed in place. iOS 15's linear
/// `ProgressView` is determinate only, hence a small custom sweep.
struct RefreshBar: View {
    var color: Color = Palette.primary
    @State private var phase: CGFloat = -0.3

    var body: some View {
        GeometryReader { geometry in
            Capsule()
                .fill(color)
                .frame(width: geometry.size.width * 0.3)
                .offset(x: geometry.size.width * phase)
        }
        .frame(height: 2)
        .clipped()
        .onAppear {
            withAnimation(.linear(duration: 1.1).repeatForever(autoreverses: false)) { phase = 1 }
        }
        .accessibilityHidden(true)
    }
}
