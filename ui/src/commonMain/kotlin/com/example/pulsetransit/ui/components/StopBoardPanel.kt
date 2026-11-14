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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
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
import com.example.pulsetransit.data.DepartureCountdown
import com.example.pulsetransit.data.DepartureGroup
import com.example.pulsetransit.data.DirectionSummary
import com.example.pulsetransit.data.EnturRepository
import com.example.pulsetransit.data.JourneyStop
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.clockTimeText
import com.example.pulsetransit.data.departureClock
import com.example.pulsetransit.data.departureCountdown
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.theme.PulseTheme
import com.example.pulsetransit.theme.type
import com.example.pulsetransit.ui.layout.BoardLayout
import com.example.pulsetransit.ui.resources.*
import com.example.pulsetransit.ui.resources.cancelled
import com.example.pulsetransit.ui.resources.details_every
import com.example.pulsetransit.ui.resources.directions_all
import com.example.pulsetransit.ui.resources.directions_count
import com.example.pulsetransit.ui.resources.directions_platforms
import com.example.pulsetransit.ui.resources.directions_some
import com.example.pulsetransit.ui.resources.loading_departures
import com.example.pulsetransit.ui.resources.more_departures_hidden
import com.example.pulsetransit.ui.resources.no_departures_body
import com.example.pulsetransit.ui.resources.now
import com.example.pulsetransit.ui.resources.panel_failed_body
import com.example.pulsetransit.ui.resources.panel_failed_title
import com.example.pulsetransit.ui.resources.panel_offline_body
import com.example.pulsetransit.ui.resources.platform_label
import com.example.pulsetransit.ui.resources.stale_no_internet_title
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

// Size of the "+1" next-day marker relative to the time it follows.
private const val DAY_OFFSET_FONT_SCALE = 0.6f

/** Rows from the end of the list at which the next page is requested, so it arrives before the end. */
private const val LOAD_MORE_THRESHOLD = 3

/** A row's destination and time grow on a wide panel, read from across the room. */
private val WIDE_DESTINATION_SIZE = 19.sp
private val WIDE_TIME_SIZE = 24.sp

private val PanelShape = RoundedCornerShape(FjordDimens.radiusLg)

/**
 * One favorite stop's departures. [departureGroups] is null until the stop's first load finishes,
 * which shows skeleton rows under the stop's name; [isLoading] on a stop that already has data
 * shows a thin progress bar instead, so a refresh never blanks a board that is being read.
 * [isFailed] means the empty board is unknown rather than empty, and says why instead of "no
 * departures". With [canLoadMore], scrolling near the end of the list calls [onLoadMore] for the
 * next page; [isLoadingMore] is that page in flight, shown as a spinner at the end of the list
 * only. Tapping a row opens its line's details, which list every loaded departure the row leaves
 * out. [directions] says which directions the panel shows, beside the locality. [header] replaces
 * the stop's name and that line, as on-spot mode does with its stop picker. With [onOpenDetails]
 * the tapped row goes there instead and the caller shows the details: a board taking turns through
 * its stops keeps them above its pages, where turning the device, which moves the panels between
 * pages, does not close them. [dragHandle], where the panel can be moved, shows a grip in the
 * header with that modifier, which does the dragging.
 */
