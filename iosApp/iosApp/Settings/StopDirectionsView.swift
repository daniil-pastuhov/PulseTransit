import Shared
import SwiftUI

/// Which directions of a stop the board shows, as Android's StopDirectionsScreen: tick platforms,
/// and with none ticked it shows all. The list is the screen's only content, so a failed load (T5)
/// fills it, with Try again; the navigation bar stays, so Back still works.
struct StopDirectionsView: View {
    @ObservedObject var model: SettingsModel
    let stopId: String

    var body: some View {
        let state = model.state
        let viewModel = model.viewModel
        let stop = state.favorites.first { $0.id == stopId }
        let withQuays = state.favoriteStopsWithQuays[stopId]
        let name = stop?.name ?? withQuays?.name ?? ""
        let selected = state.selectedQuays[stopId] ?? []
        List {
            if let withQuays, !withQuays.quays.isEmpty {
                Section(header: header(name: name, withQuays: withQuays, selected: selected)) {
                    EmptyView()
                }
                ForEach(QuaySectionKt.quaySections(stop: withQuays), id: \.self) { section in
                    Section(header: section.title.map { Text($0).textCase(nil) }) {
                        ForEach(section.quays, id: \.id) { quay in
                            QuayRow(
                                quay: quay,
                                label: quayLabel(quay, stopName: name, sectionTitle: section.title),
                                // With none chosen, every platform is shown but none is ticked:
                                // ticking one then chooses just that one, as the intro says.
                                isOn: selected.contains(quay.id)
                            ) { viewModel.toggleQuay(stopId: stopId, quayId: quay.id) }
                        }
                    }
                }
            } else if withQuays != nil {
                Text(L("directions_none", name)).font(.pulse(.footnote)).foregroundColor(Palette.textSecondary)
            } else if state.quaysFailed.contains(stopId) {
                EmptyListMessage(symbol: "icloud.slash", title: L("directions_failed_title"), detail: L("directions_failed_body", name)) {
                    Button(L("try_again")) { viewModel.retryQuays(stopId: stopId) }
                        .buttonStyle(.borderedProminent)
                        .padding(.top, 4)
                }
            } else {
                Section(header: Text(L("directions_intro", name)).font(.pulse(.footnote)).textCase(nil)) {
                    QuaySkeleton()
                }
            }
        }
        .navigationTitle(L("directions_title"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                VStack(spacing: 0) {
                    Text(L("directions_title")).font(.pulse(.headline))
                    Text(name).font(.pulse(.caption)).foregroundColor(Palette.textSecondary)
                }
                .accessibilityElement(children: .combine)
            }
        }
    }

    /// The intro, then "Showing all directions" or "Showing 2 of 5 platforms" with Show all.
    private func header(name: String, withQuays: StopPlace, selected: Set<String>) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(L("directions_intro", name)).font(.pulse(.footnote)).foregroundColor(Palette.textSecondary)
            if selected.isEmpty {
                Label(L("showing_all_directions"), systemImage: "checkmark.circle")
                    .font(.pulse(.subheadline, weight: .semibold))
                    .foregroundColor(Palette.primary)
            } else {
                // A saved choice can hold platforms Entur no longer lists; only the listed ones count.
                let chosen = withQuays.quays.filter { selected.contains($0.id) }.count
                HStack {
                    Label(L("directions_showing_some", chosen, withQuays.quays.count), systemImage: "line.3.horizontal.decrease.circle")
                        .font(.pulse(.subheadline, weight: .semibold))
                        .foregroundColor(Palette.primary)
                    Spacer()
                    Button(L("directions_show_all")) { model.viewModel.showAllQuays(stopId: stopId) }
                        .font(.pulse(.subheadline, weight: .semibold))
                }
            }
        }
        .textCase(nil)
    }
}

/// One platform: its tick, name, the lines it serves and where they go.
private struct QuayRow: View {
    let quay: Quay
    let label: String
    let isOn: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: isOn ? "checkmark.square.fill" : "square")
                    .font(.pulse(.title3))
                    .foregroundColor(isOn ? Palette.primary : Palette.textSecondary)
                VStack(alignment: .leading, spacing: 4) {
                    Text(label).font(.pulse(.body)).foregroundColor(Palette.textPrimary)
                    if !quay.lines.isEmpty {
                        HStack(spacing: 4) {
                            ForEach(quay.lines.prefix(6), id: \.self) { LinePill(line: $0, mode: nil, small: true) }
                            if quay.lines.count > 6 {
                                Text("+\(quay.lines.count - 6)").font(.pulse(.caption)).foregroundColor(Palette.textSecondary)
                            }
                        }
                    }
                    if !quay.destinations.isEmpty {
                        Text(L("directions_towards", quay.destinations.joined(separator: ", ")))
                            .font(.pulse(.footnote))
                            .foregroundColor(Palette.textSecondary)
                            .lineLimit(2)
                    }
                }
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isOn ? [.isButton, .isSelected] : .isButton)
    }
}

private struct QuaySkeleton: View {
    @State private var pulse = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            ForEach(0 ..< 4, id: \.self) { _ in
                HStack(spacing: 12) {
                    PlaceholderBlock(width: 20, height: 20, pulse: pulse)
                    VStack(alignment: .leading, spacing: 6) {
                        PlaceholderBlock(width: 100, height: 14, pulse: pulse)
                        PlaceholderBlock(width: 180, height: 11, pulse: pulse)
                    }
                }
            }
        }
        .padding(.vertical, 8)
        .modifier(PlaceholderPulse(pulse: $pulse))
        .accessibilityHidden(true)
    }
}

/// A platform's name: "Platform 2", else its description or name, unless that only repeats the
/// stop's or section's name; else the end of its id. As Android's `quayLabel`.
func quayLabel(_ quay: Quay, stopName: String, sectionTitle: String?) -> String {
    if let platform = quay.publicCode, !platform.isEmpty { return L("platform_label", platform) }
    if let description = quay.quayDescription, !description.isEmpty { return description }
    if let name = quay.name, !name.isEmpty, name != stopName, name != sectionTitle { return name }
    return L("direction_id", String(quay.id.suffix(3)))
}
