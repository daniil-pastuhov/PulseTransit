package com.example.pulsetransit.ui.layout

import kotlin.math.ceil
import kotlin.math.floor

/**
 * A board route card's size class, picked from the card's own width by [RouteCardLayout.size]
 * (never the device's). Each class has its own type sizes and structure; the views keep a table of
 * metrics per class (padding, clock and title sizes, pill scale, footer height).
 *
 * - [COMPACT]: the leave clock with the title and "in 6 min · 15 min · arr. 19:32" beside it, over
 *   the route as a wrapping chain of legs ("walk 2 min → [R12] Oslo S"). Its next departures are
 *   on the footer bar.
 * - [REGULAR]: three blocks in a row, "Leave 19:17 in 6 min", the title over the route as a
 *   timeline ([RouteCardFitting.timeline]), and "Arrive 19:32 15 min"; next departures on the footer.
 * - [WIDE] and [WALL]: as [REGULAR] in larger type, with the next departures as a block on the
 *   right of the card ([nextInHead]), so these cards have no footer and show every alternative they
 *   have room for. [WALL] is set to be read across a room.
 */
enum class RouteCardSize {
    COMPACT,
    REGULAR,
    WIDE,
    WALL,
    ;

    /** Whether the next departures sit in the card, beside the journey, rather than on a footer bar. */
    val nextInHead: Boolean get() = this >= WIDE

    /** Whether the route is drawn as a timeline (else as a wrapping chain of legs). */
    val hasTimeline: Boolean get() = this >= REGULAR
}

/**
 * How much every route card on the board shows, the same for all of them, richest first. The board
 * uses the first one whose collapsed cards fit above the stop panels ([RouteCardLayout.fits]), so
 * the cards give up detail before the panels drop below [BoardLayout.MIN_PANEL_HEIGHT_DP] and
 * before anything has to scroll.
 */
enum class RouteCardDensity {
    /** Everything: the whole card, and the footer band ([RouteCardFooter]) at its bottom. */
    FULL,

    /**
     * As [FULL], but the toggle and note bands are left out to save their height: a card with
     * alternatives gets a rail on its trailing edge instead, as tall as the card and
     * [RouteCardBands.RAIL_WIDTH_DP] wide, "+3" over a chevron, which opens the route search on
     * that destination (every route, as "+N in details" does). Expanded cards keep the setting but
     * list no rows. The searched-for destination keeps its band of buttons
     * ([RouteCardBands.showsBand]).
     */
    RAIL,

    /**
     * As [RAIL], with the route line left out: a compact card is the clock beside the title and
     * "in 6 min · 15 min · arr." only; a timeline card keeps its timeline without the stop names
     * under it.
     */
    SHORT,

    /**
     * One line per card: the clock, the title and how soon it leaves, ellipsised at the end, and
     * the rail; the searched-for destination's buttons at the end of its line. Only when this does
     * not fit either does the cards' area scroll, the last resort.
     */
    LINE,
}

/**
 * What a route card has along its bottom at [RouteCardDensity.FULL], as a band the width of the
 * card, at least a touch target high, that lines up with its neighbours' bands.
 */
enum class RouteCardFooter {
    /** Nothing. */
    NONE,

    /**
     * A plain band, "No later routes": given to a loaded saved destination without alternatives
     * when it shares its grid row with a card that has a band, so the row's bands line up.
     */
    NOTE,

    /**
     * The bar that opens and closes the alternatives (a setting, by destination): collapsed it
     * names the next departures ("Then 19:27 [R14] 19:37 [R13]", as many as fit) and "3 more",
     * expanded "Fewer". Saved destinations with alternatives, on cards without [RouteCardSize.nextInHead].
     */
    TOGGLE,

    /** The searched-for destination's buttons: its time, save, close. */
    ACTIONS,
}

/** How a timeline is drawn, richest first; see [RouteCardFitting.timeline]. */
enum class TimelineStyle {
    /** Every walk with its walker, its minutes and a dotted stretch. */
    FULL,

    /** Walks as a walker and a dotted stretch, without their minutes, so the rides get the width. */
    BARE_WALKS,

