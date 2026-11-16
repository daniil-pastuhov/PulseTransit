package com.example.pulsetransit.ui.screens.routecards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.TripTime
import com.example.pulsetransit.data.VisibleAlternatives
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.theme.PulseTheme
import com.example.pulsetransit.ui.components.PlaceholderBlock
import com.example.pulsetransit.ui.components.RefreshingIndicator
import com.example.pulsetransit.ui.components.TemporaryRouteActions
import com.example.pulsetransit.ui.components.leavesInText
import com.example.pulsetransit.ui.components.loadFailureIcon
import com.example.pulsetransit.ui.components.rememberPlaceholderPulse
import com.example.pulsetransit.ui.components.tripClockText
import com.example.pulsetransit.ui.components.tripDurationText
import com.example.pulsetransit.ui.layout.RouteCardBands
import com.example.pulsetransit.ui.layout.RouteCardDensity
import com.example.pulsetransit.ui.layout.RouteCardFitting
import com.example.pulsetransit.ui.layout.RouteCardFooter
import com.example.pulsetransit.ui.layout.RouteCardLayout
import com.example.pulsetransit.ui.layout.RouteCardSize
import com.example.pulsetransit.ui.resources.Res
import com.example.pulsetransit.ui.resources.error_retrying_automatically
import com.example.pulsetransit.ui.resources.fastest_route_unavailable
import com.example.pulsetransit.ui.resources.loading_fastest_route
import com.example.pulsetransit.ui.resources.no_route_to
import com.example.pulsetransit.ui.resources.no_route_to_arriving
import com.example.pulsetransit.ui.resources.route_arrival
import com.example.pulsetransit.ui.resources.route_arrive
import com.example.pulsetransit.ui.resources.route_leave
import com.example.pulsetransit.ui.resources.route_to
import com.example.pulsetransit.ui.screens.RouteMoreInDetailsRow
import com.example.pulsetransit.ui.screens.isoClock
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import org.jetbrains.compose.resources.stringResource

/** What the board decides about one route card before drawing it, the same at every density. */
@Immutable
internal class RouteCardSpec(
    val destination: StopPlace,
    val size: RouteCardSize,
    /** A compact card under [RouteCardLayout.NARROW_CARD_MAX_WIDTH_DP]: its title on a line of its own. */
    val narrow: Boolean,
    /**
     * Its band at [RouteCardDensity.FULL], from [RouteCardBands.footers];
     * [RouteCardFooter.ACTIONS] for the destination searched for from the board.
     */
    val footer: RouteCardFooter,
    /** Its alternatives still to leave, after the first route (none for the searched-for one). */
    val alternatives: List<TripInfo>,
    /** Whether its alternatives are open (the setting). */
    val expanded: Boolean,
)

/** The heights of a card's journey and band, in pixels, as its last measurement found them. */
internal class CardMeasure {
    var head = 0
    var footer = 0
}

/**
 * One destination's card on the board, drawn at [density] (see [RouteCardDensity]): its journey (or
 * why it has none), at [RouteCardDensity.FULL] its expanded alternatives ([visible] of them) and its
 * band, else its rail or its buttons at the trailing end. [headBand] and [footerBand] (pixels, 0 for
 * as tall as they need) are what its grid row gives its journey and band ([RouteCardLayout.stretch]);
 * a height left above that goes to its rows, or else its journey. [nextBlockMaxHeight] caps a wide
 * card's next departures block. [live] is false for the copies the board only measures, which then
 * leave out their animations and report their parts' heights to [measured].
 */
