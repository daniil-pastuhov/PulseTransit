import Foundation
import Shared

/// Holds one shared ViewModel for a SwiftUI screen and republishes its state.
///
/// Clearing the owner in `deinit` cancels `viewModelScope`; for the board that is what stops its
/// clock and auto-refresh loops, so a screen must never outlive its model by holding the ViewModel.
@MainActor
final class ScreenModel<ViewModel: AnyObject, State>: ObservableObject {
    @Published private(set) var state: State

    let viewModel: ViewModel
    private let owner: ViewModelOwner
    private var observation: Task<Void, Never>?

    init(
        create: (ViewModelOwner) -> ViewModel,
        stateFlow: (ViewModel) -> SkieSwiftStateFlow<State>
    ) {
        let owner = ViewModelOwner()
        let viewModel = create(owner)
        let flow = stateFlow(viewModel)
        self.owner = owner
        self.viewModel = viewModel
        state = flow.value
        observation = Task { [weak self] in
            for await state in flow {
                self?.state = state
            }
        }
    }

    deinit {
        observation?.cancel()
        owner.clear()
    }
}

typealias StopBoardModel = ScreenModel<StopBoardViewModel, StopBoardViewModel.UiState>
typealias SettingsModel = ScreenModel<SettingsViewModel, SettingsViewModel.UiState>
typealias ModeSelectionModel = ScreenModel<ModeSelectionViewModel, ModeSelectionViewModel.UiState>

extension ScreenModel where ViewModel == StopBoardViewModel, State == StopBoardViewModel.UiState {
    convenience init() {
        self.init(create: { PulseTransitIos.shared.stopBoardViewModel(owner: $0) }, stateFlow: { $0.uiState })
    }
}

extension ScreenModel where ViewModel == SettingsViewModel, State == SettingsViewModel.UiState {
    convenience init() {
        self.init(create: { PulseTransitIos.shared.settingsViewModel(owner: $0) }, stateFlow: { $0.uiState })
    }
}

extension ScreenModel where ViewModel == ModeSelectionViewModel, State == ModeSelectionViewModel.UiState {
    convenience init() {
        self.init(create: { PulseTransitIos.shared.modeSelectionViewModel(owner: $0) }, stateFlow: { $0.uiState })
    }
}
