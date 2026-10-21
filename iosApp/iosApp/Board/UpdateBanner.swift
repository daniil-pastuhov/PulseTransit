import Shared
import SwiftUI

/// Tells the user that the App Store has a newer version of the app, under the board's header.
/// The counterpart of Android's `UpdateBanner`.
struct UpdateBanner: View {
    let update: AppUpdate
    let onDismiss: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "arrow.down.app")
                .accessibilityHidden(true)
            Text(L("update_available"))
                .font(.subheadline)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button(L("dismiss_update"), action: onDismiss)
                .foregroundColor(Palette.textPrimary)
            if let url = update.storeUrl.flatMap(URL.init(string:)) {
                Button(L("update_app")) { UIApplication.shared.open(url) }
                    .font(.subheadline.bold())
                    .foregroundColor(Palette.primary)
            }
        }
        .buttonStyle(.borderless)
        .font(.subheadline)
        .foregroundColor(Palette.textPrimary)
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Palette.primary.opacity(0.12), ignoresSafeAreaEdges: .horizontal)
    }
}