@Composable
internal fun BoardRouteCard(
    spec: RouteCardSpec,
    uiState: StopBoardViewModel.UiState,
    density: RouteCardDensity,
    visible: VisibleAlternatives,
    nowMillis: Long,
    actions: RouteCardActions,
    nextBlockMaxHeight: Dp,
    live: Boolean,
    modifier: Modifier = Modifier,
    headBand: Int = 0,
    footerBand: Int = 0,
    measured: CardMeasure? = null,
) {
    val destination = spec.destination
    val trip = uiState.fastestTrips[destination.id]?.firstOrNull()
    val metrics = routeCardMetrics(spec.size)
    val full = density == RouteCardDensity.FULL
    val showsRows = full && spec.footer == RouteCardFooter.TOGGLE && spec.expanded && visible.showsRows
    val shownRows = if (showsRows) minOf(visible.shown, spec.alternatives.size) else 0
    val rail =
        trip != null &&
            RouteCardBands.hasRail(spec.footer, spec.size, hasAlternatives = spec.alternatives.isNotEmpty(), density = density)
    // A route being refreshed, or a failed one being retried; a card still loading has its skeletons.
    val isRefreshing =
        live &&
            destination.id in uiState.loadingTripDestinationIds &&
            (trip != null || destination.id in uiState.failedTripDestinationIds)
    val onRoute = if (trip != null) PulseTheme.colors.onRoute else MaterialTheme.colorScheme.onSurface
    val temporaryActions: @Composable () -> Unit = {
        TemporaryRouteActions(
            time = uiState.temporaryTripTime,
            onTimeChange = actions.onTimeChange,
            canSave = uiState.canSaveTemporaryDestination,
            onSave = actions.onSave,
            onDismiss = actions.onDismissTemporary,
            tint = onRoute,
        )
    }
    val divider = onRoute.copy(alpha = DIVIDER_ALPHA)

    CardFrame(
        headBand = headBand,
        footerBand = footerBand,
        measured = measured,
        modifier =
            modifier
                .clip(RoundedCornerShape(FjordDimens.radiusLg))
                .background(if (trip != null) PulseTheme.colors.route else MaterialTheme.colorScheme.surfaceContainer),
        head = {
            if (trip == null) {
                CardMessage(destination = destination, uiState = uiState, metrics = metrics, density = density, live = live)
            } else {
                CardHead(
                    trip = trip,
                    spec = spec,
                    metrics = metrics,
                    density = density,
                    nowMillis = nowMillis,
                    nextBlockMaxHeight = nextBlockMaxHeight,
                    onOpen = { actions.onOpenTrip(destination.id, trip) },
                    onOpenNext = { actions.onOpenTrip(destination.id, it) },
                    onMoreInDetails = { actions.onMoreInDetails(destination) },
                )
            }
        },
        rows =
            buildList<@Composable () -> Unit> {
                spec.alternatives.take(shownRows).forEach { alternative ->
                    add {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = metrics.padH), color = divider)
                            AlternativeRow(
                                trip = alternative,
                                size = spec.size,
                                metrics = metrics,
                                nowMillis = nowMillis,
                                onClick = { actions.onOpenTrip(destination.id, alternative) },
                            )
                        }
                    }
                }
                if (showsRows && visible.showsMoreRow) {
                    add {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = metrics.padH), color = divider)
                            RouteMoreInDetailsRow(hidden = visible.hidden, onClick = { actions.onMoreInDetails(destination) })
                        }
                    }
                }
            },
        footer =
            when {
                !RouteCardBands.showsBand(spec.footer, density) -> null
                spec.footer == RouteCardFooter.TOGGLE && trip != null -> {
                    {
                        ToggleFooter(
                            expanded = spec.expanded,
                            count = spec.alternatives.size,
                            // Collapsed, or expanded with no room for a row: the bar names what comes next.
                            next = if (!spec.expanded || visible.showsThenLine) spec.alternatives else emptyList(),
                            destinationName = destination.name,
                            metrics = metrics,
                            nowMillis = nowMillis,
                            onToggle = { actions.onToggleAlternatives(destination.id) },
                        )
                    }
                }
                spec.footer == RouteCardFooter.NOTE && trip != null -> {
                    { NoteFooter(metrics = metrics, color = onRoute) }
                }
                spec.footer == RouteCardFooter.ACTIONS -> {
                    { ActionsFooter(metrics = metrics, color = onRoute, actions = temporaryActions) }
                }
                else -> null
            },
        trailing = if (spec.footer == RouteCardFooter.ACTIONS && !RouteCardBands.showsBand(spec.footer, density)) temporaryActions else null,
        rail =
            if (rail) {
                {
                    RouteRail(
                        count = spec.alternatives.size,
                        destinationName = destination.name,
                        metrics = metrics,
                        onClick = { actions.onMoreInDetails(destination) },
                    )
                }
            } else {
                null
            },
        indicator =
            if (isRefreshing) {
                { RefreshingIndicator(modifier = Modifier.fillMaxWidth(), color = onRoute.copy(alpha = REFRESHING_ALPHA)) }
            } else {
                null
            },
    )
}

