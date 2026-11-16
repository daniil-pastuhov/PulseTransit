package com.example.pulsetransit.ui.screens.routecards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.theme.PulseTheme
import com.example.pulsetransit.ui.components.leavesInText
import com.example.pulsetransit.ui.layout.RouteCardBands
import com.example.pulsetransit.ui.layout.RouteCardFitting
import com.example.pulsetransit.ui.layout.RouteCardLayout
import com.example.pulsetransit.ui.resources.Res
import com.example.pulsetransit.ui.resources.route_alternatives_collapsed
import com.example.pulsetransit.ui.resources.route_alternatives_expanded
import com.example.pulsetransit.ui.resources.route_arrival
import com.example.pulsetransit.ui.resources.route_fewer
import com.example.pulsetransit.ui.resources.route_fewer_description
import com.example.pulsetransit.ui.resources.route_more
import com.example.pulsetransit.ui.resources.route_more_description
import com.example.pulsetransit.ui.resources.route_more_in_details
import com.example.pulsetransit.ui.resources.route_no_later
import com.example.pulsetransit.ui.resources.route_show_all_description
import com.example.pulsetransit.ui.resources.route_then
import com.example.pulsetransit.ui.screens.isoClock
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// The bands along a card's bottom (RouteCardFooter), the rail that stands in for the toggle when
// they are left out for room, and the next departures they and wide cards name.

/**
 * The card's toggle bar: one target the width of the card, at least [RouteCardMetrics.footer] high,
 * that opens or closes the alternatives (a persisted setting; the app never closes a card itself).
 * Collapsed it names the [next] departures, as many as fit ([NextEntries]), so it carries
 * information rather than being only a button; a grab handle on top says it can be pulled.
 *
 * Besides a tap, a downward drag opens it and an upward one closes it. The drag is the bar's own
 * ([draggable] dispatches no nested scroll), so it never reaches the board's pull to refresh, and
 * a tap without movement stays a click. To a screen reader the bar is one button: its label and the
 * next departures read out, "Expanded"/"Collapsed" as its state, and a double tap as the action
 * ("Show more routes to X"); the drag is a shortcut for the same thing, so it needs no action of
 * its own.
 */
@Composable
internal fun ToggleFooter(
    expanded: Boolean,
    count: Int,
    next: List<TripInfo>,
    destinationName: String,
    metrics: RouteCardMetrics,
    nowMillis: Long,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onRoute = PulseTheme.colors.onRoute
    val density = LocalDensity.current
    val label = if (expanded) stringResource(Res.string.route_fewer) else pluralStringResource(Res.plurals.route_more, count, count)
    val actionLabel = stringResource(if (expanded) Res.string.route_fewer_description else Res.string.route_more_description, destinationName)
    val state = stringResource(if (expanded) Res.string.route_alternatives_expanded else Res.string.route_alternatives_collapsed)
    val thenLabel = stringResource(Res.string.route_then)
    val nextText = next.map { nextDescription(it, nowMillis) }
    val description = listOfNotNull(actionLabel, nextText.takeIf { it.isNotEmpty() }?.joinToString("; ", prefix = "$thenLabel ")).joinToString(". ")
    // How far the bar has been pulled since the drag began, in pixels; down is positive.
    var pulled by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta -> pulled += delta }
    val threshold = with(density) { DRAG_THRESHOLD.toPx() }
    val maxShift = with(density) { DRAG_MAX_SHIFT.toPx() }
    val flingVelocity = with(density) { FLING_VELOCITY.toPx() }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = metrics.footer)
                .background(onRoute.copy(alpha = FOOTER_TINT_ALPHA))
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStarted = { pulled = 0f },
                    onDragStopped = { velocity ->
                        val opens = !expanded && (pulled > threshold || velocity > flingVelocity)
                        val closes = expanded && (pulled < -threshold || velocity < -flingVelocity)
                        pulled = 0f
                        if (opens || closes) onToggle()
                    },
                ).clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onToggle)
                .semantics {
                    contentDescription = description
                    stateDescription = state
                },
    ) {
        BandContent(metrics) {
            // The grab handle follows the pull a little, so the drag is felt before it is let go.
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .graphicsLayer { translationY = (pulled / 2).coerceIn(-maxShift, maxShift) }
                        .padding(top = 6.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .background(onRoute.copy(alpha = HANDLE_ALPHA), CircleShape),
            )
            Row(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .clearAndSetSemantics {}
                        .padding(start = metrics.padH, end = metrics.padH - FjordDimens.space1, top = FjordDimens.space3, bottom = FjordDimens.space2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3),
            ) {
                if (next.isNotEmpty()) {
                    Text(thenLabel, style = metrics.caption, color = onRoute, maxLines = 1)
                    NextEntries(trips = next, metrics = metrics, nowMillis = nowMillis, color = onRoute, modifier = Modifier.weight(1f))
                } else {
                    // Open: nothing to name, so "Fewer" sits in the middle of the bar.
                    Spacer(modifier = Modifier.weight(1f))
                }
                Text(label, style = MaterialTheme.typography.labelLarge, color = onRoute, maxLines = 1)
                Icon(
                    if (expanded) PulseIcons.ExpandLess else PulseIcons.ExpandMore,
                    contentDescription = null,
                    tint = onRoute,
                    modifier = Modifier.size(metrics.icon + 6.dp),
                )
                if (next.isEmpty()) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * A band's content, at least [RouteCardMetrics.footer] high, at the bottom of a band its grid row
 * may have made taller ([RouteCardLayout.stretch]), so it stays level with the neighbours' bands.
 */
@Composable
private fun BoxScope.BandContent(
    metrics: RouteCardMetrics,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(min = metrics.footer),
        content = content,
    )
}

/**
 * The band of a card without alternatives beside cards with a band: "No later routes", so the
 * row's bands line up and the card says why it has no toggle.
 */
@Composable
internal fun NoteFooter(
    metrics: RouteCardMetrics,
    color: Color,
) {
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = metrics.footer).background(color.copy(alpha = FOOTER_TINT_ALPHA))) {
        BandContent(metrics) {
            Text(
                stringResource(Res.string.route_no_later),
                style = metrics.caption,
                color = color.copy(alpha = CAPTION_ALPHA),
                maxLines = 1,
                modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = metrics.padH),
            )
        }
    }
}

