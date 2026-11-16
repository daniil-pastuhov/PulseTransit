package com.example.pulsetransit.desktop.gallery

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.example.pulsetransit.ui.screens.GalleryBoard
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.util.Locale
import kotlin.system.exitProcess

// The board gallery, a dev tool: renders the board offscreen with ImageComposeScene (no window, no
// Koin, no network) from the fixtures in GalleryFixtures.kt, one PNG per scenario, state and size:
// <out>/<scenario>-<state>-<size>[-fs150].png. Run it with ./gradlew :desktopApp:renderGallery (see
// desktopApp/build.gradle.kts for the options) and look at the images after changing the board.

// Frames drawn before the one saved, 16 ms apart on the scene's clock: the route cards measure
// their heads in the first frames and only then show their rows, and animations settle.
private const val WARM_UP_FRAMES = 30
private const val FRAME_NANOS = 16_000_000L
private const val PERCENT = 100
private const val NANOS_PER_MILLI = 1_000_000

/** Draws [content] offscreen at [size] and returns it as PNG bytes. */
fun renderPng(
    size: GallerySize,
    fontScale: Float,
    content: @Composable () -> Unit,
): ByteArray {
    val scene =
        ImageComposeScene(
            width = (size.widthDp * size.density).toInt(),
            height = (size.heightDp * size.density).toInt(),
            density = Density(size.density, fontScale),
            content = content,
        )
    try {
        var nanos = 0L
        repeat(WARM_UP_FRAMES) {
            scene.render(nanos)
            nanos += FRAME_NANOS
        }
        val image = scene.render(nanos)
        return checkNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "PNG encoding failed" }.bytes
    } finally {
        scene.close()
    }
}

private class GalleryOptions(
    val out: File,
    val scenarios: Set<String>?,
    val sizes: Set<String>?,
    val fontScale: Float,
    val locale: String,
)

private fun parseOptions(args: Array<String>): GalleryOptions {
    val values =
        args.associate { arg ->
            require(arg.startsWith("--") && "=" in arg) { "Expected --name=value, got '$arg'" }
            arg.removePrefix("--").substringBefore("=") to arg.substringAfter("=")
        }

    fun list(name: String) =
        values[name]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
    return GalleryOptions(
        out = File(values["out"] ?: "desktopApp/build/gallery").absoluteFile,
        scenarios = list("scenarios")?.toSet(),
        sizes = list("sizes")?.toSet(),
        fontScale = values["fontScale"]?.toFloat() ?: 1f,
        locale = values["locale"] ?: "en",
    )
}

fun main(args: Array<String>) {
    val status =
        try {
            render(parseOptions(args))
            0
        } catch (e: IllegalArgumentException) {
            System.err.println(e.message)
            1
        } catch (e: IllegalStateException) {
            System.err.println(e.message)
            1
        }
    // Compose and the board's tickers leave threads behind; nothing here should keep the JVM up.
    exitProcess(status)
}

private fun render(options: GalleryOptions) {
    // The strings follow the JVM's locale, as on the desktop app.
    Locale.setDefault(Locale.forLanguageTag(options.locale))
    val now = galleryNow()
    val scenarios = galleryScenarios(now).filter { options.scenarios == null || it.name in options.scenarios }
    val sizes = GALLERY_SIZES.filter { options.sizes == null || it.name in options.sizes }
    require(scenarios.isNotEmpty()) { "No scenario matches: one of ${galleryScenarios(now).joinToString { it.name }}" }
    require(sizes.isNotEmpty()) { "No size matches: one of ${GALLERY_SIZES.joinToString { it.name }}" }
    val suffix = if (options.fontScale == 1f) "" else "-fs${(options.fontScale * PERCENT).toInt()}"
    val started = System.nanoTime()
    var count = 0
    options.out.mkdirs()
    val boards = scenarios.flatMap { scenario -> GalleryState.entries.map { state -> Triple(scenario, state, galleryUiState(scenario, state, now)) } }
    for ((scenario, state, uiState) in boards) {
        for (size in sizes) {
            val png = renderPng(size, options.fontScale) { GalleryBoard(uiState) }
            val file = File(options.out, "${scenario.name}-${state.label}-${size.name}$suffix.png")
            file.writeBytes(png)
            println(file.path)
            count++
        }
    }
    println("Rendered $count images in ${(System.nanoTime() - started) / NANOS_PER_MILLI} ms")
}
