package com.example.pulsetransit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.pulsetransit.ui.components.loadFailureIcon
import com.example.pulsetransit.ui.components.rememberPlaceholderPulse
import com.example.pulsetransit.ui.components.tripClockText
import com.example.pulsetransit.ui.components.tripDurationText
import com.example.pulsetransit.ui.layout.BoardLayout
import com.example.pulsetransit.ui.resources.Res
import com.example.pulsetransit.ui.resources.error_retrying_automatically
import com.example.pulsetransit.ui.resources.fastest_route_info
import com.example.pulsetransit.ui.resources.fastest_route_to
import com.example.pulsetransit.ui.resources.fastest_route_unavailable
import com.example.pulsetransit.ui.resources.leg_from
import com.example.pulsetransit.ui.resources.leg_walk
import com.example.pulsetransit.ui.resources.loading_fastest_route
import com.example.pulsetransit.ui.resources.no_route_to
import com.example.pulsetransit.ui.resources.no_route_to_arriving
import com.example.pulsetransit.ui.resources.route_arrival
import com.example.pulsetransit.ui.resources.route_leave
import org.jetbrains.compose.resources.stringResource

// The fastest-route cards above the stops: "Leave 19:40 | Fastest to X, walk 9 min → FLY2 from
// Oslo S | 28 min, arr. 20:08". They use the `route` colours, which stay dark on the wall at night.

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
) {
    val onRoute = PulseTheme.colors.onRoute
    val leave = isoClock(trip.expectedStartTime)
    val arrival = tripArrival(trip)?.let { if (it.dayOffset > 0) "${it.time}+${it.dayOffset}" else it.time }
    val title = stringResource(Res.string.fastest_route_to, trip.destinationName)
    val duration = tripDurationText(trip.durationMinutes)
    val leaveLabel = stringResource(Res.string.route_leave)
    val arrivalText = arrival?.let { stringResource(Res.string.route_arrival, it) }
    val description = listOfNotNull(title, "$leaveLabel $leave", duration, arrivalText).joinToString(", ")

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compact = BoardLayout.isCompactBanner(maxWidth.value)
        val leaveBlock: @Composable () -> Unit = {
            Column {
                Text(leaveLabel, style = MaterialTheme.typography.labelSmall, color = onRoute)
                Text(
                    leave,
                    style = PulseTheme.type.countdown.copy(fontSize = LeaveTimeSize, lineHeight = LeaveTimeLine),
                    color = PulseTheme.colors.routeAccent,
                    maxLines = 1,
                )
            }
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
            }
        }
        val durationBlock: @Composable () -> Unit = {
            Column(horizontalAlignment = Alignment.End) {
                Text(duration, style = MaterialTheme.typography.labelLarge, color = onRoute, maxLines = 1)
                arrivalText?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = onRoute, maxLines = 1) }
            }
        }
        RouteCard(onClick = onClick, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
            if (compact) {
                // Too narrow for three columns: the title and legs go under the times.
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FjordDimens.space2)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        leaveBlock()
                        Box(modifier = Modifier.weight(1f))
                        durationBlock()
                    }
                    titleBlock(Modifier.fillMaxWidth())
                }
            } else {
                leaveBlock()
                titleBlock(Modifier.weight(1f))
                durationBlock()
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
 * A trip's legs as a wrapping chain: "[walk] 9 min → [FLY2] from Oslo S → [walk] 4 min", or its
 * first line and origin if Entur sent no legs. Zero-minute foot legs are quay transfers and left out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripLegs(
    trip: TripInfo,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val style = MaterialTheme.typography.bodyMedium
    val legs = trip.legs.filter { it.lineName != null || it.durationMinutes > 0 }
    if (legs.isEmpty()) {
        Text(
            stringResource(Res.string.fastest_route_info, trip.lineName, trip.originName),
            style = style,
            color = color,
            modifier = modifier,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    val walk = legs.map { stringResource(Res.string.leg_walk, it.durationMinutes) }
    val from = legs.map { leg -> leg.fromName?.let { stringResource(Res.string.leg_from, it) } }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(FjordDimens.space1),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        maxLines = maxLines,
    ) {
        legs.forEachIndexed { index, leg ->
            if (index > 0) LegIcon(PulseIcons.ArrowForward, color)
            val line = leg.lineName
            if (line == null) {
                LegIcon(PulseIcons.DirectionsWalk, color)
                Text(walk[index], style = style, color = color, maxLines = 1)
            } else {
                LinePill(line = line, mode = leg.mode, small = true)
                from[index]?.let { Text(it, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun LegIcon(
    icon: ImageVector,
    tint: Color,
) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
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

/** "HH:mm" straight out of an ISO timestamp, which is already in local Oslo time. */
private fun isoClock(iso: String): String = if (iso.length >= ISO_MINUTE_END) iso.substring(ISO_HOUR_START, ISO_MINUTE_END) else "--:--"

private const val ISO_HOUR_START = 11
private const val ISO_MINUTE_END = 16
