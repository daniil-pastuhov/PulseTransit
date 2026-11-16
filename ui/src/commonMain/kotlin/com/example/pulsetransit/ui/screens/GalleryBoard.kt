package com.example.pulsetransit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.example.pulsetransit.data.ThemeMode
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseTransitTheme
import com.example.pulsetransit.ui.screens.routecards.BoardBody
import com.example.pulsetransit.ui.screens.routecards.RouteCardActions
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel

/**
 * The board-mode board as [MainScreen] draws it (dark theme, its paddings and gaps, the real
 * header, the route cards and stop panels, the footer), from [uiState] alone and with no
 * ViewModel: what the desktop app's offscreen gallery renderer (`:desktopApp:renderGallery`)
 * draws. No notices, followed journey, sheets or pull to refresh; nothing reacts to a tap.
 */
@Composable
fun GalleryBoard(
    uiState: StopBoardViewModel.UiState,
    modifier: Modifier = Modifier,
) {
    PulseTransitTheme(themeMode = ThemeMode.DARK) {
        BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val wide = maxWidth.value >= WIDE_HEADER_MIN_WIDTH_DP
            val gap = if (wide) FjordDimens.space4 else FjordDimens.space3
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                horizontal = if (wide) FjordDimens.space6 else FjordDimens.space3,
                                vertical = if (wide) FjordDimens.space4 else FjordDimens.space3,
                            ),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    BoardHeader(uiState = uiState, wide = wide, onCycleDateStyle = {}, onSearchRoute = {}, onOpenSettings = {})
                    BoardBody(
                        uiState = uiState,
                        gap = gap,
                        actions = RouteCardActions(),
                        panels = { panelModifier ->
                            ReorderableFavoriteList(
                                favorites = uiState.favorites,
                                departures = uiState.departures,
                                directionSummaries = uiState.directionSummaries,
                                showDelays = uiState.showDelays,
                                showMessages = uiState.showMessages,
                                loadingStopIds = uiState.loadingStopIds,
                                onReorder = { _, _ -> },
                                onFilterOut = {},
                                extendedStopIds = uiState.extendedStopIds,
                                failedStopIds = uiState.failedStopIds,
                                isOnline = uiState.isOnline,
                                onSearchFurtherAhead = {},
                                canLoadMoreStopIds = uiState.canLoadMoreStopIds,
                                loadingMoreStopIds = uiState.loadingMoreStopIds,
                                onLoadMore = {},
                                loadJourney = { _, _ -> emptyList() },
                                gap = gap,
                                modifier = panelModifier,
                            )
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    BoardFooter(uiState)
                }
            }
        }
    }
}