/** The searched-for destination's band: its buttons ([actions]: time, save, close) at the end. */
@Composable
internal fun ActionsFooter(
    metrics: RouteCardMetrics,
    color: Color,
    actions: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = metrics.footer).background(color.copy(alpha = FOOTER_TINT_ALPHA))) {
        BandContent(metrics) {
            Box(modifier = Modifier.align(Alignment.CenterEnd).padding(horizontal = metrics.padH - FjordDimens.space2)) { actions() }
        }
    }
}

/**
 * The rail at a card's trailing edge when there is no room for its band
 * ([com.example.pulsetransit.ui.layout.RouteCardDensity.RAIL]): as tall as the card,
 * [RouteCardBands.RAIL_WIDTH_DP] wide, "+[count]" over a chevron. There is no room to open the
 * alternatives in the card then, so it opens the route search on the destination, which lists them
 * all, and leaves the setting as it is.
 */
@Composable
internal fun RouteRail(
    count: Int,
    destinationName: String,
    metrics: RouteCardMetrics,
    onClick: () -> Unit,
) {
    val onRoute = PulseTheme.colors.onRoute
    val description = stringResource(Res.string.route_show_all_description, destinationName)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(onRoute.copy(alpha = FOOTER_TINT_ALPHA))
                .clickable(role = Role.Button, onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = description
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("+$count", style = MaterialTheme.typography.labelLarge, color = onRoute, maxLines = 1)
        Icon(PulseIcons.ChevronRight, contentDescription = null, tint = onRoute, modifier = Modifier.size(metrics.icon + 4.dp))
    }
}

/**
 * A wide card's next departures, beside its journey: "Then" and a row per departure, each a touch
 * target opening that route and saying as much as fits its width ([NextDetail]). As many rows as
 * fit the height it is given ([RouteCardFitting.nextBlockRows]); when that is not all of them, the
 * "Then" line becomes a target too, "+N in details", opening the route search on the destination.
 */
