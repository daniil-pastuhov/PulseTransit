package com.example.pulsetransit.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.example.pulsetransit.data.Departure
import com.example.pulsetransit.data.DepartureGroup
import com.example.pulsetransit.data.EnturRepository
import com.example.pulsetransit.data.JourneyStop
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.theme.ColorBus
import com.example.pulsetransit.theme.ColorDefaultLine
import com.example.pulsetransit.theme.ColorExpressTrain
import com.example.pulsetransit.theme.ColorFerry
import com.example.pulsetransit.theme.ColorMetro
import com.example.pulsetransit.theme.ColorTrain
import com.example.pulsetransit.theme.ColorTram
import com.example.pulsetransit.ui.layout.BoardLayout
import com.example.pulsetransit.ui.resources.*
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
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

// Wide rows' fixed columns, so each lines up from row to row.
private val PLATFORM_CHIP_WIDTH = 52.dp
private val WIDE_PRIMARY_TIME_WIDTH = 64.dp
private val WIDE_CLOCK_WIDTH = 56.dp
private val WIDE_DELAY_WIDTH = 28.dp

/** Rows from the end of the list at which the next page is requested, so it arrives before the end. */
private const val LOAD_MORE_THRESHOLD = 3

/**
 * One favorite stop's departures. [departureGroups] is null until the stop's first load finishes,
 * which shows a blurred placeholder; [isLoading] on a stop that already has data shows a thin
 * progress bar instead, so a refresh never blanks a board that is being read. [isFailed] means the
 * empty board is unknown rather than empty, and shows why with a retry instead of "no departures".
 * With [canLoadMore], scrolling near the end of the list calls [onLoadMore] for the next page;
 * [isLoadingMore] is that page in flight, shown as a spinner at the end of the list only. Tapping a
 * row opens its line's details, which list every loaded departure the row leaves out.
 * [header] replaces the stop's name and locality, as on-spot mode does with its stop picker.
 * [onDetailsOpenChange] reports when those details open and close, so a board taking turns
 * through its stops can hold the page they were opened from. [dragHandle], where the panel can be
 * moved, shows a grip in the header with that modifier, which does the dragging.
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
    canLoadMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onFilterOut: (String) -> Unit = {},
    onSearchFurtherAhead: () -> Unit = {},
    onRetry: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    loadJourney: suspend (Departure) -> List<JourneyStop> = { emptyList() },
    onRowCapacity: (Int) -> Unit = {},
    onDetailsOpenChange: (Boolean) -> Unit = {},
    dragHandle: Modifier? = null,
    header: (@Composable RowScope.() -> Unit)? = null,
) {
    // The tapped row, captured at the tap. The details follow refreshes through departuresLike,
    // which gathers the line's current departures and falls back to these once they have all gone.
    var detailsGroup by remember { mutableStateOf<DepartureGroup?>(null) }
    val detailsOpen = detailsGroup != null
    LaunchedEffect(detailsOpen) { onDetailsOpenChange(detailsOpen) }
    detailsGroup?.let { group ->
        DepartureDetailsSheet(
            stopName = stopPlace.name,
            departures = group.departuresLike(departureGroups.orEmpty()),
            showDelays = showDelays,
            showMessages = showMessages,
            onFilterOut = onFilterOut,
            loadJourney = loadJourney,
            onDismiss = { detailsGroup = null },
        )
    }
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
                if (header != null) {
                    header()
                } else {
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
                dragHandle?.let { handle ->
                    Icon(
                        imageVector = Icons.Default.DragIndicator,
                        contentDescription = stringResource(Res.string.drag_to_reorder),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier =
                            Modifier
                                .padding(start = 8.dp)
                                .size(24.dp)
                                .then(handle),
                    )
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
            val density = LocalDensity.current
            AnimatedContent(
                targetState = departureGroups,
                // How many rows fit, so the stop asks for enough departures to fill its panel.
                modifier =
                    Modifier
                        .weight(
                            1f,
                        ).onSizeChanged { onRowCapacity(BoardLayout.rowCapacity(with(density) { it.height.toDp().value })) },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "departures",
                contentKey = { it == null },
            ) { groups ->
                if (groups == null) {
                    BlurredPlaceholder(
                        label = stringResource(Res.string.loading_departures),
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
                                            Res.plurals.no_departures_extended,
                                            EnturRepository.EXTENDED_DEPARTURE_WINDOW_DAYS,
                                            EnturRepository.EXTENDED_DEPARTURE_WINDOW_DAYS,
                                        )
                                    } else {
                                        stringResource(Res.string.no_departures)
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
                                    Text(stringResource(Res.string.search_further_ahead))
                                }
                            }
                        }
                    }
                } else {
                    val listState = rememberLazyListState()
                    // Only the user's scrolling asks for a page. A list that fits the panel never
                    // does: a single line folds every page into the same row, so it would keep
                    // fitting and keep asking. Nor may a board left at its end on the wall grow on
                    // every auto-refresh, which restarts this effect.
                    LaunchedEffect(listState, canLoadMore, isLoading, isLoadingMore) {
                        if (!canLoadMore || isLoading || isLoadingMore) return@LaunchedEffect
                        snapshotFlow {
                            val info = listState.layoutInfo
                            // Empty until the list is first measured, which is not "at the end".
                            val last = info.visibleItemsInfo.lastOrNull() ?: return@snapshotFlow false
                            val nearEnd = last.index >= info.totalItemsCount - LOAD_MORE_THRESHOLD
                            nearEnd && listState.isScrollInProgress
                        }.first { it }
                        onLoadMore()
                    }
                    // A wide panel gives each row columns for the platform and the clock time
                    // instead of leaving the space between destination and time empty.
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isWide = BoardLayout.isWideRow(maxWidth.value)
                        val showPlatform = isWide && BoardLayout.showsPlatform(groups)
                        LazyColumn(
                            state = listState,
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
                                DepartureRow(
                                    group = group,
                                    showDelays = showDelays,
                                    showMessages = showMessages,
                                    isWide = isWide,
                                    showPlatform = showPlatform,
                                    onClick = { detailsGroup = group },
                                )
                            }
                            if (isLoadingMore) {
                                item(key = "load_more") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
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
    onClick: () -> Unit,
    isWide: Boolean = false,
    showPlatform: Boolean = false,
) {
    val departures = group.departures
    val firstDeparture = departures.first()
    val transportEmoji = getTransportEmoji(firstDeparture.transportMode)
    val pillColor = if (firstDeparture.isCancelled) Color.Gray else getTransportColor(firstDeparture.transportMode)
    val nowText = stringResource(Res.string.now)
    val cancelledText = stringResource(Res.string.cancelled)
    val minUnit = stringResource(Res.string.unit_minute)

    // Only the next few: a frequent line would otherwise list a timetable's worth of times.
    val hiddenCount = group.hiddenDepartureCount
    val formattedTimes =
        group.visibleDepartures
            .map { dep ->
                if (dep.isCancelled) AnnotatedString(cancelledText) else formatDepartureTime(dep.expectedTime, nowText, minUnit)
            }.distinct()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (firstDeparture.isCancelled) 0.2f else 0.5f))
                .clickable(onClick = onClick)
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

            if (showPlatform) {
                PlatformChip(platform = firstDeparture.platform)
                Spacer(modifier = Modifier.width(8.dp))
            }

            // The departure's clock time beside the countdown, in a column of its own. Left
            // empty when the countdown already is a clock time (perhaps with a "+1" after it),
            // or is "cancelled".
            if (isWide) {
                val clock = formatClock(firstDeparture.expectedTime)
                Text(
                    text = if (firstDeparture.isCancelled || formattedTimes.first().text.startsWith(clock)) "" else clock,
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        ),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(WIDE_CLOCK_WIDTH),
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Delay indicator. A wide row keeps a column of its own width for it, empty without a
            // delay, so a delayed row's clock and platform stay in line with the others.
            val hasDelay = (!firstDeparture.isCancelled) && showDelays && (firstDeparture.delayMinutes > 0)
            if (hasDelay || isWide) {
                Text(
                    text = if (hasDelay) "+${firstDeparture.delayMinutes}" else "",
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        ),
                    modifier = if (isWide) Modifier.width(WIDE_DELAY_WIDTH) else Modifier.padding(end = 4.dp),
                    maxLines = 1,
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
                // Right-aligned in a column of its own width, so "Now" and "12m" end in line.
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = if (isWide) WIDE_PRIMARY_TIME_WIDTH else 0.dp).padding(end = 4.dp),
                // Never broken over lines: in a very narrow panel "Now" wrapped to "No" over "w".
                maxLines = 1,
                softWrap = false,
            )
        }

        // Secondary Departure Times, then a muted count of the ones left out. On a line of their
        // own in a wide row too: beside the destination they squeezed it to "Fil…" in a tablet's
        // half-width panel.
        if (formattedTimes.size > 1 || hiddenCount > 0) {
            Text(
                text = laterTimesText(formattedTimes.drop(1), hiddenCount),
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 58.dp, top = 2.dp, end = 4.dp),
                // A backstop: the capped times wrap at most once even on a narrow panel.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
    }
}

/** The times after the next, then a muted count of the ones left out. */
@Composable
private fun laterTimesText(
    laterTimes: List<AnnotatedString>,
    hiddenCount: Int,
): AnnotatedString {
    val hiddenText = stringResource(Res.string.more_departures_hidden, hiddenCount)
    val hiddenColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    return buildAnnotatedString {
        laterTimes.forEachIndexed { index, time ->
            if (index > 0) append("    ")
            append(time)
        }
        if (hiddenCount > 0) {
            if (laterTimes.isNotEmpty()) append("    ")
            withStyle(SpanStyle(color = hiddenColor, fontSize = 0.85.em)) { append(hiddenText) }
        }
    }
}

/** The quay's public code in an outlined box; empty but the same width when the departure has none. */
@Composable
private fun PlatformChip(platform: String?) {
    val label = platform?.let { stringResource(Res.string.platform_label, it) }
    Box(
        modifier =
            Modifier
                .width(PLATFORM_CHIP_WIDTH)
                .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (platform != null) {
            Text(
                text = platform,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                modifier =
                    Modifier
                        .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .clearAndSetSemantics {},
                maxLines = 1,
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

internal fun getTransportColor(mode: String?): Color =
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

/** [expectedTimeStr]'s "HH:mm" in the device's time zone, or empty when it cannot be read. */
private fun formatClock(expectedTimeStr: String): String =
    try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        val formatted = expectedTimeStr.replace("Z", "+0000").replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
        sdf.parse(formatted)?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) }.orEmpty()
    } catch (_: Exception) {
        ""
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
