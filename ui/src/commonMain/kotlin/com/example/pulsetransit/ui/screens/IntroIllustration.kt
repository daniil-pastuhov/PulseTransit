package com.example.pulsetransit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.IntroPage
import com.example.pulsetransit.theme.ColorTrain
import com.example.pulsetransit.theme.ColorTram
import com.example.pulsetransit.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * A drawn, made-up piece of the board for an intro page: the element the page talks about is
 * outlined and the rest is dimmed, so a new user, whose board is still empty, sees what to look
 * for. Decorative only; the page's text says the same to a screen reader.
 */
@Composable
internal fun IntroIllustration(
    page: IntroPage,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                .padding(12.dp)
                .clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (page) {
            IntroPage.NEAREST_STOP -> {
                MockHeader(spot = Spot.DIM)
                MockStopCard(
                    name = "Stortorvet",
                    subtitle = "46 m · Oslo",
                    nameSpot = Spot.FOCUS,
                    rowsSpot = Spot.DIM,
                    pickable = true,
                    tapHint = true,
                )
            }
            IntroPage.ROUTES_FROM_HERE -> {
                MockHeader(spot = Spot.DIM, focus = HeaderButton.SEARCH)
                MockRouteCard(spot = Spot.FOCUS)
                MockStopCard(name = "Stortorvet", subtitle = "46 m · Oslo", nameSpot = Spot.DIM, rowsSpot = Spot.DIM, rows = 1)
            }
            IntroPage.RESIZE -> {
                MockRouteCard(spot = Spot.DIM)
                MockResizeHandle()
                MockStopCard(name = "Stortorvet", subtitle = "46 m · Oslo", nameSpot = Spot.DIM, rowsSpot = Spot.DIM, rows = 1)
            }
            IntroPage.CHOOSE_STOPS -> {
                MockHeader(spot = Spot.DIM, focus = HeaderButton.SETTINGS)
                MockStopCard(name = "Jernbanetorget", subtitle = "Oslo", nameSpot = Spot.FOCUS, rowsSpot = Spot.PLAIN, rows = 1)
                MockStopCard(name = "Oslo S", subtitle = "Oslo", nameSpot = Spot.FOCUS, rowsSpot = Spot.PLAIN, rows = 1, train = true)
                Text(
                    text = stringResource(Res.string.board_page_stops, 1, 2, 5),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            IntroPage.FASTEST_ROUTES -> {
                MockHeader(spot = Spot.DIM)
                MockFastestBanner()
                MockStopCard(name = "Jernbanetorget", subtitle = "Oslo", nameSpot = Spot.DIM, rowsSpot = Spot.DIM, rows = 1)
            }
            IntroPage.TAP_FOR_DETAILS -> {
                MockStopCard(name = "Jernbanetorget", subtitle = "Oslo", nameSpot = Spot.DIM, rowsSpot = Spot.DIM, focusRow = 0)
                MockDetailsSheet()
            }
            IntroPage.SCREEN_CARE -> MockScreenCareSettings()
        }
    }
}

/** How an element of a mock is drawn: dimmed, as is, or outlined as the thing the page is about. */
private enum class Spot { DIM, PLAIN, FOCUS }

private enum class HeaderButton { SEARCH, SETTINGS }

@Composable
private fun Modifier.spot(spot: Spot): Modifier =
    when (spot) {
        Spot.DIM -> alpha(0.3f)
        Spot.PLAIN -> this
        Spot.FOCUS -> border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
    }

@Composable
private fun MockCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun MockHeader(
    spot: Spot,
    focus: HeaderButton? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            "08:12",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.spot(spot),
        )
        Spacer(Modifier.weight(1f))
        HeaderIcon(Icons.Default.Search, if (focus == HeaderButton.SEARCH) Spot.FOCUS else spot)
        HeaderIcon(Icons.Default.Refresh, spot)
        HeaderIcon(Icons.Default.Settings, if (focus == HeaderButton.SETTINGS) Spot.FOCUS else spot)
    }
}

