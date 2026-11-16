package com.example.pulsetransit.ui.screens.routecards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.TripLeg
import com.example.pulsetransit.data.tripArrival
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.theme.PulseTheme
import com.example.pulsetransit.theme.type
import com.example.pulsetransit.ui.components.LinePill
import com.example.pulsetransit.ui.components.leavesInText
import com.example.pulsetransit.ui.components.lineColor
import com.example.pulsetransit.ui.layout.CaptionChoice
import com.example.pulsetransit.ui.layout.RouteCardFitting
import com.example.pulsetransit.ui.layout.RouteCardSize
import com.example.pulsetransit.ui.layout.TimelineStyle
import com.example.pulsetransit.ui.resources.Res
import com.example.pulsetransit.ui.resources.fastest_route_info
import com.example.pulsetransit.ui.resources.leg_walk
import com.example.pulsetransit.ui.resources.platform_label
import com.example.pulsetransit.ui.resources.route_arrival
import com.example.pulsetransit.ui.screens.isoClock
import org.jetbrains.compose.resources.stringResource

/**
 * The type and sizes of one [RouteCardSize]: the card's text steps up with its width, and so do
 * its line pills ([pillScale]), so a wall card is read across a room while a phone card stays tight.
 */
@Immutable
internal data class RouteCardMetrics(
    val padH: Dp,
    val padV: Dp,
    val gap: Dp,
    /** The leave clock, the largest thing on the card. */
    val clock: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
    /** The arrival clock and the next departures' clocks. */
    val secondClock: TextStyle,
    val pillScale: Float,
    val icon: Dp,
    /** How thick a ride's bar on the timeline is. */
    val track: Dp,
    /** At most this many dp of timeline per minute of the route (see [RouteCardFitting.segmentWidths]). */
    val perMinute: Float,
    /** The footer bar's height: a touch target and more. */
    val footer: Dp,
    /** The leave clock on a one-line card ([com.example.pulsetransit.ui.layout.RouteCardDensity.LINE]). */
    val lineClock: TextStyle,
)

/**
 * The metrics of each size class. Type sizes in sp, line heights after them: COMPACT clock 34/38,
 * title titleMedium, body bodyMedium; REGULAR clock 44/48, title titleLarge, body 15/22; WIDE clock
 * 48/52, title 22/28, body 17/24, caption 13/18, second clock 24/28; WALL clock 56/60, title 24/30,
 * body 18/26, caption 14/18, second clock 26/30. The leave clock stays smaller than the header's
 * clock on a wide board (64sp): the board's own clock is the largest thing on it.
 */
@Composable
internal fun routeCardMetrics(size: RouteCardSize): RouteCardMetrics {
    val typography = MaterialTheme.typography
    val bold = PulseTheme.type.countdown

    fun TextStyle.sized(
        size: Int,
        line: Int,
    ) = copy(fontSize = size.sp, lineHeight = line.sp)
    return when (size) {
        RouteCardSize.COMPACT ->
            RouteCardMetrics(
                padH = FjordDimens.space4,
                padV = FjordDimens.space3,
                gap = FjordDimens.space3,
                clock = bold.sized(34, 38),
                title = typography.titleMedium,
                body = typography.bodyMedium,
                caption = typography.labelSmall,
                secondClock = typography.labelLarge,
                pillScale = 1f,
                icon = 16.dp,
                track = 4.dp,
                perMinute = Float.POSITIVE_INFINITY,
                footer = 52.dp,
                lineClock = bold.sized(24, 28),
            )
        RouteCardSize.REGULAR ->
            RouteCardMetrics(
                padH = FjordDimens.space5,
                padV = FjordDimens.space4,
                gap = FjordDimens.space5,
                clock = bold.sized(44, 48),
                title = typography.titleLarge,
                body = typography.bodyMedium.sized(15, 22),
                caption = typography.labelSmall,
                secondClock = bold,
                pillScale = 1.1f,
                icon = 18.dp,
                track = 6.dp,
                perMinute = Float.POSITIVE_INFINITY,
                footer = 56.dp,
                lineClock = bold.sized(28, 32),
            )
        RouteCardSize.WIDE ->
            RouteCardMetrics(
                padH = FjordDimens.space6,
                padV = FjordDimens.space4,
                gap = FjordDimens.space6,
                clock = bold.sized(48, 52),
                title = typography.titleLarge.sized(22, 28),
                body = typography.bodyMedium.sized(17, 24),
                caption = typography.labelSmall.sized(13, 18),
                secondClock = bold.sized(24, 28),
                pillScale = 1.25f,
                icon = 20.dp,
                track = 6.dp,
                perMinute = 34f,
                footer = 56.dp,
                lineClock = bold.sized(30, 34),
            )
        RouteCardSize.WALL ->
            RouteCardMetrics(
                padH = FjordDimens.space6,
                padV = FjordDimens.space4,
                gap = FjordDimens.space6,
                clock = bold.sized(56, 60),
                title = typography.titleLarge.sized(24, 30),
                body = typography.bodyMedium.sized(18, 26),
                caption = typography.labelSmall.sized(14, 18),
                secondClock = bold.sized(26, 30),
                pillScale = 1.35f,
                icon = 22.dp,
                track = 8.dp,
                perMinute = 38f,
                footer = 60.dp,
                lineClock = bold.sized(34, 38),
            )
    }
}