@Composable
internal fun NextBlock(
    trips: List<TripInfo>,
    metrics: RouteCardMetrics,
    nowMillis: Long,
    onOpen: (TripInfo) -> Unit,
    onMoreInDetails: () -> Unit,
) {
    val onRoute = PulseTheme.colors.onRoute
    val thenLabel = stringResource(Res.string.route_then)
    val header: @Composable (hidden: Int) -> Unit = { hidden ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (hidden > 0) {
                            Modifier.heightIn(min = FjordDimens.touch).clickable(onClick = onMoreInDetails)
                        } else {
                            Modifier.clearAndSetSemantics {}
                        },
                    ).padding(horizontal = metrics.padH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(thenLabel, style = metrics.caption, color = onRoute, modifier = Modifier.weight(1f).clearAndSetSemantics {})
            if (hidden > 0) {
                Text(stringResource(Res.string.route_more_in_details, hidden), style = metrics.caption, color = onRoute)
                Icon(PulseIcons.ChevronRight, contentDescription = null, tint = onRoute, modifier = Modifier.size(metrics.icon))
            }
        }
    }
    Layout(
        modifier = Modifier.fillMaxWidth().padding(vertical = FjordDimens.space2),
        contents =
            listOf(
                { for (hidden in 0..trips.size) header(hidden) },
                {
                    trips.forEach { trip ->
                        val description = nextDescription(trip, nowMillis)
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = FjordDimens.touch)
                                    .clickable(onClick = { onOpen(trip) })
                                    .clearAndSetSemantics { contentDescription = "$thenLabel $description" }
                                    .padding(horizontal = metrics.padH),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            RichestFitting(
                                variants =
                                    NextDetail.entries.reversed().map { detail ->
                                        { NextEntry(trip = trip, detail = detail, metrics = metrics, nowMillis = nowMillis, color = onRoute) }
                                    },
                            )
                        }
                    }
                },
            ),
    ) { (headers, rows), constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val rowPlaceables = rows.map { it.measure(loose) }
        // The plain header and each "+N" one are measured; only the one used is placed.
        val headerPlaceables = headers.map { it.measure(loose) }
        val shown =
            RouteCardFitting.nextBlockRows(
                headerHeights = headerPlaceables.map { it.height.toFloat() },
                rowHeights = rowPlaceables.map { it.height.toFloat() },
                maxHeight = constraints.maxHeight.toFloat(),
            )
        val header = headerPlaceables[rowPlaceables.size - shown]
        val height = (header.height + rowPlaceables.take(shown).sumOf { it.height }).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.maxWidth, height) {
            header.place(0, 0)
            var y = header.height
            rowPlaceables.take(shown).forEach {
                it.place(0, y)
                y += it.height
            }
        }
    }
}

/** What a screen reader says for a next departure: "18:12, in 17 min, 37, 4, arr. 19:08". */
@Composable
private fun nextDescription(
    trip: TripInfo,
    nowMillis: Long,
): String =
    listOfNotNull(
        isoClock(trip.expectedStartTime),
        leavesInText(trip.expectedStartTime, nowMillis),
        trip.shownLegs
            .mapNotNull { it.lineName }
            .joinToString(", ")
            .ifEmpty { null },
        trip.arrivalClock()?.let { stringResource(Res.string.route_arrival, it) },
    ).joinToString(", ")

/**
 * The toggle bar's next departures in a row, fitted by [RouteCardFitting.nextEntries]: all of them
 * in the richest [NextDetail] they all fit in, else as many as fit with their first ride only, in
 * order and whole; a clock is never cut.
 */
@Composable
private fun NextEntries(
    trips: List<TripInfo>,
    metrics: RouteCardMetrics,
    nowMillis: Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val details = NextDetail.entries.reversed()
    Layout(
        modifier = modifier,
        contents =
            details.map { detail ->
                { trips.forEach { NextEntry(trip = it, detail = detail, metrics = metrics, nowMillis = nowMillis, color = color) } }
            },
    ) { groups, constraints ->
        val gap = FjordDimens.space5.roundToPx()
        val loose = Constraints(maxHeight = constraints.maxHeight)
        val measured = groups.map { group -> group.map { it.measure(loose) } }
        val fit =
            RouteCardFitting.nextEntries(
                widthsByDetail = measured.map { group -> group.map { it.width.toFloat() } },
                gap = gap.toFloat(),
                available = constraints.maxWidth.toFloat(),
            )
        val shown = measured[fit.detail].take(fit.count)
        val height = shown.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            var x = 0
            shown.forEach {
                it.place(x, (height - it.height) / 2)
                x += it.width + gap
            }
        }
    }
}

/** How far the toggle bar must be pulled to open or close, and the most its handle follows. */
private val DRAG_THRESHOLD = 24.dp
private val DRAG_MAX_SHIFT = 8.dp

/** A flick faster than this, a second, opens or closes however short it was. */
private val FLING_VELOCITY = 400.dp

private const val FOOTER_TINT_ALPHA = 0.08f
private const val HANDLE_ALPHA = 0.5f
