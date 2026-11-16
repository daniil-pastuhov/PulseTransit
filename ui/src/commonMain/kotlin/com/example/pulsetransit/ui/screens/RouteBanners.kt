package com.example.pulsetransit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pulsetransit.data.ThenLineEntry
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.TripTime
import com.example.pulsetransit.data.tripArrival
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.theme.PulseTheme
import com.example.pulsetransit.theme.type
import com.example.pulsetransit.ui.components.LinePill
import com.example.pulsetransit.ui.components.PlaceholderBlock
import com.example.pulsetransit.ui.components.RefreshingIndicator
import com.example.pulsetransit.ui.components.TripLegs
import com.example.pulsetransit.ui.components.loadFailureIcon
import com.example.pulsetransit.ui.components.rememberPlaceholderPulse
import com.example.pulsetransit.ui.components.tripClockText
import com.example.pulsetransit.ui.components.tripDurationText
import com.example.pulsetransit.ui.layout.BoardLayout
import com.example.pulsetransit.ui.resources.Res
import com.example.pulsetransit.ui.resources.error_retrying_automatically
import com.example.pulsetransit.ui.resources.fastest_route_to
import com.example.pulsetransit.ui.resources.fastest_route_unavailable
import com.example.pulsetransit.ui.resources.loading_fastest_route
import com.example.pulsetransit.ui.resources.no_route_to
import com.example.pulsetransit.ui.resources.no_route_to_arriving
import com.example.pulsetransit.ui.resources.route_arrival
import com.example.pulsetransit.ui.resources.route_fewer
import com.example.pulsetransit.ui.resources.route_fewer_description
import com.example.pulsetransit.ui.resources.route_leave
import com.example.pulsetransit.ui.resources.route_more
import com.example.pulsetransit.ui.resources.route_more_description
import com.example.pulsetransit.ui.resources.route_more_in_details
import com.example.pulsetransit.ui.resources.route_then
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// The fastest-route cards above the stops: "Leave 19:40, 28 min, arr. 20:08 | Fastest to X, walk
// 9 min → FLY2 from Oslo S", the times in one narrow column so the title and legs get the width.
// They use the `route` colours, which stay dark on the wall at night.

private val RouteShape = RoundedCornerShape(FjordDimens.radiusLg)
private val LeaveTimeSize = 26.sp
private val LeaveTimeLine = 28.sp

/** A route card's frame: the route colours, rounded, at least a touch target high. */
@Composable
private fun RouteCard(
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val container = if (muted) MaterialTheme.colorScheme.surfaceContainer else PulseTheme.colors.route
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = FjordDimens.touch)
                .clip(RouteShape)
                .background(container)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = FjordDimens.space4, vertical = FjordDimens.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3),
        content = content,
    )
}

/** The fastest route to a destination; tapping it opens the trip's details. */
@Composable
fun FastestTripBanner(
    trip: TripInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    /** Under the title and legs, e.g. the board's "Then" line; [belowTitleDescription] is read out for it. */
    belowTitle: (@Composable () -> Unit)? = null,
    belowTitleDescription: String? = null,
) {
    val onRoute = PulseTheme.colors.onRoute
    val leave = isoClock(trip.expectedStartTime)
    val arrival = tripArrival(trip)?.let { if (it.dayOffset > 0) "${it.time}+${it.dayOffset}" else it.time }
    val title = stringResource(Res.string.fastest_route_to, trip.destinationName)
    val duration = tripDurationText(trip.durationMinutes)
    val leaveLabel = stringResource(Res.string.route_leave)
    val arrivalText = arrival?.let { stringResource(Res.string.route_arrival, it) }
    val description = listOfNotNull(title, "$leaveLabel $leave", duration, arrivalText, belowTitleDescription).joinToString(", ")

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Larger text needs the width a narrower card has: past it, the title goes under the times.
        val compact = BoardLayout.isCompactBanner(maxWidth.value / LocalDensity.current.fontScale.coerceAtLeast(1f))
        val leaveTime: @Composable () -> Unit = {
            Text(leaveLabel, style = MaterialTheme.typography.labelSmall, color = onRoute)
            Text(
                leave,
                style = PulseTheme.type.countdown.copy(fontSize = LeaveTimeSize, lineHeight = LeaveTimeLine),
                color = PulseTheme.colors.routeAccent,
                maxLines = 1,
            )
        }
        val titleBlock: @Composable (Modifier) -> Unit = { blockModifier ->
            Column(modifier = blockModifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = onRoute,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TripLegs(trip = trip, color = onRoute, maxLines = 2)
                belowTitle?.invoke()
            }
        }
        val durationLines: @Composable () -> Unit = {
            Text(duration, style = MaterialTheme.typography.labelLarge, color = onRoute, maxLines = 1)
            arrivalText?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = onRoute, maxLines = 1) }
        }
        RouteCard(onClick = onClick, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
            if (compact) {
                // Too narrow for three columns: the title and legs go under the times.
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FjordDimens.space2)) {
                    LeaveAndDuration(
                        leave = { Column { leaveTime() } },
                        duration = { aside -> Column(horizontalAlignment = if (aside) Alignment.End else Alignment.Start) { durationLines() } },
                    )
                    titleBlock(Modifier.fillMaxWidth())
                }
            } else {
                // The times under the leave clock, so the title, legs and "Then" line get the width;
                // the two columns start level, the trailing control stays centred.
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3)) {
                    Column {
                        leaveTime()
                        Spacer(modifier = Modifier.height(2.dp))
                        durationLines()
                    }
                    titleBlock(Modifier.weight(1f))
                }
            }
            trailing?.invoke()
        }
        if (isRefreshing) {
            RefreshingIndicator(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = FjordDimens.radiusLg)
                        .fillMaxWidth()
                        .height(2.dp),
                color = onRoute.copy(alpha = 0.6f),
            )
        }
    }
}

