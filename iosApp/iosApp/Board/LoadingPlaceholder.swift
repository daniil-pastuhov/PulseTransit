import SwiftUI

/// Blur-up placeholder, as on Android: the skeleton is drawn blurred, the way content looks before
/// it comes into focus, with a sharp spinner and label on top so it reads unmistakably as loading.
/// Only the label reaches VoiceOver.
struct BlurredPlaceholder<Skeleton: View>: View {
    let label: String
    var tint: Color = Palette.textSecondary
    @ViewBuilder let skeleton: () -> Skeleton

    var body: some View {
        ZStack {
            skeleton()
                .blur(radius: 6)
                .accessibilityHidden(true)
            HStack(spacing: 8) {
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(tint)
                Text(label)
                    .font(.subheadline.weight(.medium))
                    .foregroundColor(tint)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
    }
}

/// A rounded skeleton block standing in for a piece of text or a badge.
struct PlaceholderBlock: View {
    var width: CGFloat?
    var height: CGFloat
    var cornerRadius: CGFloat = 4
    var color: Color = Palette.textSecondary
    let pulse: Bool

    var body: some View {
        RoundedRectangle(cornerRadius: cornerRadius)
            .fill(color.opacity(pulse ? 0.45 : 0.15))
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