    /** No timeline: the legs wrap as a chain, which names the first ride's boarding stop. */
    CHAIN,
}

/** Which of a ride's captions a timeline shows under it; see [RouteCardFitting.timeline]. */
enum class CaptionChoice {
    /** "Oslo S · Platform 11": the boarding stop, and the platform on the first ride. */
    FULL,

    /** "Oslo S": the boarding stop alone. */
    SHORT,

    /** Nothing: not even the stop's name fits, and a name is never cut short. */
    NONE,
}

/**
 * A timeline's layout: its [style], and for a timeline (not [TimelineStyle.CHAIN]) each segment's
 * width ([widths], in the unit given) and each segment's caption ([captions], [CaptionChoice.NONE]
 * for walks).
 */
class TimelineFit(
    val style: TimelineStyle,
    val widths: List<Float>,
    val captions: List<CaptionChoice>,
)

/**
 * How a card fills its grid row ([RouteCardLayout.stretch]): its journey's band height, how much
 * each alternative row grows, and its band's height, in the unit given.
 */
data class CardStretch(
    val headBand: Float,
    val rowExtra: Float,
    val footerBand: Float,
)

/**
 * How the board's next departures are fitted into a width: [count] entries, each in the amount of
 * detail with index [detail] (0 the richest). See [RouteCardFitting.nextEntries].
 */
data class NextEntriesFit(
    val detail: Int,
    val count: Int,
)

/**
 * The grid of the board's route cards (board mode; on the spot lists its own), free of UI code so
 * both apps lay the cards out alike. Sizes are in dp (points on iOS) unless a function says it
 * takes any one unit; "font scale" is the text scale (Dynamic Type's on iOS), counted from 1 up.
 * [RouteCardBands] decides what each card has along its bottom and how much it shows, and
 * [RouteCardFitting] how its content fits its width.
 *
 * The cards sit above the stop panels, as a grid:
 * 1. [columns] cards to a grid row, every card in a row as wide ([cardWidths]); a short last row's
 *    cards share its width.
 * 2. Each card's [RouteCardSize] from its own width ([size]); a compact card under
 *    [NARROW_CARD_MAX_WIDTH_DP] ([isNarrow]) gives its title a line of its own over the clock and
 *    its chain leaves out the boarding stop.
 * 3. Each card's band ([RouteCardBands.footers]).
 * 4. One [RouteCardDensity] for all cards, the first of [RouteCardBands.densities] whose collapsed
 *    cards, as measured, fit the height left once the stop panels have
 *    [BoardLayout.MIN_PANEL_HEIGHT_DP] ([fits]). Should not even [RouteCardDensity.LINE] fit, the
 *    cards' area scrolls.
 * 5. At [RouteCardDensity.FULL] only, expanded cards (a [RouteCardFooter.TOGGLE] whose
 *    destination is in the setting) list their alternatives below the journey, as many as
 *    [com.example.pulsetransit.data.BoardRouteAlternatives.visibleRows] lets fit in what is left
 *    (row height: the tallest alternative row measured), else their toggle names them as when
 *    collapsed. At other densities the setting is kept but no rows show.
 * 6. Every card in a grid row is as tall as the tallest ([rowHeights]), except that a card
 *    showing no alternative rows beside one that does is only as tall as the grid row would be
 *    without rows ([cardHeights]), at its top; how a shorter card fills the difference is [stretch].
 */
object RouteCardLayout {
    /** Narrowest a route card gets (times the font scale) before the grid uses fewer columns. */
    const val CARD_MIN_WIDTH_DP = 280f

    /** Below this effective width ([effectiveWidth]) a compact card is narrow ([isNarrow]). */
    const val NARROW_CARD_MAX_WIDTH_DP = 360f

    /** From this effective width a card is [RouteCardSize.REGULAR]: its route a timeline. */
    const val REGULAR_MIN_WIDTH_DP = 540f

    /** From this effective width a card is [RouteCardSize.WIDE]: its next departures beside the journey. */
    const val WIDE_MIN_WIDTH_DP = 860f

    /** From this effective width a card is [RouteCardSize.WALL]. */
    const val WALL_MIN_WIDTH_DP = 1400f

