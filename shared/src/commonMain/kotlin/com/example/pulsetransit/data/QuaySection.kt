package com.example.pulsetransit.data

/** Quays of one child stop, in the order the quay picker shows them. */
data class QuaySection(
    /** The child stop's name; null when the stop has only one, so a heading would just repeat it. */
    val title: String?,
    val quays: List<Quay>,
)

/**
 * Groups [stop]'s quays by the child stop they belong to and sorts each group by platform, so a
 * parent like Oslo S reads as "the station's tracks 1–19" and "the bus terminal's bays 31–43"
 * rather than one list in whatever order Entur returned. The child named like the parent comes
 * first; quays without a platform code go after those with one.
 */
fun quaySections(stop: StopPlace): List<QuaySection> {
    val groups = stop.quays.groupBy { it.stopPlaceId ?: stop.id }
    return groups.values
        .map { quays -> quays.first().stopPlaceName to quays.sortedWith(quayOrder) }
        .sortedWith(
            compareBy<Pair<String?, List<Quay>>> { (name, _) -> name != null && name != stop.name }
                .thenBy { (name, _) -> name.orEmpty() },
        ).map { (name, quays) ->
            QuaySection(title = if (groups.size > 1) name ?: stop.name else null, quays = quays)
        }
}

private val quayOrder: Comparator<Quay> =
    Comparator { a, b ->
        val codeA = a.publicCode?.takeIf(String::isNotBlank)
        val codeB = b.publicCode?.takeIf(String::isNotBlank)
        when {
            codeA != null && codeB != null -> naturalCompare(codeA, codeB)
            codeA != null -> -1
            codeB != null -> 1
            else -> (a.description ?: a.name ?: a.id).compareTo(b.description ?: b.name ?: b.id)
        }
    }

/** Compares runs of digits by value, so "2" < "10" and "L2" < "L2x" < "L12". */
fun naturalCompare(
    a: String,
    b: String,
): Int {
    val runsA = splitRuns(a)
    val runsB = splitRuns(b)
    for (i in 0 until minOf(runsA.size, runsB.size)) {
        val x = runsA[i]
        val y = runsB[i]
        val result =
            if (x.first().isDigit() && y.first().isDigit()) {
                compareValuesBy(x, y, { it.trimStart('0').length }, { it.trimStart('0') })
            } else {
                x.compareTo(y, ignoreCase = true)
            }
        if (result != 0) return result
    }
    return runsA.size.compareTo(runsB.size).takeIf { it != 0 } ?: a.compareTo(b)
}

private fun splitRuns(s: String): List<String> {
    val runs = mutableListOf<String>()
    var start = 0
    for (i in 1..s.length) {
        if (i == s.length || s[i].isDigit() != s[i - 1].isDigit()) {
            runs += s.substring(start, i)
            start = i
        }
    }
    return runs
}
