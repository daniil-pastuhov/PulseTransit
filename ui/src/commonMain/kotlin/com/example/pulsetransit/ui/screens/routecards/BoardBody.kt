package com.example.pulsetransit.ui.screens.routecards

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.BoardRouteAlternatives
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseTheme
import com.example.pulsetransit.ui.layout.BoardLayout
import com.example.pulsetransit.ui.layout.RouteCardBands
import com.example.pulsetransit.ui.layout.RouteCardDensity
import com.example.pulsetransit.ui.layout.RouteCardFooter
import com.example.pulsetransit.ui.layout.RouteCardLayout
import com.example.pulsetransit.ui.screens.RouteMoreInDetailsRow
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import kotlinx.coroutines.delay
import kotlin.time.Clock

/**
 * The board below its header and notices in board mode: a card per destination routes are shown
 * for (the one searched for from the board first, each as soon as its own route arrives), then the
 * stop panels, drawn by [panels] with the modifier it is given, taking the rest. [gap] is the
 * board's gap between its blocks.
 *
 * Every rule here is [RouteCardLayout]'s (see there for the whole sequence): the cards form a grid
 * from the width the body has, never the device's; each card's size class comes from its own
 * width; all cards take the richest [RouteCardDensity] whose collapsed cards fit above the panels'
 * [BoardLayout.MIN_PANEL_HEIGHT_DP], measured as drawn, and scroll only when not even one line
 * each fits; expanded cards list the alternatives that fit
 * ([BoardRouteAlternatives.visibleRows]); and a grid row's cards are as tall as its tallest
 * (a collapsed card beside an expanded one only as tall as the row collapsed, at its top).
 * Departed alternatives drop off on a [ROUTE_CARDS_TICK_MILLIS] tick, as the cache drops them.
 */
@Composable
fun BoardBody(
    uiState: StopBoardViewModel.UiState,
    gap: Dp,
    actions: RouteCardActions,
    panels: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val destinations =
        uiState.routeDestinations.filter {
            it.id in uiState.fastestTrips ||
                it.id in uiState.loadingTripDestinationIds ||
                it.id in uiState.failedTripDestinationIds ||
                it.id == uiState.temporaryDestination?.id
        }
    val nowMillis by produceState(Clock.System.now().toEpochMilliseconds()) {
        while (true) {
            delay(ROUTE_CARDS_TICK_MILLIS)
            value = Clock.System.now().toEpochMilliseconds()
        }
    }
    val fontScale = LocalDensity.current.fontScale

    BoxWithConstraints(modifier = modifier) {
        if (destinations.isEmpty()) {
            panels(Modifier.fillMaxSize())
            return@BoxWithConstraints
        }
        val cardGap = FjordDimens.space3
        val columns = RouteCardLayout.columns(destinations.size, maxWidth.value, cardGap.value, fontScale)
        val widths = RouteCardLayout.cardWidths(destinations.size, columns, maxWidth.value, cardGap.value)
        val own =
            destinations.mapIndexed { index, destination ->
                RouteCardBands.own(
                    isSearched = destination.id == uiState.temporaryDestination?.id,
                    hasRoute = !uiState.fastestTrips[destination.id].isNullOrEmpty(),
                    size = RouteCardLayout.size(widths[index], fontScale),
                    hasAlternatives = uiState.canToggleRouteAlternatives(destination.id, nowMillis),
                )
            }
        val footers = RouteCardBands.footers(own, columns)
        val specs =
            destinations.mapIndexed { index, destination ->
                RouteCardSpec(
                    destination = destination,
                    size = RouteCardLayout.size(widths[index], fontScale),
                    narrow = RouteCardLayout.isNarrow(widths[index], fontScale),
                    footer = footers[index],
                    alternatives =
                        if (uiState.canToggleRouteAlternatives(destination.id, nowMillis)) {
                            BoardRouteAlternatives.alternatives(uiState.fastestTrips[destination.id], nowMillis)
                        } else {
                            emptyList()
                        },
                    expanded = uiState.showsRouteAlternatives(destination.id),
                )
            }
        val cardsMax = (maxHeight - gap - BoardLayout.MIN_PANEL_HEIGHT_DP.dp).coerceAtLeast(0.dp)

        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            val scroll = rememberScrollState()
            RouteCardGrid(
                specs = specs,
                columns = columns,
                cardGap = cardGap,
                maxHeight = cardsMax,
                uiState = uiState,
                nowMillis = nowMillis,
                actions = actions,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = cardsMax)
                        .fadeWhenScrollable(scroll)
                        .verticalScroll(scroll),
            )
            panels(Modifier.fillMaxWidth().weight(1f))
        }
    }
}

