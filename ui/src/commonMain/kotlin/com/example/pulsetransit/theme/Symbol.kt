package com.example.pulsetransit.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/** Material Symbols' 960-unit grid, whose SVGs start at y = -960. */
private const val GRID = 960f

/** Builds a 24 dp icon from Material Symbols SVG paths (used by the generated [PulseIcons]). */
internal fun symbol(
    name: String,
    autoMirror: Boolean,
    vararg paths: String,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = GRID,
            viewportHeight = GRID,
            autoMirror = autoMirror,
        ).apply {
            group(translationY = GRID) {
                paths.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
            }
        }.build()
