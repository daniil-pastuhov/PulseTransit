package com.example.pulsetransit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.TripLeg
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.ui.layout.WrappedRows
import com.example.pulsetransit.ui.resources.Res
import com.example.pulsetransit.ui.resources.fastest_route_info
import com.example.pulsetransit.ui.resources.leg_from
import com.example.pulsetransit.ui.resources.leg_walk
import com.example.pulsetransit.ui.resources.legs_hidden
import org.jetbrains.compose.resources.stringResource

/**
 * A trip's legs as a chain that wraps onto more lines: "[walk] 9 min → [FLY2] from Oslo S →
 * [walk] 4 min", or its first line and origin if Entur sent no legs. Zero-minute foot legs are
 * quay transfers and left out. With a [maxLines] limit, as in the route rows, it shows the start
 * of the journey without the boarding stops and, when the rest doesn't fit, ends with how many legs
 * are hidden: the trip's details show them all, with their stops. A line only breaks between legs. Where it breaks is [WrappedRows], which the iOS app uses too.
 */
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
    val limited = maxLines != Int.MAX_VALUE
    val spacing = FjordDimens.space1
    val lineSpacing = 2.dp
    Layout(
        modifier = modifier,
        content = {
            legs.forEachIndexed { index, leg ->
                LegItem(leg, index, showsFrom = !limited, color = color)
            }
            // One marker per count that can be hidden; the layout places only the one it needs. Screen
            // readers skip them all: they read every leg, the hidden ones too.
            if (limited) {
                for (hidden in 1..legs.size) {
                    Text(
                        stringResource(Res.string.legs_hidden, hidden),
                        style = style,
                        color = color,
                        maxLines = 1,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val itemConstraints = Constraints(maxWidth = maxWidth)
        val items = measurables.take(legs.size).map { it.measure(itemConstraints) }
        val markers = measurables.drop(legs.size).map { it.measure(itemConstraints) }
        val gap = spacing.roundToPx()
        val arrangement =
            WrappedRows.arrange(
                widths = items.map { it.width.toFloat() },
                maxWidth = maxWidth.toFloat(),
                spacing = gap.toFloat(),
                maxLines = maxLines,
                markerWidth = markers.maxOfOrNull { it.width }?.toFloat() ?: 0f,
            )
        val marker = markers.getOrNull(arrangement.hidden - 1)
        val rows =
            arrangement.rowEnds.mapIndexed { row, end ->
                val start = if (row == 0) 0 else arrangement.rowEnds[row - 1]
                val placeables = items.subList(start, end).toMutableList()
                if (marker != null && row == arrangement.rowEnds.lastIndex) placeables += marker
                placeables
            }
        val rowHeights = rows.map { row -> row.maxOfOrNull { it.height } ?: 0 }
        val width =
            rows
                .maxOf { row -> row.sumOf { it.width } + gap * (row.size - 1).coerceAtLeast(0) }
                .coerceIn(constraints.minWidth, constraints.maxWidth)
        val height =
            (rowHeights.sum() + lineSpacing.roundToPx() * (rows.size - 1))
                .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            var y = 0
            rows.forEachIndexed { index, row ->
                var x = 0
                row.forEach { placeable ->
                    placeable.place(x, y + (rowHeights[index] - placeable.height) / 2)
                    x += placeable.width + gap
                }
                y += rowHeights[index] + lineSpacing.roundToPx()
            }
        }
    }
}

/** One leg with the arrow before it, so a wrapped chain only breaks between legs. */
@Composable
private fun LegItem(
    leg: TripLeg,
    index: Int,
    showsFrom: Boolean,
    color: Color,
) {
    val style = MaterialTheme.typography.bodyMedium
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FjordDimens.space1)) {
        if (index > 0) LegIcon(PulseIcons.ArrowForward, color)
        val line = leg.lineName
        if (line == null) {
            LegIcon(PulseIcons.DirectionsWalk, color)
            Text(stringResource(Res.string.leg_walk, leg.durationMinutes), style = style, color = color, maxLines = 1)
        } else {
            LinePill(line = line, mode = leg.mode, small = true)
            val from = leg.fromName
            if (showsFrom && from != null) {
                Text(
                    stringResource(Res.string.leg_from, from),
                    style = style,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
