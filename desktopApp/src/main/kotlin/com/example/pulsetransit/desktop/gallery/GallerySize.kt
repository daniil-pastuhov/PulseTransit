package com.example.pulsetransit.desktop.gallery

/** A screen size in dp, rendered at [density] pixels per dp. */
class GallerySize(
    val name: String,
    val widthDp: Int,
    val heightDp: Int,
    val density: Float = 1f,
)

val GALLERY_SIZES =
    listOf(
        GallerySize("phone", 400, 860, density = 2f),
        GallerySize("tabP", 820, 1180),
        GallerySize("tabL", 1180, 820),
        GallerySize("tv", 1920, 1080),
        // An Android TV's size in dp (1080p at density 2).
        GallerySize("tvdp", 960, 540),
    )
