package com.example.pulsetransit.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.pulsetransit.R
import com.example.pulsetransit.data.DepartureGroup
import com.example.pulsetransit.data.EnturRepository
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.theme.ColorBus
import com.example.pulsetransit.theme.ColorDefaultLine
import com.example.pulsetransit.theme.ColorExpressTrain
import com.example.pulsetransit.theme.ColorFerry
import com.example.pulsetransit.theme.ColorMetro
import com.example.pulsetransit.theme.ColorTrain
import com.example.pulsetransit.theme.ColorTram
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L

/** Departures closer than this read as "now" rather than a countdown. */
private const val DEPARTING_NOW_SECONDS = 15L

/** Past this, a wall clock time is more useful than a countdown. */
private const val RELATIVE_TIME_CUTOFF_SECONDS = 1800L

// "HH:mm" within a yyyy-MM-ddTHH:mm:ss... timestamp.
private const val ISO_HOUR_START = 11
private const val ISO_MINUTE_END = 16
private const val MILLIS_PER_DAY = 86_400_000.0

// Size of the "+1" next-day marker relative to the time it follows.
private const val DAY_OFFSET_FONT_SCALE = 0.6f

/**
 * One favorite stop's departures. [departureGroups] is null until the stop's first load finishes,
 * which shows a blurred placeholder; [isLoading] on a stop that already has data shows a thin
 * progress bar instead, so a refresh never blanks a board that is being read. [isFailed] means the
 * empty board is unknown rather than empty, and shows why with a retry instead of "no departures".
 */
@Composable
fun StopBoardPanel(
    stopPlace: StopPlace,
    departureGroups: List<DepartureGroup>?,
    modifier: Modifier = Modifier,
    showDelays: Boolean = true,
    showMessages: Boolean = true,
    isLoading: Boolean = false,
    isExtended: Boolean = false,
    isFailed: Boolean = false,
    isOnline: Boolean = true,
    onFilterOut: (String) -> Unit = {},
    onSearchFurtherAhead: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Card(
        modifier =
            modifier
                .fillMaxSize()
                .padding(6.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
        ) {
            // Header
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stopPlace.name,
                        style =
                            MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    stopPlace.locality?.let {
                        Text(
                            text = it,
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                        )
                    }
                }
            }

            // Same height either way, so the list below doesn't jump when a refresh starts.
            if (isLoading && departureGroups != null) {
                RefreshingIndicator(modifier = Modifier.fillMaxWidth().height(1.dp))
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 1.dp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Departures list. Fades from the blurred placeholder into focus once the stop has
            // loaded; keyed on loaded-ness only, so later refreshes swap the rows in place.
            AnimatedContent(
                targetState = departureGroups,
                modifier = Modifier.weight(1f),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "departures",
                contentKey = { it == null },
            ) { groups ->
                if (groups == null) {
                    BlurredPlaceholder(
                        label = stringResource(R.string.loading_departures),
                        modifier =
                            Modifier
                                .fillMaxSize(),
                    ) {
                        LoadingPlaceholderList()
                    }
                } else if (groups.isEmpty() && isFailed) {
                    LoadFailedMessage(isOnline = isOnline, onRetry = onRetry, modifier = Modifier.fillMaxSize())
                } else if (groups.isEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text =
                                    if (isExtended) {
                                        pluralStringResource(
                                            R.plurals.no_departures_extended,
                                            EnturRepository.EXTENDED_DEPARTURE_WINDOW_DAYS,
                                            EnturRepository.EXTENDED_DEPARTURE_WINDOW_DAYS,
                                        )
                                    } else {
                                        stringResource(R.string.no_departures)
                                    },
                                style =
                                    MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                textAlign = TextAlign.Center,
                            )
                            if (!isExtended) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(onClick = onSearchFurtherAhead) {
                                    Text(stringResource(R.string.search_further_ahead))
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(
                            items = groups,
                            // Entur can return several service journeys that are identical in line,
                            // destination, time and quay (replacement buses, for one), and when two
                            // land non-consecutively they form two groups with the same identity.
                            // The index keeps the key unique, which Compose requires.
                            key = { index, group ->
                                val first = group.departures.first()
                                "${index}_${first.line}_${first.destination}_${first.expectedTime}_${first.quayId ?: ""}"
                            },
                        ) { _, group ->
                            DepartureRow(group, showDelays, showMessages, onFilterOut)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingPlaceholderList() {
    val alpha = rememberPlaceholderPulse()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(6) {
            DepartureRowPlaceholder(alpha = alpha)
        }
    }
}

@Composable
fun DepartureRowPlaceholder(alpha: Float) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Line badge
        PlaceholderBlock(alpha, Modifier.width(48.dp).height(24.dp), cornerRadius = 6.dp)
        Spacer(modifier = Modifier.width(10.dp))
        // Destination
        PlaceholderBlock(alpha, Modifier.weight(1f).height(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        // Time
        PlaceholderBlock(alpha, Modifier.width(40.dp).height(20.dp))
    }
}

@Composable
fun DepartureRow(
    group: DepartureGroup,
    showDelays: Boolean,
    showMessages: Boolean,
    onFilterOut: (String) -> Unit,
) {
    val departures = group.departures
    val firstDeparture = departures.first()
    val transportEmoji = getTransportEmoji(firstDeparture.transportMode)
    val pillColor = if (firstDeparture.isCancelled) Color.Gray else getTransportColor(firstDeparture.transportMode)
    val nowText = stringResource(R.string.now)
    val cancelledText = stringResource(R.string.cancelled)
    val minUnit = stringResource(R.string.unit_minute)

    val formattedTimes =
        departures
            .map { dep ->
                if (dep.isCancelled) AnnotatedString(cancelledText) else formatDepartureTime(dep.expectedTime, nowText, minUnit)
            }.distinct()

    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (firstDeparture.isCancelled) 0.2f else 0.5f))
                .clickable { showMenu = true }
                .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Line number badge
            Box(
                modifier =
                    Modifier
                        .width(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(pillColor)
                        .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = firstDeparture.line,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textDecoration = if (firstDeparture.isCancelled) TextDecoration.LineThrough else null,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Transport Icon & Destination
            Text(
                text = "$transportEmoji  ${firstDeparture.destination}",
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        color =
                            if (firstDeparture.isCancelled) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.6f,
                                )
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        textDecoration = if (firstDeparture.isCancelled) TextDecoration.LineThrough else null,
                    ),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Delay indicator
            if ((!firstDeparture.isCancelled) && showDelays && (firstDeparture.delayMinutes > 0)) {
                Text(
                    text = "+${firstDeparture.delayMinutes}",
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        ),
                    modifier = Modifier.padding(end = 4.dp),
                )
            }

            // Primary Departure Time
            val primaryTime = formattedTimes.first()
            val isNow = primaryTime.text == nowText

            Text(
                text = primaryTime,
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color =
                            when {
                                firstDeparture.isCancelled -> MaterialTheme.colorScheme.error
                                isNow -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                    ),
                modifier = Modifier.padding(end = 4.dp),
            )
        }

        // Secondary Departure Times
        if (formattedTimes.size > 1) {
            val secondaryTimes = formattedTimes.drop(1)
            Text(
                text =
                    buildAnnotatedString {
                        secondaryTimes.forEachIndexed { index, time ->
                            if (index > 0) append("    ")
                            append(time)
                        }
                    },
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 58.dp, top = 2.dp, end = 4.dp),
            )
        }

        val allNotices = departures.flatMap { it.notices }.distinct()
        if (showMessages && allNotices.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            allNotices.forEach { notice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⚠️",
                        fontSize = 10.sp,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(
                        text = notice,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 10.sp,
                            ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.filter_out_line, firstDeparture.line)) },
                onClick = {
                    onFilterOut(firstDeparture.line)
                    showMenu = false
                },
            )
        }
    }
}