/**
 * A stacked banner's times: [leave] at the start and [duration] across from it at the end, or under
 * it when both do not fit (large text), so neither clock is cut short. [duration] is told which.
 */
@Composable
private fun LeaveAndDuration(
    leave: @Composable () -> Unit,
    duration: @Composable (aside: Boolean) -> Unit,
) {
    SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = FjordDimens.space3.roundToPx()
        val leavePlaceable = subcompose("leave", leave).map { it.measure(loose) }.single()
        val asidePlaceable = subcompose("aside") { duration(true) }.map { it.measure(loose) }.single()
        if (leavePlaceable.width + gap + asidePlaceable.width <= constraints.maxWidth) {
            val height = maxOf(leavePlaceable.height, asidePlaceable.height)
            layout(constraints.maxWidth, height) {
                leavePlaceable.placeRelative(0, (height - leavePlaceable.height) / 2)
                asidePlaceable.placeRelative(constraints.maxWidth - asidePlaceable.width, (height - asidePlaceable.height) / 2)
            }
        } else {
            val below = subcompose("below") { duration(false) }.map { it.measure(loose) }.single()
            val top = FjordDimens.space1.roundToPx()
            layout(constraints.maxWidth, leavePlaceable.height + top + below.height) {
                leavePlaceable.placeRelative(0, 0)
                below.placeRelative(0, leavePlaceable.height + top)
            }
        }
    }
}

/** A muted card in a route's slot, so the layout doesn't jump when the route comes back. */
@Composable
private fun RouteMessage(
    icon: ImageVector,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    isRetrying: Boolean = false,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        RouteCard(muted = true, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (isRetrying) {
            RefreshingIndicator(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = FjordDimens.radiusLg)
                        .fillMaxWidth()
                        .height(2.dp),
            )
        }
    }
}

/** Keeps a destination's slot when its route failed to load, rather than silently dropping it. */
@Composable
fun FastestTripUnavailable(
    destinationName: String,
    isOnline: Boolean,
    isRetrying: Boolean,
    modifier: Modifier = Modifier,
) {
    RouteMessage(
        icon = loadFailureIcon(isOnline),
        title = stringResource(Res.string.fastest_route_unavailable, destinationName),
        body = stringResource(Res.string.error_retrying_automatically),
        isRetrying = isRetrying,
        modifier = modifier,
    )
}

/**
 * Says that Entur found no route to a destination, e.g. none arriving by the [time] asked for,
 * rather than leaving a placeholder that never fills.
 */
@Composable
fun FastestTripNone(
    destinationName: String,
    time: TripTime,
    modifier: Modifier = Modifier,
) {
    RouteMessage(
        icon = if (time is TripTime.ArriveBy) PulseIcons.Schedule else PulseIcons.SearchOff,
        title =
            if (time is TripTime.ArriveBy) {
                stringResource(Res.string.no_route_to_arriving, destinationName, tripClockText(time.atMillis))
            } else {
                stringResource(Res.string.no_route_to, destinationName)
            },
        body = null,
        modifier = modifier,
    )
}