@Composable
fun StopBoardPanel(
    stopPlace: StopPlace,
    departureGroups: List<DepartureGroup>?,
    modifier: Modifier = Modifier,
    directions: DirectionSummary? = null,
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
    onLoadMore: () -> Unit = {},
    loadJourney: suspend (Departure) -> List<JourneyStop> = { emptyList() },
    onRowCapacity: (Int) -> Unit = {},
    onOpenDetails: ((DepartureGroup) -> Unit)? = null,
    dragHandle: Modifier? = null,
    header: (@Composable RowScope.() -> Unit)? = null,
) {
    // The tapped row, captured at the tap. The details follow refreshes through departuresLike,
    // which gathers the line's current departures and falls back to these once they have all gone.
    // Keyed by stop, since turning the device can move the panel within the layout.
    var detailsGroup by rememberSaveable(key = "departure_details_${stopPlace.id}", stateSaver = DepartureGroupSaver) {
        mutableStateOf<DepartureGroup?>(null)
    }
    val openDetails: (DepartureGroup) -> Unit = onOpenDetails ?: { detailsGroup = it }
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
    val isDark = MaterialTheme.colorScheme.background.luminance() < DARK_LUMINANCE
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    // Light: a soft shadow lifts the card off the grey; dark: a hairline ring on black.
                    .then(
                        if (isDark) {
                            Modifier.border(1.dp, Color.White.copy(alpha = DARK_RING_ALPHA), PanelShape)
                        } else {
                            Modifier.shadow(1.dp, PanelShape)
                        },
                    ).clip(PanelShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(start = FjordDimens.space4, end = FjordDimens.space4, top = FjordDimens.space3, bottom = FjordDimens.space2),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = FjordDimens.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (header != null) {
                    header()
                } else {
                    Row(
                        modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { heading() },
                        horizontalArrangement = Arrangement.spacedBy(FjordDimens.space2),
                    ) {
                        Text(
                            text = stopPlace.name,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.alignByBaseline(),
                        )
                        stopSubtitle(stopPlace.locality, directions)?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                // The name is measured first and keeps its room; this takes what is left.
                                modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
                            )
                        }
                    }
                }
                dragHandle?.let { handle ->
                    Icon(
                        imageVector = PulseIcons.DragIndicator,
                        contentDescription = stringResource(Res.string.drag_to_reorder),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier
                                .padding(start = FjordDimens.space2)
                                .size(24.dp)
                                .then(handle),
                    )
                }
            }

            // Same height either way, so the list below doesn't jump when a refresh starts.
            if (isLoading && departureGroups != null) {
                RefreshingIndicator(modifier = Modifier.fillMaxWidth().height(1.dp))
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
            }

            // The rows. Fades from the skeleton once the stop has loaded; keyed on loaded-ness only, so
            // later refreshes swap the rows in place.
            val density = LocalDensity.current
            AnimatedContent(
                targetState = departureGroups,
                // How many rows fit, so the stop asks for enough departures to fill its panel.
                modifier =
                    Modifier
                        .weight(1f)
                        .onSizeChanged { onRowCapacity(BoardLayout.rowCapacity(with(density) { it.height.toDp().value })) },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "departures",
                contentKey = { it == null },
            ) { groups ->
                when {
                    groups == null -> LoadingPlaceholderList()
                    groups.isEmpty() && isFailed ->
                        PanelMessage(
                            icon = loadFailureIcon(isOnline),
                            title =
                                if (isOnline) stringResource(Res.string.panel_failed_title) else stringResource(Res.string.stale_no_internet_title),
                            body =
                                if (isOnline) stringResource(Res.string.panel_failed_body) else stringResource(Res.string.panel_offline_body),
                        )
                    groups.isEmpty() ->
                        PanelMessage(
                            icon = PulseIcons.EventBusy,
                            title =
                                if (isExtended) {
                                    pluralStringResource(
                                        Res.plurals.no_departures_extended,
                                        EnturRepository.EXTENDED_DEPARTURE_WINDOW_DAYS,
                                        EnturRepository.EXTENDED_DEPARTURE_WINDOW_DAYS,
                                    )
                                } else {
                                    stringResource(Res.string.no_departures)
                                },
                            body =
                                if (isExtended) {
                                    null
                                } else {
                                    stringResource(Res.string.no_departures_body, EnturRepository.DEFAULT_DEPARTURE_WINDOW_SECONDS / SECONDS_PER_HOUR)
                                },
                        ) {
                            if (!isExtended) {
                                TextButton(onClick = onSearchFurtherAhead) { Text(stringResource(Res.string.search_further_ahead)) }
                            }
                        }
                    else -> {
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
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val isWide = BoardLayout.isWideRow(maxWidth.value)
                            val showPlatform = BoardLayout.showsPlatform(groups)
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
                                ) { index, group ->
                                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                                    DepartureRow(
                                        group = group,
                                        showDelays = showDelays,
                                        showMessages = showMessages,
                                        isWide = isWide,
                                        showPlatform = showPlatform,
                                        onClick = { openDetails(group) },
                                    )
                                }
                                if (isLoadingMore) {
                                    item(key = "load_more") {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = FjordDimens.space2),
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
}

private const val DARK_LUMINANCE = 0.2f
private const val DARK_RING_ALPHA = 0.06f
private const val SECONDS_PER_HOUR = 3600

/** "Oslo · all directions", "Platforms 2–4" or "3 of 7 directions"; null with nothing to say. */
@Composable
private fun stopSubtitle(
    locality: String?,
    directions: DirectionSummary?,
): String? {
    val summary =
        when (directions) {
            null -> null
            DirectionSummary.All -> stringResource(Res.string.directions_all)
            is DirectionSummary.Platforms ->
                if (directions.count == 1) {
                    stringResource(Res.string.platform_label, directions.codes)
                } else {
                    stringResource(Res.string.directions_platforms, directions.codes)
                }
            is DirectionSummary.Some ->
                directions.total?.let { stringResource(Res.string.directions_some, directions.count, it) }
                    ?: pluralStringResource(Res.plurals.directions_count, directions.count, directions.count)
        }
    // A picked set of platforms says enough on its own; "all directions" reads with the place.
    return when (directions) {
        is DirectionSummary.Platforms, is DirectionSummary.Some -> summary
        else -> listOfNotNull(locality, summary).joinToString(" · ").ifEmpty { null }
    }
}

/** A panel's own message in place of its rows: an icon, a title, a line of explanation. */
@Composable
private fun PanelMessage(
    icon: ImageVector,
    title: String,
    body: String?,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = FjordDimens.space4, vertical = FjordDimens.space6)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FjordDimens.space2, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        body?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        action?.invoke()
    }
}

