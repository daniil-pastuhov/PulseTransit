package com.example.pulsetransit.data

/**
 * What a stop panel's head says about the directions it shows: "all directions", the platforms
 * ("Platforms 2–4") or a count ("3 of 7 directions"). Each platform words it in its own strings.
 */
sealed interface DirectionSummary {
    /** No directions were picked, so every one is shown. */
    data object All : DirectionSummary

    /** Every picked direction is a numbered platform; [codes] as "2–4" or "A, C". */
    data class Platforms(
        val codes: String,
        val count: Int,
    ) : DirectionSummary

    /** [count] picked directions out of [total], or of an unknown total when the quays are not known. */
    data class Some(
        val count: Int,
        val total: Int?,
    ) : DirectionSummary
}

/**
 * Summarises the [selected] quay ids (null when the user picked none) of a stop whose quays are
 * [quays], which may be empty when they could not be loaded.
 */
fun directionSummary(
    selected: Set<String>?,
    quays: List<Quay>,
): DirectionSummary {
    if (selected.isNullOrEmpty()) return DirectionSummary.All
    val known = quays.associateBy { it.id }
    if (quays.isNotEmpty() && quays.all { it.id in selected }) return DirectionSummary.All
    val picked = selected.mapNotNull { known[it] }
    val codes = picked.mapNotNull { it.publicCode?.takeIf(String::isNotBlank) }
    return if (picked.size == selected.size && codes.size == picked.size) {
        DirectionSummary.Platforms(codeRange(codes), codes.size)
    } else {
        DirectionSummary.Some(selected.size, quays.size.takeIf { it > 0 })
    }
}

/** "2–4" for a consecutive run of numbers, otherwise the codes in natural order: "1, 3" or "A, B". */
internal fun codeRange(codes: List<String>): String {
    val sorted = codes.distinct().sortedWith { a, b -> naturalCompare(a, b) }
    val numbers = sorted.mapNotNull { it.toIntOrNull() }
    val consecutive =
        numbers.size == sorted.size && numbers.size > 2 && numbers.zipWithNext().all { (a, b) -> b == a + 1 }
    return if (consecutive) "${numbers.first()}–${numbers.last()}" else sorted.joinToString(", ")
}
