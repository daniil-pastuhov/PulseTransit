import Shared
import SwiftUI

/// The short intro to the chosen mode, between the first-launch choice and the board, the
/// counterpart of Android's IntroScreen. Every page can be skipped, which ends the whole intro, as
/// does "Get started" on the last page.
struct IntroView: View {
    let pages: [IntroPage]
    let onFinish: () -> Void

    @State private var current = 0

    private var isLast: Bool { current >= pages.count - 1 }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                // Kept in place on the last page, hidden, so the pages do not shift up.
                Button(L("intro_skip"), action: onFinish)
                    .foregroundColor(Palette.primary)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .opacity(isLast ? 0 : 1)
                    .disabled(isLast)
                    .accessibilityHidden(isLast)
            }
            TabView(selection: $current) {
                ForEach(Array(pages.enumerated()), id: \.offset) { index, page in
                    IntroPageView(page: page).tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            PageDots(count: pages.count, current: current)
                .padding(.vertical, 16)
            Button {
                if isLast {
                    onFinish()
                } else {
                    withAnimation { current += 1 }
                }
            } label: {
                Text(L(isLast ? "intro_done" : "intro_next"))
                    .font(.pulse(.headline))
                    .foregroundColor(.white)
                    .frame(maxWidth: 480)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Capsule().fill(Palette.primary).frame(maxWidth: 480))
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 24)
            .padding(.bottom, 24)
        }
        .foregroundColor(Palette.textPrimary)
        .background(Palette.background.ignoresSafeArea())
    }
}

private struct IntroPageView: View {
    let page: IntroPage

    var body: some View {
        let content = IntroPageView.content(page)
        // Centred in the page, and scrolling only when large text makes it taller than that.
        GeometryReader { proxy in
            ScrollView {
                pageContent(content).frame(maxWidth: .infinity, minHeight: proxy.size.height)
            }
        }
    }

    private func pageContent(_ content: (title: String, body: String)) -> some View {
        VStack(spacing: 20) {
            IntroIllustration(page: page)
            Text(L(content.title))
                .font(.pulse(.title2, weight: .bold))
                .multilineTextAlignment(.center)
            Text(L(content.body))
                .font(.pulse(.body))
                .foregroundColor(Palette.textSecondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 480)
        }
        .padding(.horizontal, 24)
    }

    private static func content(_ page: IntroPage) -> (title: String, body: String) {
        switch page {
        case .nearestStop: return ("intro_nearest_stop_title", "intro_nearest_stop_body")
        case .routesFromHere: return ("intro_routes_title", "intro_routes_body")
        case .resize: return ("intro_resize_title", "intro_resize_body")
        case .chooseStops: return ("intro_choose_stops_title", "intro_choose_stops_body")
        case .fastestRoutes: return ("intro_fastest_routes_title", "intro_fastest_routes_body")
        case .tapForDetails: return ("intro_details_title", "intro_details_body")
        case .screenCare: return ("intro_screen_care_title", "intro_screen_care_body")
        }
    }
}

private struct PageDots: View {
    let count: Int
    let current: Int

    var body: some View {
        HStack(spacing: 8) {
            ForEach(0..<count, id: \.self) { index in
                Circle()
                    .fill(index == current ? Palette.primary : Palette.textSecondary.opacity(0.35))
                    .frame(width: 8, height: 8)
            }
        }
        .accessibilityElement()
        .accessibilityLabel(L("intro_step", current + 1, count))
    }
}