    /** A card's width as the size classes count it: divided by the font scale from 1 up. */
    fun effectiveWidth(
        widthDp: Float,
        fontScale: Float,
    ): Float = widthDp / fontScale.coerceAtLeast(1f)

    /** The size class of a card [widthDp] wide at [fontScale]. */
    fun size(
        widthDp: Float,
        fontScale: Float,
    ): RouteCardSize {
        val effective = effectiveWidth(widthDp, fontScale)
        return when {
            effective >= WALL_MIN_WIDTH_DP -> RouteCardSize.WALL
            effective >= WIDE_MIN_WIDTH_DP -> RouteCardSize.WIDE
            effective >= REGULAR_MIN_WIDTH_DP -> RouteCardSize.REGULAR
            else -> RouteCardSize.COMPACT
        }
    }

    /** Whether a compact card this wide stacks its title over the clock ([NARROW_CARD_MAX_WIDTH_DP]). */
    fun isNarrow(
        widthDp: Float,
        fontScale: Float,
    ): Boolean = effectiveWidth(widthDp, fontScale) < NARROW_CARD_MAX_WIDTH_DP

    /**
     * How many of [count] cards share a grid row [widthDp] wide with [gapDp] between them: as many
     * as fit [CARD_MIN_WIDTH_DP] times the font scale, then the fewest that need no more grid rows,
     * so the rows come out even (3 cards that fit 2 across: 2 and 1, the last spanning the row;
     * 5 that fit 3 across: 3 and 2).
     */
    fun columns(
        count: Int,
        widthDp: Float,
        gapDp: Float,
        fontScale: Float,
    ): Int {
        if (count <= 1) return 1
        val min = CARD_MIN_WIDTH_DP * fontScale.coerceAtLeast(1f)
        val fitting = floor((widthDp + gapDp) / (min + gapDp)).toInt().coerceIn(1, count)
        val rows = ceil(count / fitting.toFloat()).toInt()
        return ceil(count / rows.toFloat()).toInt()
    }

    /** Each of [count] cards' width, in board order, [columns] to a grid row [widthDp] wide. */
    fun cardWidths(
        count: Int,
        columns: Int,
        widthDp: Float,
        gapDp: Float,
    ): List<Float> =
        (0 until count).chunked(columns.coerceAtLeast(1)).flatMap { row ->
            val width = ((widthDp - gapDp * (row.size - 1)) / row.size).coerceAtLeast(0f)
            row.map { width }
        }

    /** Each grid row's height: its tallest card of [cardHeights] (any one unit), [columns] to a row. */
    fun rowHeights(
        cardHeights: List<Float>,
        columns: Int,
    ): List<Float> = cardHeights.chunked(columns.coerceAtLeast(1)).map { it.max() }

    /**
     * Each card's height (any one unit), [columns] to a grid row: a card showing alternative rows
     * ([hasRows]) is as tall as its grid row ([rowHeights] of [natural], the cards as shown); one
     * without is as tall as the grid row would be with no card showing rows ([rowHeights] of
     * [collapsed], the cards measured collapsed), and sits at the row's top. So a collapsed card
     * beside an expanded one keeps its own height instead of growing a tall, empty band, while a
     * row of collapsed cards, or of expanded ones, stays as tall as its tallest, as before.
     */
    fun cardHeights(
        natural: List<Float>,
        collapsed: List<Float>,
        hasRows: List<Boolean>,
        columns: Int,
    ): List<Float> {
        val full = rowHeights(natural, columns)
        val bare = rowHeights(collapsed, columns)
        val perRow = columns.coerceAtLeast(1)
        return natural.indices.map { index ->
            val row = index / perRow
            if (hasRows[index]) full[row] else minOf(bare[row], full[row])
        }
    }

    /** The grid's height: its [rowHeights] and a [gap] between grid rows. */
    fun gridHeight(
        cardHeights: List<Float>,
        columns: Int,
        gap: Float,
    ): Float {
        val rows = rowHeights(cardHeights, columns)
        return rows.sum() + gap * (rows.size - 1).coerceAtLeast(0)
    }

