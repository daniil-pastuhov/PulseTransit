package com.example.pulsetransit.data

/**
 * Places in Norway that on-spot mode can pretend the device is at, so someone abroad (a tester, a
 * store reviewer) can try it. Each is a busy station, so there are stops and routes around it. The
 * names are proper nouns, the same in every language.
 */
enum class SamplePlace(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
) {
    OSLO_S("Oslo S", 59.9111, 10.7528),
    BERGEN("Bergen", 60.3904, 5.3330),
    TRONDHEIM("Trondheim S", 63.4363, 10.3990),
}