/** The legs a route shows: rides, and walks that take any time (a zero-minute walk is a quay change). */
internal val TripInfo.shownLegs: List<TripLeg> get() = legs.filter { it.lineName != null || it.durationMinutes > 0 }

/** "19:00", or "19:00+1" on a later day; null when Entur gave no arrival. */
internal fun TripInfo.arrivalClock(): String? = tripArrival(this)?.let { if (it.dayOffset > 0) "${it.time}+${it.dayOffset}" else it.time }

/** A small line pill scaled by [scale] as a whole (its box and its text), so it keeps its proportions. */
@Composable
internal fun ScaledPill(
    line: String,
    mode: String?,
    scale: Float,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
        LinePill(line = line, mode = mode, small = true)
    }
}

@Composable
private fun WalkIcon(
    color: Color,
    size: Dp,
) = Icon(PulseIcons.DirectionsWalk, contentDescription = null, tint = color, modifier = Modifier.size(size))

/**
 * A route's legs as a chain that wraps between legs onto as many lines as it needs, never cut
 * short: "[walk] 4 min → [31] Jernbanetorget → [walk] 3 min → [5] → …". Only the first ride names
 * its boarding stop ([showsFirstFrom]): where to go is what matters at a glance; the trip's details
 * have the rest. The walks drop their minutes when that saves a line
 * ([RouteCardFitting.chainShowsWalkMinutes]). Decorative for screen readers: the card's description
 * reads the route.
 */