    /**
     * Whether cards of these heights (one density's, collapsed, as measured; any one unit) fit in
     * [maxHeight]: the height the body has, less the gap above the panels and their
     * [BoardLayout.MIN_PANEL_HEIGHT_DP].
     */
    fun fits(
        cardHeights: List<Float>,
        columns: Int,
        gap: Float,
        maxHeight: Float,
    ): Boolean = gridHeight(cardHeights, columns, gap) <= maxHeight

    /**
     * How a card fills its grid row [rowHeight] tall (any one unit), from its own parts as
     * measured: its journey [head], its alternative rows ([rows] of them, [rowsHeight] together)
     * and its band [footer] (0 without). So that a row reads as one:
     * - the journey takes the height of the tallest journey in the grid row ([rowHeadMax]),
     *   centred in it, so the clocks side by side line up;
     * - what is left goes to the rows, shared equally, each centred in its share;
     * - with no rows, to the band, which grows upwards while its content stays at its bottom,
     *   level with the neighbours' bands: a collapsed card beside a taller collapsed one gets a
     *   taller toggle (all of it the target), a card without alternatives a taller "No later
     *   routes" band (beside an expanded card neither is as tall as the row: [cardHeights]);
     * - with neither, to the journey.
     * The band always ends at the card's bottom, level with its neighbours'.
     */
    @Suppress("LongParameterList") // Each is one measured part of the card; a holder would only rename them.
    fun stretch(
        head: Float,
        rowHeadMax: Float,
        rows: Int,
        rowsHeight: Float,
        footer: Float,
        rowHeight: Float,
    ): CardStretch {
        val headBand = maxOf(head, rowHeadMax)
        val left = (rowHeight - headBand - rowsHeight - footer).coerceAtLeast(0f)
        return when {
            rows > 0 -> CardStretch(headBand = headBand, rowExtra = left / rows, footerBand = footer)
            footer > 0f -> CardStretch(headBand = headBand, rowExtra = 0f, footerBand = footer + left)
            else -> CardStretch(headBand = headBand + left, rowExtra = 0f, footerBand = 0f)
        }
    }
}

/**
 * What a board route card has along its bottom ([RouteCardFooter]) and, from the
 * [RouteCardDensity] the board picked, whether it shows it or a rail instead.
 */
object RouteCardBands {
    /** Width of the rail ([RouteCardDensity.RAIL]): more than a touch target, at its full height. */
    const val RAIL_WIDTH_DP = 48f

    /**
     * The band a card would have on its own, before [footers] lines up its grid row: the
     * searched-for destination's buttons ([isSearched]) whatever it shows; nothing for a card
     * without a route ([hasRoute]: loading, failed, none found) or whose next departures sit beside
     * its journey ([RouteCardSize.nextInHead]); the toggle for one with alternatives still to leave
     * ([hasAlternatives]); else [RouteCardFooter.NOTE].
     */
    fun own(
        isSearched: Boolean,
        hasRoute: Boolean,
        size: RouteCardSize,
        hasAlternatives: Boolean,
    ): RouteCardFooter =
        when {
            isSearched -> RouteCardFooter.ACTIONS
            !hasRoute || size.nextInHead -> RouteCardFooter.NONE
            hasAlternatives -> RouteCardFooter.TOGGLE
            else -> RouteCardFooter.NOTE
        }

    /**
     * Each card's band at [RouteCardDensity.FULL], in board order, from the band each would have on
     * its own ([own]): [RouteCardFooter.NOTE] stays only where another card in the same grid row of
     * [columns] has a [RouteCardFooter.TOGGLE] or [RouteCardFooter.ACTIONS] band, else becomes
     * [RouteCardFooter.NONE]. Pass NOTE for a loaded saved destination without alternatives, NONE
     * for a card without a route (loading, failed, none found) and for wide cards without one.
     */
    fun footers(
        own: List<RouteCardFooter>,
        columns: Int,
    ): List<RouteCardFooter> =
        own.chunked(columns.coerceAtLeast(1)).flatMap { row ->
            val banded = row.any { it == RouteCardFooter.TOGGLE || it == RouteCardFooter.ACTIONS }
            row.map { if (it == RouteCardFooter.NOTE && !banded) RouteCardFooter.NONE else it }
        }