/**
 * A card's frame: its [head] and [rows] stacked over its [footer], and at the trailing end its
 * [trailing] buttons (centred) or its [rail] (full height). The head is centred in at least
 * [headBand], the footer at least [footerBand] high and at the bottom; a height above what they
 * need is shared equally among the rows, each centred in its share, or else goes to the head. The
 * [indicator], a thin line, sits along the bottom of the head's share. Reports the head's and
 * footer's own heights to [measured].
 */
@Composable
private fun CardFrame(
    headBand: Int,
    footerBand: Int,
    measured: CardMeasure?,
    head: @Composable () -> Unit,
    rows: List<@Composable () -> Unit>,
    footer: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    rail: (@Composable () -> Unit)?,
    indicator: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier,
        contents =
            listOf(
                head,
                { rows.forEach { it() } },
                { if (footer != null) footer() },
                { if (trailing != null) trailing() },
                { if (rail != null) rail() },
                { if (indicator != null) indicator() },
            ),
    ) { slots, constraints ->
        val width = constraints.maxWidth
        val railWidth = if (slots[RAIL_SLOT].isEmpty()) 0 else RouteCardBands.RAIL_WIDTH_DP.dp.roundToPx()
        val trailingP = slots[TRAILING_SLOT].firstOrNull()?.measure(Constraints(maxWidth = width / 2))
        val contentWidth = (width - railWidth - (trailingP?.width ?: 0)).coerceAtLeast(0)
        val content = Constraints.fixedWidth(contentWidth)
        val headP = slots[HEAD_SLOT].single().measure(content)
        val rowPs = slots[ROWS_SLOT].map { it.measure(content) }
        val footerP = slots[FOOTER_SLOT].firstOrNull()?.measure(content.copy(minHeight = footerBand))
        measured?.apply {
            this.head = headP.height
            this.footer = footerP?.height ?: 0
        }
        val headHeight = maxOf(headP.height, headBand)
        val stacked = headHeight + rowPs.sumOf { it.height } + (footerP?.height ?: 0)
        val natural = maxOf(stacked, trailingP?.height ?: 0, if (railWidth > 0) FjordDimens.touch.roundToPx() else 0)
        val height = natural.coerceIn(constraints.minHeight, constraints.maxHeight)
        val left = (height - stacked).coerceAtLeast(0)
        val rowExtra = if (rowPs.isEmpty()) 0 else left / rowPs.size
        val headExtra = headHeight - headP.height + if (rowPs.isEmpty()) left else 0
        val railP = slots[RAIL_SLOT].firstOrNull()?.measure(Constraints.fixed(railWidth, height))
        val indicatorP = slots[INDICATOR_SLOT].firstOrNull()?.measure(Constraints.fixed((contentWidth - 2 * INDICATOR_INSET.roundToPx()).coerceAtLeast(0), 2.dp.roundToPx()))
        layout(width, height) {
            var y = 0
            headP.place(0, y + headExtra / 2)
            y += headP.height + headExtra
            indicatorP?.place(INDICATOR_INSET.roundToPx(), y - indicatorP.height)
            rowPs.forEach {
                it.place(0, y + rowExtra / 2)
                y += it.height + rowExtra
            }
            footerP?.place(0, height - footerP.height)
            trailingP?.place(contentWidth, (height - trailingP.height) / 2)
            railP?.place(width - railWidth, 0)
        }
    }
}

/**
 * The card's journey at [density]. Compact cards (and every card at [RouteCardDensity.LINE]) are
 * one target opening the route; a timeline card's target is its leave, journey and arrival blocks
 * only, its next departures block having targets of its own ([JourneyRow]).
 */
