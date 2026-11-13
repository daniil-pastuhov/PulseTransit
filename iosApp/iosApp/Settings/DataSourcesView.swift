import Shared
import SwiftUI

/// "Data sources", pushed from Settings: every transport company whose timetables reach Entur,
/// searchable, so people can check whether one they travel with is covered. The counterpart of
/// Android's DataSourcesScreen; a row opens the company's website.
struct DataSourcesView: View {
    @StateObject private var model = DataSourcesModel()
    @Environment(\.openURL) private var openURL

    var body: some View {
        let state = model.state
        List {
            Section(footer: Text(L("data_sources_intro"))) {}
            content(state)
            Section(footer: VStack(alignment: .leading, spacing: 8) {
                Text(L("data_sources_check"))
                Text(L("data_sources_weather"))
            }) {}
        }
        .listStyle(.insetGrouped)
        .searchable(
            text: Binding(get: { state.query }, set: { model.viewModel.setQuery(query: $0) }),
            prompt: Text(L("data_sources_search"))
        )
        .animation(.default, value: state.shown.map(\.name))
        .navigationTitle(L("data_sources_title"))
        .navigationBarTitleDisplayMode(.inline)
    }

    @ViewBuilder
    private func content(_ state: DataSourcesViewModel.UiState) -> some View {
        if state.isLoading {
            // The intro is local text, so only the list is a skeleton.
            SourcesSkeleton()
        } else if state.failed {
            // T5 in the list's place: the list is the content and nothing is cached.
            EmptyListMessage(symbol: "icloud.slash", title: L("data_sources_failed"), detail: L("data_sources_failed_body")) {
                Button(L("try_again")) { model.viewModel.retry() }
                    .buttonStyle(.borderedProminent)
                    .padding(.top, 4)
            }
        } else if state.noMatches {
            // A real answer, not an error: the company isn't covered.
            EmptyListMessage(
                symbol: "magnifyingglass",
                title: L("data_sources_no_match", state.query.trimmingCharacters(in: .whitespaces)),
                detail: ""
            )
        } else {
            Section(header: state.query.isEmpty ? Text(L("data_sources_count", state.all.count)).font(.pulse(.subheadline, weight: .semibold)).foregroundColor(Palette.primary).textCase(nil) : nil) {
                ForEach(state.shown, id: \.name) { source in
                    row(source)
                }
            }
        }
    }

    @ViewBuilder
    private func row(_ source: DataSource) -> some View {
        let label = VStack(alignment: .leading, spacing: 2) {
            Text(source.name).foregroundColor(.primary)
            Text(source.modes.map { L(Self.modeKey($0)) }.joined(separator: ", "))
                .font(.pulse(.caption))
                .foregroundColor(.secondary)
        }
        if let link = source.url.flatMap(URL.init(string:)) {
            Button { openURL(link) } label: { label }
        } else {
            label
        }
    }

    /// The same labels as Android, shared with Settings' "Travel by".
    private static func modeKey(_ mode: DataSourceMode) -> String {
        switch mode {
        case .bus: return "route_group_bus"
        case .coach: return "route_mode_express_bus"
        case .train: return "route_group_train"
        case .metro: return "route_mode_metro"
        case .tram: return "route_mode_tram"
        case .boat: return "route_group_boat"
        case .plane: return "route_mode_plane"
        case .cableCar: return "route_mode_cable_car"
        case .onDemand: return "route_mode_on_demand"
        }
    }
}

private struct SourcesSkeleton: View {
    @State private var pulse = false

    var body: some View {
        Section {
            ForEach(0 ..< 6, id: \.self) { _ in
                VStack(alignment: .leading, spacing: 6) {
                    PlaceholderBlock(width: 140, height: 14, pulse: pulse)
                    PlaceholderBlock(width: 200, height: 11, pulse: pulse)
                }
                .padding(.vertical, 4)
            }
        }
        .modifier(PlaceholderPulse(pulse: $pulse))
        .accessibilityHidden(true)
    }
}