internal fun getTransportEmoji(mode: String?): String =
    when (mode?.lowercase()) {
        "bus" -> "🚌"
        "tram" -> "🚃"
        "metro" -> "🚇"
        "rail" -> "🚆"
        "water" -> "⛴️"
        else -> "🚌" // fallback
    }

private fun getTransportColor(mode: String?): Color =
    when (mode?.lowercase()) {
        "bus" -> ColorBus
        "tram" -> ColorTram
        "metro" -> ColorMetro
        "rail" -> ColorTrain
        "airportrail" -> ColorExpressTrain
        "water" -> ColorFerry
        else -> ColorDefaultLine
    }

private fun startOfDayMillis(date: Date): Long =
    Calendar
        .getInstance()
        .apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

/** Calendar days from [now] to [time]; rounded so a DST shift inside the span doesn't skew it. */
private fun daysBetween(
    now: Date,
    time: Date,
): Int = ((startOfDayMillis(time) - startOfDayMillis(now)).toDouble() / MILLIS_PER_DAY).roundToInt()

/**
 * Beyond today is only reachable when the user widened the search window. Such times get a small
 * raised "+1" (or "+2", …) day marker, as printed timetables do, instead of a weekday prefix.
 */
private fun formatClockTime(
    time: Date,
    now: Date,
): AnnotatedString {
    val clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(time)
    val dayOffset = daysBetween(now, time)
    return buildAnnotatedString {
        append(clock)
        if (dayOffset > 0) {
            withStyle(SpanStyle(fontSize = DAY_OFFSET_FONT_SCALE.em, baselineShift = BaselineShift.Superscript)) {
                append("+$dayOffset")
            }
        }
    }
}

private fun formatDepartureTime(
    expectedTimeStr: String,
    nowText: String,
    minUnit: String,
): AnnotatedString =
    try {
        // Simple ISO parser for common formats
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        val formatted = expectedTimeStr.replace("Z", "+0000").replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
        val expectedDate = sdf.parse(formatted)
        val now = Date()

        if (expectedDate != null) {
            val diffSeconds = (expectedDate.time - now.time) / MILLIS_PER_SECOND
            when {
                diffSeconds < DEPARTING_NOW_SECONDS -> AnnotatedString(nowText)
                diffSeconds < SECONDS_PER_MINUTE -> AnnotatedString("1$minUnit")
                diffSeconds < RELATIVE_TIME_CUTOFF_SECONDS -> AnnotatedString("${diffSeconds / SECONDS_PER_MINUTE}$minUnit")
                else -> formatClockTime(expectedDate, now)
            }
        } else {
            AnnotatedString("?")
        }
    } catch (_: Exception) {
        // Fall back to slicing "HH:mm" straight out of the ISO-8601 timestamp.
        try {
            AnnotatedString(expectedTimeStr.substring(ISO_HOUR_START, ISO_MINUTE_END))
        } catch (_: Exception) {
            AnnotatedString("?")
        }
    }
