package com.example.pulsetransit.data

/**
 * Whether a point is in Norway, roughly: mainland Norway as a coarse outline (tens of kilometres
 * off near the border, with the coast pushed out to sea), plus Svalbard. It only decides whether
 * on-spot mode tells someone abroad that PulseTransit covers Norway, so near the border either
 * answer is fine; Entur still decides which stops are near.
 */
fun isInNorway(
    latitude: Double,
    longitude: Double,
): Boolean = isInSvalbard(latitude, longitude) || isInOutline(latitude, longitude, MAINLAND_OUTLINE)

private fun isInSvalbard(
    latitude: Double,
    longitude: Double,
) = latitude in SVALBARD_LATITUDES && longitude in SVALBARD_LONGITUDES

private val SVALBARD_LATITUDES = 74.0..81.0
private val SVALBARD_LONGITUDES = 10.0..34.0

/**
 * Mainland Norway as (latitude, longitude) corners: up the west coast from Lindesnes, east along
 * the north coast, then south along the borders with Russia, Finland and Sweden, and back west
 * along the Skagerrak.
 */
private val MAINLAND_OUTLINE =
    listOf(
        57.85 to 7.0,
        58.3 to 5.3,
        59.2 to 4.5,
        61.0 to 4.4,
        62.3 to 4.7,
        63.4 to 7.0,
        64.6 to 9.5,
        66.2 to 11.7,
        67.6 to 11.5,
        68.9 to 14.3,
        69.5 to 15.9,
        70.3 to 18.5,
        71.3 to 24.5,
        71.3 to 27.8,
        70.6 to 31.3,
        69.79 to 30.84, // Grense Jakobselv
        69.66 to 30.15, // Storskog
        69.05 to 28.93, // the Norway–Finland–Russia tripoint
        69.75 to 29.3,
        70.1 to 27.9, // Nuorgam, the Tana's mouth
        69.4 to 25.8, // Karigasniemi
        68.75 to 23.3,
        68.95 to 21.0,
        69.06 to 20.55, // the Norway–Sweden–Finland tripoint
        68.4 to 18.1, // Riksgränsen
        67.0 to 16.1,
        66.0 to 14.5,
        64.4 to 13.9,
        63.3 to 12.1, // Storlien
        62.3 to 12.2,
        61.0 to 12.6,
        60.1 to 12.5,
        59.3 to 11.82,
        58.9 to 11.7, // round Enningdalen, which reaches south of Strömstad
        58.9 to 11.5,
        59.0 to 11.4, // up the Iddefjord
        59.09 to 11.26, // Svinesund
        59.0 to 11.05,
        58.8 to 10.6,
        58.2 to 8.6,
    )

/** Ray casting: a ray from the point crosses an odd number of the outline's edges when inside. */
private fun isInOutline(
    latitude: Double,
    longitude: Double,
    outline: List<Pair<Double, Double>>,
): Boolean {
    var inside = false
    var previous = outline.last()
    for (corner in outline) {
        val (lat1, lon1) = previous
        val (lat2, lon2) = corner
        if ((lat1 > latitude) != (lat2 > latitude)) {
            val crossingLon = lon1 + (latitude - lat1) / (lat2 - lat1) * (lon2 - lon1)
            if (longitude < crossingLon) inside = !inside
        }
        previous = corner
    }
    return inside
}
