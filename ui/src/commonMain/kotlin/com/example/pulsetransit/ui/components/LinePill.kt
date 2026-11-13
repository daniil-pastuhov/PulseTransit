package com.example.pulsetransit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.theme.PulseTheme
import com.example.pulsetransit.theme.type

/** A transport mode's icon, for an Entur mode ("bus", "rail", "water"…). Also used by the widgets. */
fun transportIcon(mode: String?): ImageVector =
    when (mode?.lowercase()) {
        "tram" -> PulseIcons.Tram
        "metro" -> PulseIcons.Subway
        "rail", "airportrail" -> PulseIcons.Train
        "water" -> PulseIcons.DirectionsBoat
        "air" -> PulseIcons.Flight
        "foot" -> PulseIcons.DirectionsWalk
        else -> PulseIcons.DirectionsBus
    }

/** A line's pill colour in the current theme; grey when the departure is cancelled. */
@Composable
fun lineColor(
    mode: String?,
    cancelled: Boolean = false,
): Color = if (cancelled) MaterialTheme.colorScheme.outline else PulseTheme.colors.lines.forMode(mode)

/**
 * A line number on its mode's colour. Never truncated: a four-character line ("RE30", "FLY2") widens
 * the pill instead. The text is left to the row's accessibility label, which names the mode.
 */
@Composable
fun LinePill(
    line: String,
    mode: String?,
    modifier: Modifier = Modifier,
    cancelled: Boolean = false,
    minWidth: Dp = FjordDimens.pillW,
) {
    Box(
        modifier =
            modifier
                .defaultMinSize(minWidth = minWidth)
                .clip(RoundedCornerShape(FjordDimens.radiusSm))
                .background(lineColor(mode, cancelled))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = line,
            style = PulseTheme.type.pill,
            color = PulseTheme.colors.lines.onLine,
            textDecoration = if (cancelled) TextDecoration.LineThrough else null,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The mode's icon at text size, in the surrounding text colour; decorative. */
@Composable
fun TransportIcon(
    mode: String?,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Icon(transportIcon(mode), contentDescription = null, modifier = modifier.size(size), tint = tint)
}