/** Skeleton rows while a stop's first departures load: no spinner, no label (the name says which). */
@Composable
fun LoadingPlaceholderList() {
    val alpha = rememberPlaceholderPulse()
    val description = stringResource(Res.string.loading_departures)
    Column(modifier = Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = description }) {
        repeat(SKELETON_ROWS) { index ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
            Row(
                modifier = Modifier.fillMaxWidth().height(FjordDimens.row),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FjordDimens.space2),
            ) {
                PlaceholderBlock(alpha, Modifier.width(FjordDimens.pillW).height(28.dp))
                PlaceholderBlock(alpha, Modifier.weight(1f).height(14.dp))
                PlaceholderBlock(alpha, Modifier.width(48.dp).height(18.dp))
            }
        }
    }
}

private const val SKELETON_ROWS = 5

/**
 * One line to one destination: pill, mode, destination with a detail line under it, then the delay
 * and the time of its next departure. Later departures are in the row's details.
 */
@Composable
fun DepartureRow(
    group: DepartureGroup,
    showDelays: Boolean,
    showMessages: Boolean,
    onClick: () -> Unit,
    isWide: Boolean = false,
    showPlatform: Boolean = false,
) {
    val departure = group.departures.first()
    val cancelled = departure.isCancelled
    val delayed = !cancelled && showDelays && departure.delayMinutes > 0
    val subLines = departureSubLines(group, showPlatform)
    val notices = if (showMessages) group.departures.flatMap { it.notices }.distinct() else emptyList()
    val destinationStyle =
        MaterialTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.Medium,
            fontSize = if (isWide) WIDE_DESTINATION_SIZE else MaterialTheme.typography.bodyLarge.fontSize,
        )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = FjordDimens.row)
                .clickable(onClick = onClick)
                .padding(vertical = FjordDimens.space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FjordDimens.space2),
    ) {
        LinePill(
            line = departure.line,
            mode = departure.transportMode,
            cancelled = cancelled,
            modifier = Modifier.alpha(if (cancelled) CANCELLED_PILL_ALPHA else 1f),
        )
        TransportIcon(departure.transportMode, size = 20.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = departure.destination,
                style = destinationStyle,
                color = if (cancelled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subLines.isNotEmpty()) {
                Text(
                    text = subLines.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            notices.forEach { notice ->
                Row(horizontalArrangement = Arrangement.spacedBy(FjordDimens.space1), verticalAlignment = Alignment.CenterVertically) {
                    Icon(PulseIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(14.dp))
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (delayed) DelayChip(departure.delayMinutes)
        DepartureTime(departure = departure, delayed = delayed, isWide = isWide)
    }
}

private const val CANCELLED_PILL_ALPHA = 0.55f

/** "Platform 4", "Every ~8 min", "Every ~5 min · +4 more": what else to know about the row. */
@Composable
private fun departureSubLines(
    group: DepartureGroup,
    showPlatform: Boolean,
): List<String> {
    val platform =
        group.departures
            .first()
            .platform
            ?.takeIf { showPlatform }
    return listOfNotNull(
        platform?.let { stringResource(Res.string.platform_label, it) },
        group.headwayMinutes?.let { stringResource(Res.string.details_every, it) },
        group.moreDepartures?.let { stringResource(Res.string.more_departures_hidden, it) },
    )
}

/**
 * The time column: "Now", "4 min" or "19:56"; "Cancelled"; and for a late departure shown as a
 * clock time, the planned time struck through before the new one in the late colour.
 */
@Composable
private fun DepartureTime(
    departure: Departure,
    delayed: Boolean,
    isWide: Boolean,
) {
    val countdown = PulseTheme.type.countdown.let { if (isWide) it.copy(fontSize = WIDE_TIME_SIZE) else it }
    if (departure.isCancelled) {
        Text(
            stringResource(Res.string.cancelled),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = if (isWide) 17.sp else MaterialTheme.typography.labelLarge.fontSize),
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
            softWrap = false,
        )
        return
    }
    val time = formatDepartureTime(departure.expectedTime)
    val isNow = time.text == stringResource(Res.string.now)
    val isClock = departureCountdown(departure.expectedTime, Clock.System.now().toEpochMilliseconds()) is DepartureCountdown.Clock
    val planned = if (delayed && isClock) formatClock(departure.aimedTime) else null
    Text(
        text =
            buildAnnotatedString {
                if (planned != null) {
                    withStyle(
                        MaterialTheme.typography.bodyMedium.toSpanStyle().copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough,
                        ),
                    ) { append(planned) }
                    append(" ")
                    withStyle(SpanStyle(color = PulseTheme.colors.late)) { append(time) }
                } else {
                    append(time)
                }
            },
        style = countdown,
        color = if (isNow) PulseTheme.colors.now else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.End,
        // Never broken over lines: in a very narrow panel "Now" wrapped to "No" over "w".
        maxLines = 1,
        softWrap = false,
    )
}

/** [isoTime]'s "HH:mm" in Norway time, or empty when it cannot be read. */
private fun formatClock(isoTime: String): String =
    departureClock(isoTime, Clock.System.now().toEpochMilliseconds())
        ?.let { clockTimeText(it.epochMillis) }
        .orEmpty()

/**
 * A departure's time column: "Now", "5 min" up to [DepartureCountdown.RELATIVE_TIME_CUTOFF_SECONDS],
 * then its clock time. Beyond today (only when the user widened the search) the clock gets a small
 * raised "+1", as printed timetables do.
 */
@Composable
private fun formatDepartureTime(expectedTime: String): AnnotatedString =
    when (val countdown = departureCountdown(expectedTime, Clock.System.now().toEpochMilliseconds())) {
        DepartureCountdown.Now -> AnnotatedString(stringResource(Res.string.now))
        is DepartureCountdown.Minutes -> AnnotatedString(stringResource(Res.string.countdown_minutes, countdown.minutes))
        is DepartureCountdown.Clock ->
            buildAnnotatedString {
                append(clockTimeText(countdown.epochMillis))
                if (countdown.dayOffset > 0) {
                    withStyle(SpanStyle(fontSize = DAY_OFFSET_FONT_SCALE.em, baselineShift = BaselineShift.Superscript)) {
                        append("+${countdown.dayOffset}")
                    }
                }
            }
        DepartureCountdown.Unknown -> AnnotatedString("?")
    }