    /**
     * Whether a card shows its band [footer] at [density]: [RouteCardFooter.TOGGLE] and
     * [RouteCardFooter.NOTE] only at [RouteCardDensity.FULL]; the searched-for destination's
     * [RouteCardFooter.ACTIONS] at every density but [RouteCardDensity.LINE], where its buttons
     * move to the trailing end of its one line (beside the card's title they would squeeze it).
     */
    fun showsBand(
        footer: RouteCardFooter,
        density: RouteCardDensity,
    ): Boolean =
        when (footer) {
            RouteCardFooter.NONE -> false
            RouteCardFooter.NOTE, RouteCardFooter.TOGGLE -> density == RouteCardDensity.FULL
            RouteCardFooter.ACTIONS -> density != RouteCardDensity.LINE
        }

    /**
     * The densities worth trying for these bands, richest first: [RouteCardDensity.RAIL] only
     * differs from [RouteCardDensity.FULL] when some card has a band it leaves out.
     */
    fun densities(footers: List<RouteCardFooter>): List<RouteCardDensity> =
        if (footers.any { it == RouteCardFooter.TOGGLE || it == RouteCardFooter.NOTE }) {
            RouteCardDensity.entries
        } else {
            RouteCardDensity.entries.filter { it != RouteCardDensity.RAIL }
        }

    /**
     * Whether a card shows the rail ([RouteCardDensity.RAIL]) at [density]: a card whose band at
     * [RouteCardDensity.FULL] is [footer] [RouteCardFooter.TOGGLE], at any density but FULL; and a
     * [RouteCardSize.nextInHead] card with alternatives ([hasAlternatives]) at
     * [RouteCardDensity.LINE], where its next departures block is left out.
     */
    fun hasRail(
        footer: RouteCardFooter,
        size: RouteCardSize,
        hasAlternatives: Boolean,
        density: RouteCardDensity,
    ): Boolean =
        when {
            footer == RouteCardFooter.TOGGLE -> density != RouteCardDensity.FULL
            size.nextInHead -> hasAlternatives && density == RouteCardDensity.LINE
            else -> false
        }
}

/**
 * How a route card's content fits its width: the timeline's segments and captions, the chain, the
 * meta line, the one-line card, and the next departures in a row or a block. The views measure
 * their pieces as drawn and pass the sizes in; these decide what to show.
 */
object RouteCardFitting {
    /** Narrowest a timeline caption may be shown in, at font scale 1; less is left out. */
    const val CAPTION_MIN_WIDTH_DP = 48f

    /**
     * Narrowest a wide card's next departures block gets beside its journey, before scaling with
     * the card's line pills: the journey's timeline gives way to it.
     */
    const val NEXT_BLOCK_MIN_WIDTH_DP = 220f

    /** Which of a compact card's meta parts [metaParts] drops first: the duration, then the arrival. */
    val META_DROP_ORDER: List<Int> = listOf(1, 2)

    /**
     * The widths of a timeline's segments: each at least its [minWidths] (what its pill or label
     * needs) and otherwise in proportion to its [weights] (minutes, at least 1), filling [available]
     * but at most [maxPerWeight] per unit of weight, so a short route is not stretched across a TV.
     * Null when the minimums alone do not fit. Any one unit.
     */
    fun segmentWidths(
        minWidths: List<Float>,
        weights: List<Float>,
        available: Float,
        maxPerWeight: Float,
    ): List<Float>? {
        require(minWidths.size == weights.size) { "One weight per segment" }
        if (minWidths.isEmpty()) return emptyList()
        if (minWidths.sum() > available) return null
        val safeWeights = weights.map { it.coerceAtLeast(MIN_WEIGHT) }

        fun total(k: Float) = minWidths.indices.sumOf { maxOf(minWidths[it], k * safeWeights[it]).toDouble() }.toFloat()
        val target = if (maxPerWeight.isFinite()) minOf(available, total(maxPerWeight)) else available
        // total(k) grows with k, so the k that fills the target is found by halving.
        var low = 0f
        var high = target / safeWeights.min()
        repeat(SEARCH_STEPS) {
            val mid = (low + high) / 2
            if (total(mid) > target) high = mid else low = mid
        }
        return minWidths.indices.map { maxOf(minWidths[it], low * safeWeights[it]) }
    }