@Composable
private fun CardHead(
    trip: TripInfo,
    spec: RouteCardSpec,
    metrics: RouteCardMetrics,
    density: RouteCardDensity,
    nowMillis: Long,
    nextBlockMaxHeight: Dp,
    onOpen: () -> Unit,
    onOpenNext: (TripInfo) -> Unit,
    onMoreInDetails: () -> Unit,
) {
    val onRoute = PulseTheme.colors.onRoute
    val title = stringResource(Res.string.route_to, trip.destinationName)
    val leaveLabel = stringResource(Res.string.route_leave)
    val leave = isoClock(trip.expectedStartTime)
    val leavesIn = leavesInText(trip.expectedStartTime, nowMillis)
    val duration = tripDurationText(trip.durationMinutes)
    val arrival = trip.arrivalClock()
    val arrivalText = arrival?.let { stringResource(Res.string.route_arrival, it) }
    val description = listOfNotNull(title, "$leaveLabel $leave", leavesIn, duration, arrivalText).joinToString(", ")
    val target = Modifier.clickable(onClick = onOpen).semantics(mergeDescendants = true) { contentDescription = description }
    when {
        density == RouteCardDensity.LINE ->
            LineHead(
                clock = { Text(leave, style = metrics.lineClock, color = PulseTheme.colors.routeAccent, maxLines = 1, softWrap = false) },
                title = { Text(title, style = metrics.title, color = onRoute, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                soon = { leavesIn?.let { Text(it, style = metrics.body, color = onRoute, maxLines = 1, softWrap = false) } },
                modifier = target.fillMaxWidth().heightIn(min = FjordDimens.touch).padding(horizontal = metrics.padH, vertical = FjordDimens.space2),
            )
        spec.size == RouteCardSize.COMPACT -> {
            val lines = if (density == RouteCardDensity.SHORT) 1 else 2
            Column(
                modifier = target.fillMaxWidth().padding(horizontal = metrics.padH, vertical = metrics.padV),
                verticalArrangement = Arrangement.spacedBy(FjordDimens.space2),
            ) {
                // The clock is as tall as the title and its times beside it, so they share its height;
                // on a narrow card the title gets a line of its own and the times go beside the clock.
                if (spec.narrow) Text(title, style = metrics.title, color = onRoute, maxLines = lines, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3)) {
                    Text(leave, style = metrics.clock, color = PulseTheme.colors.routeAccent, maxLines = 1, softWrap = false)
                    Column(modifier = Modifier.weight(1f)) {
                        if (!spec.narrow) Text(title, style = metrics.title, color = onRoute, maxLines = lines, overflow = TextOverflow.Ellipsis)
                        MetaLine(listOfNotNull(leavesIn, duration, arrivalText), metrics, onRoute, maxLines = lines)
                    }
                }
                if (density != RouteCardDensity.SHORT) LegChain(trip = trip, metrics = metrics, color = onRoute, showsFirstFrom = !spec.narrow)
            }
        }
        else -> {
            val labelled = density != RouteCardDensity.SHORT
            val next = if (spec.size.nextInHead) spec.alternatives else emptyList()
            JourneyRow(
                metrics = metrics,
                hasNext = next.isNotEmpty(),
                target = { Box(modifier = target) },
                leave = {
                    Column(modifier = Modifier.clearAndSetSemantics {}.padding(start = metrics.padH, top = metrics.padV, bottom = metrics.padV)) {
                        if (labelled) Text(leaveLabel, style = metrics.caption, color = onRoute, maxLines = 1)
                        Text(leave, style = metrics.clock, color = PulseTheme.colors.routeAccent, maxLines = 1, softWrap = false)
                        leavesIn?.let { Text(it, style = metrics.body, color = onRoute, maxLines = 1) }
                    }
                },
                journey = { capped ->
                    Column(
                        modifier = Modifier.clearAndSetSemantics {}.padding(vertical = metrics.padV),
                        verticalArrangement = Arrangement.spacedBy(FjordDimens.space2),
                    ) {
                        Text(title, style = metrics.title, color = onRoute, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        RouteTimeline(
                            trip = trip,
                            metrics = if (capped) metrics else metrics.copy(perMinute = Float.POSITIVE_INFINITY),
                            color = onRoute,
                            showsCaptions = labelled,
                        )
                    }
                },
                arrive = {
                    Column(modifier = Modifier.clearAndSetSemantics {}.padding(vertical = metrics.padV), horizontalAlignment = Alignment.End) {
                        if (labelled) Text(stringResource(Res.string.route_arrive), style = metrics.caption, color = onRoute, maxLines = 1)
                        Text(arrival ?: "--:--", style = metrics.secondClock, color = onRoute, maxLines = 1, softWrap = false)
                        Text(duration, style = metrics.body, color = onRoute, maxLines = 1, softWrap = false)
                    }
                },
                next = { NextBlock(trips = next, metrics = metrics, nowMillis = nowMillis, onOpen = onOpenNext, onMoreInDetails = onMoreInDetails) },
                nextMaxHeight = nextBlockMaxHeight,
            )
        }
    }
}

/**
 * "in 6 min · 58 min · arr. 19:10", wrapping only between its parts onto at most [maxLines] lines;
 * when that is not enough the duration goes, then the arrival ([RouteCardFitting.metaParts]).
 */
@Composable
private fun MetaLine(
    parts: List<String>,
    metrics: RouteCardMetrics,
    color: Color,
    maxLines: Int,
) {
    Layout(
        content = {
            parts.forEachIndexed { index, part ->
                // Each part with the separator after it, and without for when it ends a line or the row.
                Text("$part ·", style = metrics.body, color = color, maxLines = 1, softWrap = false)
                Text(part, style = metrics.body, color = color, maxLines = 1, softWrap = false)
            }
        },
    ) { measurables, constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth)
        val withDot = parts.indices.map { measurables[2 * it].measure(loose) }
        val plain = parts.indices.map { measurables[2 * it + 1].measure(loose) }
        val gap = FjordDimens.space1.roundToPx()
        val kept =
            RouteCardFitting.metaParts(
                widths = withDot.map { it.width.toFloat() },
                gap = gap.toFloat(),
                available = constraints.maxWidth.toFloat(),
                maxLines = maxLines,
                dropOrder = RouteCardFitting.META_DROP_ORDER.filter { it < parts.size },
            )
        // Lay the kept parts out as they flow; a part ending a line or the row drops its separator.
        val lines =
            RouteCardFitting
                .flowRows(kept.map { withDot[it].width.toFloat() }, gap.toFloat(), constraints.maxWidth.toFloat())
                .map { row -> row.map { kept[it] } }
        val lineHeight = plain.maxOfOrNull { it.height } ?: 0
        val height = lineHeight * lines.size
        layout(constraints.maxWidth, height) {
            lines.forEachIndexed { line, indices ->
                var left = 0
                indices.forEachIndexed { position, index ->
                    val placeable = if (position == indices.lastIndex) plain[index] else withDot[index]
                    placeable.place(left, line * lineHeight)
                    left += placeable.width + gap
                }
            }
        }
    }
}

/**
 * A one-line card's row: [clock], then [title] ellipsised in what is left, then [soon] ("in 6 min")
 * only when the whole title fits beside it ([RouteCardFitting.lineShowsSoon]).
 */
@Composable
private fun LineHead(
    clock: @Composable () -> Unit,
    title: @Composable () -> Unit,
    soon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(modifier = modifier, contents = listOf(clock, title, soon)) { (clockM, titleM, soonM), constraints ->
        val gap = FjordDimens.space3.roundToPx()
        val loose = Constraints(maxWidth = constraints.maxWidth)
        val clockP = clockM.single().measure(loose)
        val soonP = soonM.singleOrNull()?.measure(loose)
        val titleWidth = titleM.single().maxIntrinsicWidth(Constraints.Infinity)
        val showsSoon =
            soonP != null &&
                RouteCardFitting.lineShowsSoon(
                    clockWidth = clockP.width.toFloat(),
                    titleWidth = titleWidth.toFloat(),
                    soonWidth = soonP.width.toFloat(),
                    gap = gap.toFloat(),
                    available = constraints.maxWidth.toFloat(),
                )
        val titleMax = (constraints.maxWidth - clockP.width - gap - if (showsSoon) gap + soonP.width else 0).coerceAtLeast(0)
        val titleP = titleM.single().measure(Constraints(maxWidth = titleMax))
        val height = maxOf(clockP.height, titleP.height, if (showsSoon) soonP.height else 0).coerceAtLeast(constraints.minHeight)
        layout(constraints.maxWidth, height) {
            clockP.place(0, (height - clockP.height) / 2)
            titleP.place(clockP.width + gap, (height - titleP.height) / 2)
            if (showsSoon) soonP.place(constraints.maxWidth - soonP.width, (height - soonP.height) / 2)
        }
    }
}

/**
 * A timeline card's row: [leave], [journey] and [arrive] side by side, then, when [hasNext], a
 * divider and the [next] block. The leave and arrival blocks take what they need; without a next
 * block the journey takes the rest (its timeline filling it), with one the journey takes what its
 * capped timeline needs, at most what leaves the next block [RouteCardFitting.NEXT_BLOCK_MIN_WIDTH_DP] (times the pill
 * scale), and the next block gets everything else, up to [nextMaxHeight] tall. The three journey
 * blocks are centred on the row's height. [target], drawn under them and exactly as large, is what
 * opens the route: the next block's empty space opens nothing. [journey] is told whether its
 * timeline is capped.
 */
@Composable
private fun JourneyRow(
    metrics: RouteCardMetrics,
    hasNext: Boolean,
    target: @Composable () -> Unit,
    leave: @Composable () -> Unit,
    journey: @Composable (capped: Boolean) -> Unit,
    arrive: @Composable () -> Unit,
    next: @Composable () -> Unit,
    nextMaxHeight: Dp,
) {
    val onRoute = PulseTheme.colors.onRoute
    Layout(
        contents =
            listOf(
                target,
                leave,
                { journey(hasNext) },
                { Box(modifier = Modifier.padding(end = metrics.padH)) { arrive() } },
                { if (hasNext) Box(modifier = Modifier.width(1.dp).background(onRoute.copy(alpha = DIVIDER_ALPHA))) },
                { if (hasNext) next() },
            ),
    ) { slots, constraints ->
        val width = constraints.maxWidth
        val gap = metrics.gap.roundToPx()
        val loose = Constraints()
        val leaveP = slots[1].single().measure(loose)
        val arriveP = slots[3].single().measure(loose)
        val nextMin = if (hasNext) (RouteCardFitting.NEXT_BLOCK_MIN_WIDTH_DP.dp * metrics.pillScale).roundToPx() + 1 else 0
        val journeyMax = (width - leaveP.width - arriveP.width - 2 * gap - nextMin).coerceAtLeast(0)
        val journeyP =
            slots[2].single().measure(
                if (hasNext) Constraints(maxWidth = journeyMax) else Constraints(minWidth = journeyMax, maxWidth = journeyMax),
            )
        val journeyWidth = leaveP.width + gap + journeyP.width + gap + arriveP.width
        val nextWidth = (width - journeyWidth - 1).coerceAtLeast(0)
        val journeyHeight = maxOf(leaveP.height, journeyP.height, arriveP.height)
        // The next block may be as tall as the journey, or as the card's share of the board allows.
        val nextLimit = if (nextMaxHeight == Dp.Infinity) Constraints.Infinity else maxOf(journeyHeight, nextMaxHeight.roundToPx())
        val nextP = slots[5].singleOrNull()?.measure(Constraints(minWidth = nextWidth, maxWidth = nextWidth, maxHeight = nextLimit))
        val height = maxOf(journeyHeight, nextP?.height ?: 0)
        val targetP = slots[0].single().measure(Constraints.fixed(if (hasNext) journeyWidth else width, height))
        val dividerP = slots[4].singleOrNull()?.measure(Constraints.fixed(1, (height - 2 * metrics.padV.roundToPx()).coerceAtLeast(0)))
        layout(width, height) {
            targetP.place(0, 0)
            leaveP.place(0, (height - leaveP.height) / 2)
            journeyP.place(leaveP.width + gap, (height - journeyP.height) / 2)
            arriveP.place(leaveP.width + gap + journeyP.width + gap, (height - arriveP.height) / 2)
            dividerP?.place(journeyWidth, metrics.padV.roundToPx())
            nextP?.place(journeyWidth + 1, (height - nextP.height) / 2)
        }
    }
}

/**
 * A card without a route, muted: still loading (skeletons), failed (retrying by itself), or none
 * found. At [RouteCardDensity.LINE] only its title, on one line.
 */
@Composable
private fun CardMessage(
    destination: StopPlace,
    uiState: StopBoardViewModel.UiState,
    metrics: RouteCardMetrics,
    density: RouteCardDensity,
    live: Boolean,
) {
    val noRouteTime = uiState.noRouteTime(destination.id)
    val failed = destination.id in uiState.failedTripDestinationIds
    val icon: ImageVector?
    val title: String
    val body: String?
    when {
        failed -> {
            icon = loadFailureIcon(uiState.isOnline)
            title = stringResource(Res.string.fastest_route_unavailable, destination.name)
            body = stringResource(Res.string.error_retrying_automatically)
        }
        noRouteTime != null -> {
            icon = if (noRouteTime is TripTime.ArriveBy) PulseIcons.Schedule else PulseIcons.SearchOff
            title =
                if (noRouteTime is TripTime.ArriveBy) {
                    stringResource(Res.string.no_route_to_arriving, destination.name, tripClockText(noRouteTime.atMillis))
                } else {
                    stringResource(Res.string.no_route_to, destination.name)
                }
            body = null
        }
        else -> {
            icon = null
            title = stringResource(Res.string.loading_fastest_route, destination.name)
            body = null
        }
    }
    val line = density == RouteCardDensity.LINE
    val alpha = if (live && icon == null) rememberPlaceholderPulse() else STATIC_PLACEHOLDER_ALPHA
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = FjordDimens.touch)
                .clearAndSetSemantics { contentDescription = listOfNotNull(title, body).joinToString(". ") }
                .padding(horizontal = metrics.padH, vertical = if (line) FjordDimens.space2 else metrics.padV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3),
    ) {
        val iconSize = if (line) metrics.icon + 4.dp else metrics.icon * MESSAGE_ICON_SCALE
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(iconSize))
        } else {
            PlaceholderBlock(alpha, Modifier.size(width = iconSize * 1.5f, height = iconSize), color = MaterialTheme.colorScheme.onSurface)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FjordDimens.space1)) {
            Text(
                title,
                style = metrics.title,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (line) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!line && density != RouteCardDensity.SHORT) {
                body?.let { Text(it, style = metrics.body, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
                if (icon == null) {
                    PlaceholderBlock(
                        alpha,
                        Modifier.fillMaxWidth(SKELETON_WIDTH).height(SKELETON_HEIGHT),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * An expanded card's alternative: its clock and how soon, the route (a smaller timeline on a
 * timeline card, the wrapping chain on a compact one), the duration and arrival. Opens the route.
 */
@Composable
internal fun AlternativeRow(
    trip: TripInfo,
    size: RouteCardSize,
    metrics: RouteCardMetrics,
    nowMillis: Long,
    onClick: () -> Unit,
) {
    val onRoute = PulseTheme.colors.onRoute
    val leavesIn = leavesInText(trip.expectedStartTime, nowMillis)
    val duration = tripDurationText(trip.durationMinutes)
    val arrivalText = trip.arrivalClock()?.let { stringResource(Res.string.route_arrival, it) }
    val description = listOfNotNull(isoClock(trip.expectedStartTime), leavesIn, duration, arrivalText).joinToString(", ")
    val rowMetrics =
        if (size == RouteCardSize.COMPACT) metrics else routeCardMetrics(RouteCardSize.COMPACT).copy(padH = metrics.padH, padV = metrics.padV, gap = metrics.gap)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = FjordDimens.touch)
                .clickable(onClick = onClick)
                .clearAndSetSemantics { contentDescription = description }
                .padding(horizontal = metrics.padH, vertical = FjordDimens.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3),
    ) {
        Column {
            Text(isoClock(trip.expectedStartTime), style = metrics.secondClock, color = onRoute, maxLines = 1)
            leavesIn?.let { Text(it, style = metrics.caption, color = onRoute, maxLines = 1) }
        }
        if (size == RouteCardSize.COMPACT) {
            LegChain(trip = trip, metrics = rowMetrics, color = onRoute, showsFirstFrom = false, modifier = Modifier.weight(1f))
        } else {
            Box(modifier = Modifier.weight(1f)) { RouteTimeline(trip = trip, metrics = rowMetrics, color = onRoute) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(duration, style = MaterialTheme.typography.labelLarge, color = onRoute, maxLines = 1)
            arrivalText?.let { Text(it, style = metrics.caption, color = onRoute, maxLines = 1) }
        }
    }
}

private val INDICATOR_INSET = FjordDimens.radiusLg

private const val HEAD_SLOT = 0
private const val ROWS_SLOT = 1
private const val FOOTER_SLOT = 2
private const val TRAILING_SLOT = 3
private const val RAIL_SLOT = 4
private const val INDICATOR_SLOT = 5
internal const val DIVIDER_ALPHA = 0.15f
private const val REFRESHING_ALPHA = 0.6f
private const val STATIC_PLACEHOLDER_ALPHA = 0.5f
private const val MESSAGE_ICON_SCALE = 1.6f
private const val SKELETON_WIDTH = 0.6f
private val SKELETON_HEIGHT = 12.dp