/**
 * The cards' grid. Each card is measured as it would be drawn, collapsed, at each density in turn
 * until all fit [maxHeight] ([RouteCardLayout.fits]), and at the last one otherwise (the area
 * around scrolls then). At [RouteCardDensity.FULL] the expanded cards' alternative rows are
 * measured too, the tallest standing for every row in [BoardRouteAlternatives.visibleRows]. Then
 * the cards are drawn at the density chosen, each as tall as its grid row
 * ([RouteCardLayout.cardHeights]).
 */
@Composable
private fun RouteCardGrid(
    specs: List<RouteCardSpec>,
    columns: Int,
    cardGap: Dp,
    maxHeight: Dp,
    uiState: StopBoardViewModel.UiState,
    nowMillis: Long,
    actions: RouteCardActions,
    modifier: Modifier = Modifier,
) {
    val unmeasured = BoardRouteAlternatives.unmeasured(1).single()
    // Each probe's journey and band heights, kept with the probe: a probe measured again with the
    // same constraints is not measured anew, and keeps what it reported.
    val probes = remember { HashMap<ProbeSlot, CardMeasure>() }
    SubcomposeLayout(modifier = modifier) { constraints ->
        val gapPx = cardGap.roundToPx()
        val maxPx = maxHeight.toPx()
        val gridRows = specs.indices.chunked(columns)
        // Each card's width in pixels: a grid row's cards share its width, the first ones taking
        // the pixels left over.
        val widths =
            gridRows.flatMap { row ->
                val available = (constraints.maxWidth - gapPx * (row.size - 1)).coerceAtLeast(0)
                row.mapIndexed { index, _ -> available / row.size + if (index < available % row.size) 1 else 0 }
            }
        val nextBlockMax = ((maxHeight - cardGap * (gridRows.size - 1)) / gridRows.size).coerceAtLeast(0.dp)

        val densities = RouteCardBands.densities(specs.map { it.footer })
        var chosen = densities.last()
        var heads: List<Int> = emptyList()
        var parts: List<CardMeasure> = emptyList()
        for (density in densities) {
            parts = specs.indices.map { probes.getOrPut(ProbeSlot(density, it)) { CardMeasure() } }
            heads =
                specs.indices.map { index ->
                    subcompose(ProbeSlot(density, index)) {
                        BoardRouteCard(
                            spec = specs[index],
                            uiState = uiState,
                            density = density,
                            visible = unmeasured,
                            nowMillis = nowMillis,
                            actions = actions,
                            nextBlockMaxHeight = nextBlockMax,
                            live = false,
                            measured = parts[index],
                        )
                    }.single().measure(Constraints.fixedWidth(widths[index])).height
                }
            chosen = density
            if (RouteCardLayout.fits(heads.map { it.toFloat() }, columns, gapPx.toFloat(), maxPx)) break
        }

        // The expanded cards' rows, measured one by one, and how many of them fit.
        val expanded = specs.map { chosen == RouteCardDensity.FULL && it.footer == RouteCardFooter.TOGGLE && it.expanded }
        val rowHeights =
            specs.indices.map { index ->
                if (!expanded[index]) return@map emptyList()
                subcompose(RowsSlot(index)) {
                    val spec = specs[index]
                    val metrics = routeCardMetrics(spec.size)
                    val divider = PulseTheme.colors.onRoute.copy(alpha = DIVIDER_ALPHA)
                    spec.alternatives.forEach { trip ->
                        Column {
                            HorizontalDivider(color = divider)
                            AlternativeRow(trip = trip, size = spec.size, metrics = metrics, nowMillis = nowMillis, onClick = {})
                        }
                    }
                    Column {
                        HorizontalDivider(color = divider)
                        RouteMoreInDetailsRow(hidden = spec.alternatives.size, onClick = {})
                    }
                }.map { it.measure(Constraints.fixedWidth(widths[index])).height }
            }
        val visible =
            if (expanded.none { it }) {
                specs.map { unmeasured }
            } else {
                BoardRouteAlternatives.visibleRows(
                    alternativeCounts = specs.indices.map { if (expanded[it]) specs[it].alternatives.size else 0 },
                    columns = columns,
                    spareHeight = maxPx - RouteCardLayout.gridHeight(heads.map { it.toFloat() }, columns, gapPx.toFloat()),
                    rowHeight = rowHeights.maxOf { it.dropLast(1).maxOrNull() ?: 0 }.toFloat(),
                    moreRowHeight = rowHeights.maxOf { it.lastOrNull() ?: 0 }.toFloat(),
                )
            }
        // Each card's rows as shown: how many, and how tall together.
        val shownRows =
            specs.indices.map { index ->
                val rows = rowHeights[index]
                val shown = visible[index]
                if (rows.isEmpty() || !shown.showsRows) {
                    emptyList()
                } else {
                    rows.take(shown.shown) + if (shown.showsMoreRow) listOf(rows.last()) else emptyList()
                }
            }
        val natural = specs.indices.map { heads[it] + shownRows[it].sum() }
        val gridRowHeights = RouteCardLayout.rowHeights(natural.map { it.toFloat() }, columns).map { it.toInt() }
        // A card without rows beside one with them keeps its collapsed height, at the row's top.
        val cardHeights =
            RouteCardLayout
                .cardHeights(
                    natural = natural.map { it.toFloat() },
                    collapsed = heads.map { it.toFloat() },
                    hasRows = shownRows.map { it.isNotEmpty() },
                    columns = columns,
                ).map { it.toInt() }
        val headMax = RouteCardLayout.rowHeights(parts.map { it.head.toFloat() }, columns)
        val cards =
            specs.indices.map { index ->
                val height = cardHeights[index]
                val stretch =
                    RouteCardLayout.stretch(
                        head = parts[index].head.toFloat(),
                        rowHeadMax = headMax[index / columns],
                        rows = shownRows[index].size,
                        rowsHeight = shownRows[index].sum().toFloat(),
                        footer = parts[index].footer.toFloat(),
                        rowHeight = height.toFloat(),
                    )
                subcompose(CardSlot(specs[index].destination.id)) {
                    BoardRouteCard(
                        spec = specs[index],
                        uiState = uiState,
                        density = chosen,
                        visible = visible[index],
                        nowMillis = nowMillis,
                        actions = actions,
                        nextBlockMaxHeight = nextBlockMax,
                        live = true,
                        headBand = stretch.headBand.toInt(),
                        footerBand = stretch.footerBand.toInt(),
                    )
                }.single().measure(Constraints.fixed(widths[index], height))
            }
        val height = gridRowHeights.sum() + gapPx * (gridRows.size - 1)
        layout(constraints.maxWidth, height) {
            var y = 0
            gridRows.forEachIndexed { rowIndex, row ->
                var x = 0
                row.forEach { index ->
                    cards[index].place(x, y)
                    x += widths[index] + gapPx
                }
                y += gridRowHeights[rowIndex] + gapPx
            }
        }
    }
}

// The grid's subcomposition slots: a card measured at a density, its alternative rows, and as drawn.
private data class ProbeSlot(
    val density: RouteCardDensity,
    val index: Int,
)

private data class RowsSlot(
    val index: Int,
)

private data class CardSlot(
    val destinationId: String,
)

/**
 * Fades the bottom of a scrolling area while there is more below, so cards cut short by the last
 * resort scroll read as more to come rather than as broken.
 */
private fun Modifier.fadeWhenScrollable(scroll: ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (scroll.canScrollForward) {
                val fade = FADE_HEIGHT.toPx().coerceAtMost(size.height)
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - fade, endY = size.height),
                    topLeft = Offset(0f, size.height - fade),
                    size = Size(size.width, fade),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

/** How often the route cards drop departed alternatives. */
private const val ROUTE_CARDS_TICK_MILLIS = 15_000L

private val FADE_HEIGHT = 32.dp