    /** [segmentWidths] with no cap per unit of weight: the segments fill [available]. */
    fun segmentWidths(
        minWidths: List<Float>,
        weights: List<Float>,
        available: Float,
    ): List<Float>? = segmentWidths(minWidths, weights, available, Float.POSITIVE_INFINITY)

    /**
     * Each ride's caption on a timeline whose segments have [widths] with [gap] between them: a
     * ride's caption starts under its pill and may reach under the walks after it, up to a [gap]
     * before the next ride (or the timeline's end); in that span it shows [CaptionChoice.FULL]
     * when [fullWidths] fits, else [CaptionChoice.SHORT] when [shortWidths] fits, else nothing,
     * and nothing when the span is under [minWidth]. Walks ([isRide] false) never have one.
     */
    @Suppress("LongParameterList") // One list per measured piece, as the timeline has them.
    fun captions(
        widths: List<Float>,
        isRide: List<Boolean>,
        fullWidths: List<Float>,
        shortWidths: List<Float>,
        gap: Float,
        minWidth: Float,
    ): List<CaptionChoice> {
        val starts = widths.runningFold(0f) { x, width -> x + width + gap }
        val end = starts.last() - gap
        return widths.indices.map { index ->
            if (!isRide[index]) return@map CaptionChoice.NONE
            val nextRide = (index + 1 until widths.size).firstOrNull { isRide[it] }
            val spanEnd = if (nextRide == null) end else starts[nextRide] - gap
            val span = spanEnd - starts[index]
            when {
                span < minWidth -> CaptionChoice.NONE
                fullWidths[index] <= span -> CaptionChoice.FULL
                shortWidths[index] <= span -> CaptionChoice.SHORT
                else -> CaptionChoice.NONE
            }
        }
    }

    /**
     * How a route is drawn across [available] (any one unit, [gap] between segments): the first
     * [TimelineStyle] that works, [TimelineStyle.FULL] (segments at least [fullMinWidths]) then
     * [TimelineStyle.BARE_WALKS] ([bareMinWidths]: walks without their minutes), then the chain.
     * A timeline style works when its segments fit ([segmentWidths], at most [maxPerWeight] per
     * minute) and, when [withCaptions], the first ride's boarding stop gets a caption ([captions]):
     * where to get on matters most, and the chain always names it. [captionMinWidth] is
     * [CAPTION_MIN_WIDTH_DP] in the unit used. A route without rides always fits as a timeline.
     */
    @Suppress("LongParameterList") // One list per measured piece, as the timeline has them.
    fun timeline(
        fullMinWidths: List<Float>,
        bareMinWidths: List<Float>,
        weights: List<Float>,
        isRide: List<Boolean>,
        fullCaptionWidths: List<Float>,
        shortCaptionWidths: List<Float>,
        available: Float,
        gap: Float,
        maxPerWeight: Float,
        withCaptions: Boolean,
        captionMinWidth: Float,
    ): TimelineFit {
        val segmentsSpace = available - gap * (weights.size - 1).coerceAtLeast(0)
        val firstRide = isRide.indexOfFirst { it }
        for ((style, minWidths) in listOf(TimelineStyle.FULL to fullMinWidths, TimelineStyle.BARE_WALKS to bareMinWidths)) {
            val widths = segmentWidths(minWidths, weights, segmentsSpace, maxPerWeight) ?: continue
            val captions =
                if (withCaptions) {
                    captions(widths, isRide, fullCaptionWidths, shortCaptionWidths, gap, captionMinWidth)
                } else {
                    List(widths.size) { CaptionChoice.NONE }
                }
            if (!withCaptions || firstRide < 0 || captions[firstRide] != CaptionChoice.NONE) {
                return TimelineFit(style, widths, captions)
            }
        }
        return TimelineFit(TimelineStyle.CHAIN, emptyList(), emptyList())
    }

    /**
     * Whether a chain of legs shows each walk's minutes: unless leaving them out (a walker alone)
     * puts the chain on fewer lines, from its height measured both ways ([fullHeight],
     * [bareHeight], any one unit, or line counts).
     */
    fun chainShowsWalkMinutes(
        fullHeight: Float,
        bareHeight: Float,
    ): Boolean = fullHeight <= bareHeight