/** Stands in for a [FastestTripBanner] whose route hasn't arrived yet: its title, and skeletons. */
@Composable
fun FastestTripPlaceholder(
    destinationName: String,
    modifier: Modifier = Modifier,
) {
    val alpha = rememberPlaceholderPulse()
    val label = stringResource(Res.string.loading_fastest_route, destinationName)
    RouteCard(muted = true, modifier = modifier.clearAndSetSemantics { contentDescription = label }) {
        PlaceholderBlock(alpha, Modifier.size(width = 52.dp, height = 36.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
            PlaceholderBlock(alpha, Modifier.fillMaxWidth(0.6f).height(12.dp))
        }
    }
}

/**
 * The collapsed board banner's "Then 08:16 [3] · 08:19 [5]": the next alternatives by departure
 * clock and the line of their first ride, on one line. As many entries as fit the width are shown,
 * whole and in order, and none (with no "Then") when not even the first fits: a clock is never
 * cut short. iOS does the same.
 */
@Composable
fun RouteThenLine(
    entries: List<ThenLineEntry>,
    modifier: Modifier = Modifier,
) {
    val onRoute = PulseTheme.colors.onRoute
    Layout(
        modifier = modifier.clearAndSetSemantics {},
        content = {
            Text(stringResource(Res.string.route_then), style = MaterialTheme.typography.labelSmall, color = onRoute, maxLines = 1)
            entries.forEachIndexed { index, entry ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FjordDimens.space1)) {
                    if (index > 0) Text("·", style = MaterialTheme.typography.labelSmall, color = onRoute, maxLines = 1)
                    Text(entry.clock, style = MaterialTheme.typography.labelLarge, color = onRoute, maxLines = 1)
                    entry.lineName?.let { LinePill(line = it, mode = entry.mode, small = true) }
                }
            }
        },
    ) { measurables, constraints ->
        val gap = FjordDimens.space1.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxHeight = constraints.maxHeight)) }
        // "Then" and the entries that fit after it; nothing unless the first entry fits too.
        var width = placeables.first().width
        var count = 1
        while (count < placeables.size && width + gap + placeables[count].width <= constraints.maxWidth) {
            width += gap + placeables[count].width
            count++
        }
        val shown = if (count > 1) placeables.take(count) else emptyList()
        val height = shown.maxOfOrNull { it.height } ?: 0
        layout(if (shown.isEmpty()) 0 else width, height) {
            var x = 0
            shown.forEach { placeable ->
                placeable.placeRelative(x, (height - placeable.height) / 2)
                x += placeable.width + gap
            }
        }
    }
}

/** What [RouteThenLine] says to a screen reader: "Then 08:16 3, 08:19 5". */
@Composable
fun routeThenLineDescription(entries: List<ThenLineEntry>): String = stringResource(Res.string.route_then) + " " + entries.joinToString(", ") { listOfNotNull(it.clock, it.lineName).joinToString(" ") }

/**
 * The banner's "3 more" / "Fewer", in its trailing slot: opens or closes the alternatives below
 * it. [count] is how many there are to open.
 */
@Composable
fun RouteAlternativesToggle(
    count: Int,
    expanded: Boolean,
    destinationName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onRoute = PulseTheme.colors.onRoute
    val label = if (expanded) stringResource(Res.string.route_fewer) else pluralStringResource(Res.plurals.route_more, count, count)
    val description =
        stringResource(if (expanded) Res.string.route_fewer_description else Res.string.route_more_description, destinationName)
    Column(
        modifier =
            modifier
                .sizeIn(minWidth = FjordDimens.touch, minHeight = FjordDimens.touch)
                .clip(RoundedCornerShape(FjordDimens.radiusSm))
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .padding(horizontal = FjordDimens.space1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (expanded) PulseIcons.ExpandLess else PulseIcons.ExpandMore,
            contentDescription = null,
            tint = onRoute,
            modifier = Modifier.size(20.dp),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = onRoute, maxLines = 1)
    }
}

/**
 * The last row of an expanded card short of room: "+2 in details", which opens the route search on
 * that destination's routes, every one of them.
 */
@Composable
fun RouteMoreInDetailsRow(
    hidden: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onRoute = PulseTheme.colors.onRoute
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = FjordDimens.touch)
                .clickable(onClick = onClick)
                .padding(horizontal = FjordDimens.space4, vertical = FjordDimens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.route_more_in_details, hidden),
            style = MaterialTheme.typography.labelLarge,
            color = onRoute,
            modifier = Modifier.weight(1f),
        )
        Icon(PulseIcons.ChevronRight, contentDescription = null, tint = onRoute)
    }
}

/** "HH:mm" straight out of an ISO timestamp, which is already in local Oslo time. */
private fun isoClock(iso: String): String = if (iso.length >= ISO_MINUTE_END) iso.substring(ISO_HOUR_START, ISO_MINUTE_END) else "--:--"

private const val ISO_HOUR_START = 11
private const val ISO_MINUTE_END = 16
