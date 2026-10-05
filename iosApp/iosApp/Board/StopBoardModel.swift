import Foundation
import Shared

/// Holds the shared `StopBoardViewModel` for one board screen and republishes its state to SwiftUI.
@MainActor
final class StopBoardModel: ObservableObject {
    @Published private(set) var state: StopBoardViewModel.UiState

    let viewModel: StopBoardViewModel
    private let owner = ViewModelOwner()
    private var observation: Task<Void, Never>?

    init() {
        viewModel = PulseTransitIos.shared.stopBoardViewModel(owner: owner)
        state = viewModel.uiState.value
        let uiState = viewModel.uiState
        observation = Task { [weak self] in
            for await state in uiState {
                self?.state = state
            }
        }
    }

    deinit {
        observation?.cancel()
        // Cancels viewModelScope, which stops the board's clock and auto-refresh loops.
        owner.clear()
    }
}