    /**
     * Whether a one-line card ([RouteCardDensity.LINE]) shows how soon it leaves after its title:
     * only when the clock, the whole title and that fit [available] with [gap] between them, so the
     * destination's name is never cut for it. Any one unit.
     */
    fun lineShowsSoon(
        clockWidth: Float,
        titleWidth: Float,
        soonWidth: Float,
        gap: Float,
        available: Float,
    ): Boolean = clockWidth + gap + titleWidth + gap + soonWidth <= available

    /**
     * Which of a compact card's meta parts ("in 6 min", "15 min", "arr. 19:45", [widths] in that
     * order, any one unit) it shows beside its clock, wrapping between parts onto at most
     * [maxLines] lines [available] wide with [gap] between parts on a line: all that fit, dropping
     * them in [dropOrder] (indices; the board drops the duration, then the arrival) until the rest
     * fit. A part never dropped (the first, how soon it leaves) is shown even if it alone overflows.
     * The kept indices, in order.
     */
    fun metaParts(
        widths: List<Float>,
        gap: Float,
        available: Float,
        maxLines: Int,
        dropOrder: List<Int>,
    ): List<Int> {
        var kept = widths.indices.toList()
        for (drop in listOf(-1) + dropOrder) {
            if (drop >= 0) kept = kept - drop
            if (flowRows(kept.map { widths[it] }, gap, available).size <= maxLines) return kept
        }
        return kept
    }

    /**
     * Parts of these [widths] (any one unit) flowed onto lines [available] wide with [gap] between
     * them, in order: each line's indices. A part wider than [available] has a line of its own.
     */
    fun flowRows(
        widths: List<Float>,
        gap: Float,
        available: Float,
    ): List<List<Int>> {
        val rows = mutableListOf<MutableList<Int>>()
        var x = 0f
        widths.forEachIndexed { index, width ->
            if (rows.isEmpty() || (x > 0f && x + gap + width > available)) {
                rows += mutableListOf(index)
                x = width
            } else {
                rows.last() += index
                x += if (x > 0f) gap + width else width
            }
        }
        return rows
    }

    /**
     * How a row of next departures fits [available] (any one unit, [gap] between entries), from
     * each entry's width in each amount of detail ([widthsByDetail], richest first, one list per
     * detail with one width per entry): every entry in the richest detail they all fit in, else as
     * many as fit, in order and whole, in the least detail (the clock and the first ride's pill).
     * A clock is never cut short, so the count may be 0.
     */
    fun nextEntries(
        widthsByDetail: List<List<Float>>,
        gap: Float,
        available: Float,
    ): NextEntriesFit {
        fun widthOf(widths: List<Float>) = widths.sum() + gap * (widths.size - 1).coerceAtLeast(0)
        val all = widthsByDetail.indexOfFirst { widthOf(it) <= available }
        if (all >= 0) return NextEntriesFit(detail = all, count = widthsByDetail[all].size)
        val least = widthsByDetail.lastIndex
        val widths = widthsByDetail.lastOrNull().orEmpty()
        var count = 0
        while (count < widths.size && widthOf(widths.take(count + 1)) <= available) count++
        return NextEntriesFit(detail = least.coerceAtLeast(0), count = count)
    }

    /**
     * How many of a wide card's next departures its block beside the journey lists in [maxHeight]
     * (any one unit): as many rows ([rowHeights], in order) as fit under the header, where
     * [headerHeights] holds the header's height for each count of hidden departures (index 0: the
     * plain "Then"; more: "Then … +N in details", a touch target). Possibly 0.
     */
    fun nextBlockRows(
        headerHeights: List<Float>,
        rowHeights: List<Float>,
        maxHeight: Float,
    ): Int =
        (rowHeights.size downTo 0).firstOrNull { shown ->
            headerHeights[rowHeights.size - shown] + rowHeights.take(shown).sum() <= maxHeight
        } ?: 0

    private const val MIN_WEIGHT = 1f

    private const val SEARCH_STEPS = 40
}
