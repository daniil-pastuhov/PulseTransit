import SwiftUI

// The Fjord design system's small components, the counterparts of Android's LinePill.kt and
// StatusComponents.kt.

/// A line number on its mode's colour. Never truncated: a four-character line ("RE30", "FLY2")
/// widens the pill instead. Hidden from accessibility; the row's label names the line and mode.
struct LinePill: View {
    let line: String
    let mode: String?
    var cancelled = false
    var minWidth: CGFloat = FjordTokens.pillW

    var body: some View {
        Text(line)
            .font(.pulse(role: FjordTokens.typePill, relativeTo: .subheadline))
            .strikethrough(cancelled)
            .foregroundColor(Palette.onLine)
            .lineLimit(1)
            .fixedSize()
            .padding(.horizontal, 6)
            .padding(.vertical, 4)
            .frame(minWidth: minWidth)
            .background(
                RoundedRectangle(cornerRadius: FjordTokens.radiusSm)
                    .fill(cancelled ? Palette.outline : Palette.transport(mode))
            )
            .accessibilityHidden(true)
    }
}

/// A transport mode's SF Symbol at text size, in the muted text colour; decorative.
struct TransportIcon: View {
    let mode: String?
    var color: Color = Palette.textSecondary

    var body: some View {
        Image(systemName: Palette.transportSymbol(mode))
            .foregroundColor(color)
            .accessibilityHidden(true)
    }
}

/// A delay as a small warning chip, "+3".
struct DelayChip: View {
    let minutes: Int

    var body: some View {
        Text("+\(minutes)")
            .font(.pulse(.caption, weight: .bold))
            .foregroundColor(Palette.onWarningContainer)
            .lineLimit(1)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(RoundedRectangle(cornerRadius: FjordTokens.radiusSm).fill(Palette.warningContainer))
    }
}

/// A callout's meaning, which picks its container colour and default icon.
enum CalloutTone {
    case info, warning, error, success

    fileprivate var colors: (container: Color, content: Color, symbol: String) {
        switch self {
        case .info: return (Palette.infoContainer, Palette.onInfoContainer, "info.circle")
        case .warning: return (Palette.warningContainer, Palette.onWarningContainer, "exclamationmark.circle")
        case .error: return (Palette.errorContainer, Palette.onErrorContainer, "exclamationmark.circle")
        case .success: return (Palette.successContainer, Palette.onSuccessContainer, "checkmark.circle.fill")
        }
    }
}

/// A T4 callout: a persistent inline status with an optional title and actions.
struct Callout<Actions: View>: View {
    let body_: String
    var tone: CalloutTone = .info
    var title: String?
    var symbol: String?
    let actions: Actions

    init(_ body: String, tone: CalloutTone = .info, title: String? = nil, symbol: String? = nil, @ViewBuilder actions: () -> Actions) {
        body_ = body
        self.tone = tone
        self.title = title
        self.symbol = symbol
        self.actions = actions()
    }

    var body: some View {
        let colors = tone.colors
        HStack(alignment: .top, spacing: FjordTokens.space3) {
            Image(systemName: symbol ?? colors.symbol).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                if let title {
                    Text(title).font(.pulse(.subheadline, weight: .semibold))
                }
                Text(body_).font(.pulse(.subheadline))
                HStack(spacing: FjordTokens.space2) { actions }
                    .font(.pulse(.subheadline, weight: .semibold))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .foregroundColor(colors.content)
        .padding(.horizontal, FjordTokens.space4)
        .padding(.vertical, FjordTokens.space3)
        .background(RoundedRectangle(cornerRadius: FjordTokens.radiusMd).fill(colors.container))
        .accessibilityElement(children: .combine)
    }
}

extension Callout where Actions == EmptyView {
    init(_ body: String, tone: CalloutTone = .info, title: String? = nil, symbol: String? = nil) {
        self.init(body, tone: tone, title: title, symbol: symbol) { EmptyView() }
    }
}

/// A transport notice from Entur, marked with a warning icon.
struct NoticeText: View {
    let notice: String
    var font: Font = .pulse(.footnote)

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 4) {
            Image(systemName: "exclamationmark.circle").accessibilityHidden(true)
            Text(notice)
        }
        .font(font)
        .foregroundColor(Palette.error)
    }
}
