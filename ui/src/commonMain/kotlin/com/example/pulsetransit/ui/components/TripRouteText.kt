package com.example.pulsetransit.ui.components

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.ui.resources.Res
import com.example.pulsetransit.ui.resources.fastest_route_info
import com.example.pulsetransit.ui.resources.leg_from
import com.example.pulsetransit.ui.resources.leg_walk
import org.jetbrains.compose.resources.stringResource

// Icons are decorative: their alternate text would be read out, untranslated.
private const val SILENT = " "
private const val WALK = "walk"
private const val MODE_PREFIX = "mode:"
private val MODES = listOf("bus", "tram", "metro", "rail", "airportrail", "water", "air", "other")

/**
 * A trip's legs on one line, "[metro] 2 from Oslo S → [walk] 4 min", with the mode icons drawn
 * inline in the text colour; or its first line and origin if Entur sent no legs.
 */
@Composable
fun TripRouteText(
    trip: TripInfo,
    style: TextStyle,
    color: Color,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    val from = trip.legs.associateWith { leg -> leg.fromName?.let { stringResource(Res.string.leg_from, it) } }
    val walks = trip.legs.associateWith { leg -> stringResource(Res.string.leg_walk, leg.durationMinutes) }
    val text =
        if (trip.legs.isEmpty()) {
            buildAnnotatedString { append(stringResource(Res.string.fastest_route_info, trip.lineName, trip.originName)) }
        } else {
            buildAnnotatedString {
                var first = true
                trip.legs.forEach { leg ->
                    // 0-minute foot legs are quay transfers, not walks worth showing.
                    if (leg.lineName == null && leg.durationMinutes <= 0) return@forEach
                    if (!first) append("  →  ")
                    first = false
                    if (leg.lineName != null) {
                        val mode = leg.mode?.lowercase()?.takeIf { it in MODES } ?: "other"
                        appendInlineContent(MODE_PREFIX + mode, SILENT)
                        append(" ${leg.lineName}")
                        from[leg]?.let { append(" $it") }
                    } else {
                        appendInlineContent(WALK, SILENT)
                        append(" ${walks[leg]}")
                    }
                }
            }
        }
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        inlineContent = remember(color) { routeIcons(color) },
        modifier = modifier,
    )
}

private fun icon(
    vector: ImageVector,
    tint: Color,
) = InlineTextContent(Placeholder(1.2.em, 1.2.em, PlaceholderVerticalAlign.TextCenter)) {
    Icon(vector, contentDescription = null, tint = tint)
}

private fun routeIcons(tint: Color): Map<String, InlineTextContent> = MODES.associate { MODE_PREFIX + it to icon(transportIcon(it), tint) } + (WALK to icon(PulseIcons.DirectionsWalk, tint))