@Composable
internal fun LegChain(
    trip: TripInfo,
    metrics: RouteCardMetrics,
    color: Color,
    modifier: Modifier = Modifier,
    showsFirstFrom: Boolean = true,
) {
    val legs = trip.shownLegs
    if (legs.isEmpty()) {
        Text(stringResource(Res.string.fastest_route_info, trip.lineName, trip.originName), style = metrics.body, color = color, modifier = modifier)
        return
    }
    SubcomposeLayout(modifier = modifier.clearAndSetSemantics {}) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val full = subcompose(false) { ChainFlow(trip, metrics, color, showsFirstFrom, bare = false) }.single().measure(loose)
        val hasWalks = legs.any { it.lineName == null }
        val bare = if (hasWalks) subcompose(true) { ChainFlow(trip, metrics, color, showsFirstFrom, bare = true) }.single().measure(loose) else null
        val shown =
            if (bare == null || RouteCardFitting.chainShowsWalkMinutes(full.height.toFloat(), bare.height.toFloat())) full else bare
        layout(shown.width.coerceAtLeast(constraints.minWidth), shown.height.coerceAtLeast(constraints.minHeight)) { shown.place(0, 0) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChainFlow(
    trip: TripInfo,
    metrics: RouteCardMetrics,
    color: Color,
    showsFirstFrom: Boolean,
    bare: Boolean,
) {
    val legs = trip.shownLegs
    val firstRide = legs.indexOfFirst { it.lineName != null }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(FjordDimens.space1),
        verticalArrangement = Arrangement.spacedBy(FjordDimens.space1),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        legs.forEachIndexed { index, leg ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FjordDimens.space1)) {
                if (index > 0) Icon(PulseIcons.ArrowForward, contentDescription = null, tint = color.copy(alpha = 0.7f), modifier = Modifier.size(metrics.icon * 0.85f))
                val line = leg.lineName
                if (line == null) {
                    WalkIcon(color, metrics.icon)
                    if (!bare) Text(stringResource(Res.string.leg_walk, leg.durationMinutes), style = metrics.body, color = color, maxLines = 1)
                } else {
                    ScaledPill(line, leg.mode, metrics.pillScale)
                    val from = leg.fromName
                    if (showsFirstFrom && index == firstRide && from != null) {
                        Text(from, style = metrics.body, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/**
 * A route drawn as a timeline across the width it is given: each ride a bar in its line's colour
 * starting at its pill, each walk a dotted stretch after a walker, every segment as long as its
 * share of the minutes (at most [RouteCardMetrics.perMinute] dp a minute, at least what its pill or
 * label needs). Under each ride its boarding stop (the first one's platform too) when
 * [showsCaptions], whole or not at all. How it degrades when short of width, walks without their
 * minutes and then a wrapping [LegChain], is [RouteCardFitting.timeline]. The timeline is as wide as
 * its segments: a short route on a wide card leaves the rest to whatever follows it.
 */
@Composable
internal fun RouteTimeline(
    trip: TripInfo,
    metrics: RouteCardMetrics,
    color: Color,
    modifier: Modifier = Modifier,
    showsCaptions: Boolean = true,
) {
    val legs = trip.shownLegs
    if (legs.isEmpty()) {
        LegChain(trip, metrics, color, modifier)
        return
    }
    val firstRide = legs.indexOfFirst { it.lineName != null }
    val platformText = legs.getOrNull(firstRide)?.platform?.let { stringResource(Res.string.platform_label, it) }
    SubcomposeLayout(modifier = modifier.clearAndSetSemantics {}) { constraints ->
        val gap = FjordDimens.space1.roundToPx()
        val captionGap = 2.dp.roundToPx()
        val tops = subcompose("tops") { legs.forEach { leg -> SegmentTop(leg, metrics, color, bare = false) } }
        val bareTops = subcompose("bareTops") { legs.forEach { leg -> SegmentTop(leg, metrics, color, bare = true) } }

        // Each ride's stop name with the first ride's platform, and without it.
        fun captionsOf(
            slot: String,
            withPlatform: Boolean,
        ) = subcompose(slot) {
            legs.forEachIndexed { index, leg ->
                val parts = listOfNotNull(leg.fromName, platformText.takeIf { withPlatform && index == firstRide })
                Text(
                    if (leg.lineName == null) "" else parts.joinToString(" · "),
                    style = metrics.caption,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        val captions = if (showsCaptions) captionsOf("captions", withPlatform = true) else emptyList()
        val shortCaptions = if (showsCaptions) captionsOf("shortCaptions", withPlatform = false) else emptyList()
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity

        fun List<Measurable>.widths() = map { it.maxIntrinsicWidth(Constraints.Infinity).toFloat() }
        val fit =
            RouteCardFitting.timeline(
                fullMinWidths = tops.widths(),
                bareMinWidths = bareTops.widths(),
                weights = legs.map { it.durationMinutes.toFloat() },
                isRide = legs.map { it.lineName != null },
                fullCaptionWidths = if (showsCaptions) captions.widths() else List(legs.size) { 0f },
                shortCaptionWidths = if (showsCaptions) shortCaptions.widths() else List(legs.size) { 0f },
                available = maxWidth.toFloat(),
                gap = gap.toFloat(),
                maxPerWeight = metrics.perMinute * density,
                withCaptions = showsCaptions,
                captionMinWidth = RouteCardFitting.CAPTION_MIN_WIDTH_DP.dp.toPx(),
            )
        if (fit.style == TimelineStyle.CHAIN) {
            val chain =
                subcompose("chain") { LegChain(trip, metrics, color) }
                    .map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
                    .single()
            return@SubcomposeLayout layout(chain.width.coerceAtLeast(constraints.minWidth), chain.height.coerceAtLeast(constraints.minHeight)) {
                chain.place(0, 0)
            }
        }
        val pixelWidths = fit.widths.map { it.toInt() }
        val shownTops = if (fit.style == TimelineStyle.FULL) tops else bareTops
        val topPlaceables = shownTops.mapIndexed { index, measurable -> measurable.measure(Constraints.fixedWidth(pixelWidths[index])) }
        val loose = Constraints(maxWidth = maxWidth)
        val captionPlaceables =
            fit.captions.mapIndexed { index, choice ->
                when (choice) {
                    CaptionChoice.FULL -> captions[index].measure(loose)
                    CaptionChoice.SHORT -> shortCaptions[index].measure(loose)
                    CaptionChoice.NONE -> null
                }
            }
        val topHeight = topPlaceables.maxOf { it.height }
        val captionHeight = captionPlaceables.maxOfOrNull { it?.height ?: 0 } ?: 0
        val width = pixelWidths.sum() + gap * (legs.size - 1)
        val height = topHeight + if (captionHeight > 0) captionGap + captionHeight else 0
        layout(width.coerceAtMost(maxWidth), height) {
            var x = 0
            legs.indices.forEach { index ->
                val top = topPlaceables[index]
                top.place(x, (topHeight - top.height) / 2)
                captionPlaceables[index]?.place(x, topHeight + captionGap)
                x += pixelWidths[index] + gap
            }
        }
    }
}

/**
 * A timeline segment's top: a ride's pill on its coloured bar, or a walker on a dotted line, with
 * its minutes unless [bare].
 */
@Composable
private fun SegmentTop(
    leg: TripLeg,
    metrics: RouteCardMetrics,
    color: Color,
    bare: Boolean,
) {
    val line = leg.lineName
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (line == null) {
            WalkIcon(color, metrics.icon)
            if (!bare) {
                Text(
                    stringResource(Res.string.leg_walk, leg.durationMinutes),
                    style = metrics.caption,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 2.dp, end = FjordDimens.space1),
                )
            }
            val dot = color.copy(alpha = WALK_DOT_ALPHA)
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .widthIn(min = MIN_BAR)
                        .height(metrics.track)
                        .drawBehind {
                            val radius = size.height / 3
                            val step = radius * 4
                            var x = radius + step / 2
                            while (x < size.width - radius) {
                                drawCircle(dot, radius, Offset(x, size.height / 2))
                                x += step
                            }
                        },
            )
        } else {
            val barColor = lineColor(leg.mode)
            ScaledPill(line, leg.mode, metrics.pillScale)
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .widthIn(min = MIN_BAR)
                        .height(metrics.track)
                        .background(barColor, RoundedCornerShape(topEnd = metrics.track, bottomEnd = metrics.track)),
            )
        }
    }
}

/**
 * One next departure, in one of three amounts of detail ([NextDetail]): its clock always, then
 * its rides' pills, then how soon it leaves and when it arrives. The caller picks the richest one
 * that fits. Decorative for screen readers: the row or bar around it reads it.
 */
@Composable
internal fun NextEntry(
    trip: TripInfo,
    detail: NextDetail,
    metrics: RouteCardMetrics,
    nowMillis: Long,
    color: Color,
) {
    val rides = trip.shownLegs.filter { it.lineName != null }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FjordDimens.space2)) {
        Text(isoClock(trip.expectedStartTime), style = metrics.secondClock, color = color, maxLines = 1)
        if (detail >= NextDetail.FULL) {
            leavesInText(trip.expectedStartTime, nowMillis)?.let {
                Text(it, style = metrics.caption, color = color, maxLines = 1)
            }
        }
        val shownRides = if (detail >= NextDetail.RIDES) rides else rides.take(1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            shownRides.forEachIndexed { index, leg ->
                if (index > 0) Icon(PulseIcons.ChevronRight, contentDescription = null, tint = color.copy(alpha = CAPTION_ALPHA), modifier = Modifier.size(metrics.icon * 0.8f))
                ScaledPill(leg.lineName.orEmpty(), leg.mode, metrics.pillScale)
            }
        }
        if (detail >= NextDetail.FULL) {
            trip.arrivalClock()?.let {
                Text(stringResource(Res.string.route_arrival, it), style = metrics.caption, color = color, maxLines = 1)
            }
        }
    }
}

/** How much a next departure shows, least first. */
internal enum class NextDetail {
    /** "18:12 [37]": the clock and the first ride's pill. */
    FIRST_RIDE,

    /** "18:12 [37] › [4] › [11]": every ride's pill. */
    RIDES,

    /** "18:12 in 17 min [37] › [4] › [11] arr. 19:08". */
    FULL,
}

/**
 * Shows the first of [variants] (richest first) whose natural width fits, else the last one; for a
 * row whose content can say more when there is room.
 */
@Composable
internal fun RichestFitting(
    variants: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    Layout(contents = variants, modifier = modifier) { measurables, constraints ->
        val loose = Constraints(maxHeight = constraints.maxHeight)
        val placeables = measurables.map { group -> group.map { it.measure(loose) } }
        val chosen =
            placeables.firstOrNull { group -> (group.maxOfOrNull { it.width } ?: 0) <= constraints.maxWidth }
                ?: placeables.last()
        val width = (chosen.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (chosen.maxOfOrNull { it.height } ?: 0).coerceAtLeast(constraints.minHeight)
        layout(width, height) { chosen.forEach { it.place(0, (height - it.height) / 2) } }
    }
}

internal const val CAPTION_ALPHA = 0.78f
private const val WALK_DOT_ALPHA = 0.7f
private val MIN_BAR = 12.dp
