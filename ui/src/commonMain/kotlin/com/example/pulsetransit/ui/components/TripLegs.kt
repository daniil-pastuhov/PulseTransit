package com.example.pulsetransit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.ui.resources.Res
import com.example.pulsetransit.ui.resources.fastest_route_info
import com.example.pulsetransit.ui.resources.leg_from
import com.example.pulsetransit.ui.resources.leg_walk
import org.jetbrains.compose.resources.stringResource

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