@Composable
private fun HeaderIcon(
    icon: ImageVector,
    spot: Spot,
) {
    Box(modifier = Modifier.size(36.dp).spot(spot), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
        if (spot == Spot.FOCUS) TapHint(Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 14.dp))
    }
}

@Composable
private fun TapHint(modifier: Modifier = Modifier) {
    Icon(
        Icons.Default.TouchApp,
        contentDescription = null,
        modifier = modifier.size(22.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun MockStopCard(
    name: String,
    subtitle: String,
    nameSpot: Spot,
    rowsSpot: Spot,
    rows: Int = 2,
    pickable: Boolean = false,
    tapHint: Boolean = false,
    focusRow: Int? = null,
    train: Boolean = false,
) {
    MockCard {
        Box(modifier = Modifier.spot(nameSpot).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    if (pickable) Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (tapHint) TapHint(Modifier.align(Alignment.BottomEnd).offset(x = 18.dp, y = 10.dp))
        }
        val departures =
            if (train) {
                listOf(Triple("R13", ColorTrain, "🚆  Dal"), Triple("RE20", ColorTrain, "🚆  Halden"))
            } else {
                listOf(Triple("19", ColorTram, "🚃  Majorstuen"), Triple("17", ColorTram, "🚃  Sinsen"))
            }
        departures.take(rows).forEachIndexed { index, (line, color, destination) ->
            val rowSpot =
                if (focusRow == index) {
                    Spot.FOCUS
                } else if (focusRow != null) {
                    Spot.DIM
                } else {
                    rowsSpot
                }
            Box {
                MockDepartureRow(line, color, destination, "${3 + index * 4}m", Modifier.spot(rowSpot))
                if (focusRow == index) TapHint(Modifier.align(Alignment.BottomEnd).offset(x = (-24).dp, y = 14.dp))
            }
        }
    }
}

@Composable
private fun MockDepartureRow(
    line: String,
    color: Color,
    destination: String,
    time: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
                    .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(line, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            destination,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(time, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun MockRouteCard(spot: Spot) {
    MockCard(modifier = Modifier.spot(spot)) {
        Text(
            stringResource(Res.string.route_to, "Oslo S"),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("08:14", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.leg_walk, 2) + "  →  🚃 19", style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(Res.string.route_arrival, "08:29"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(Res.string.duration_min, 15),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun MockResizeHandle() {
    Box(modifier = Modifier.fillMaxWidth().height(36.dp).spot(Spot.FOCUS), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant),
        )
        Icon(
            Icons.Default.UnfoldMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
        )
    }
}

@Composable
private fun MockFastestBanner() {
    Column(modifier = Modifier.spot(Spot.FOCUS).padding(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(10.dp),
        ) {
            Text(
                stringResource(Res.string.fastest_route_to, "Lillestrøm"),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "08:23",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(Res.string.fastest_route_info, "RE10", "Oslo S"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            stringResource(Res.string.schedule_summary, stringResource(Res.string.schedule_weekdays), "07:00", "09:00"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun MockDetailsSheet() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .width(32.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant),
        )
        Text(stringResource(Res.string.details_departures), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        Text(
            "08:15 · 08:25 · 08:35",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(Res.string.filter_out_line, "19"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun MockScreenCareSettings() {
    MockCard {
        MockSettingRow(stringResource(Res.string.pixel_shift_level), stringResource(Res.string.burn_in_medium))
        MockSettingRow(stringResource(Res.string.auto_dim_delay), stringResource(Res.string.dim_30s))
        MockSettingRow(stringResource(Res.string.wake_camera), null)
        MockSettingRow(stringResource(Res.string.day_night_mode), stringResource(Res.string.theme_auto))
    }
}

@Composable
private fun MockSettingRow(
    label: String,
    value: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().spot(Spot.FOCUS).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        if (value == null) {
            Switch(checked = true, onCheckedChange = null)
        } else {
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
