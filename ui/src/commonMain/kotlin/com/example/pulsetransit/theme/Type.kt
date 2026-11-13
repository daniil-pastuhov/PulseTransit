package com.example.pulsetransit.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.pulsetransit.ui.resources.Res
import com.example.pulsetransit.ui.resources.public_sans_bold
import com.example.pulsetransit.ui.resources.public_sans_medium
import com.example.pulsetransit.ui.resources.public_sans_regular
import com.example.pulsetransit.ui.resources.public_sans_semibold
import org.jetbrains.compose.resources.Font

/** Public Sans, bundled so the apps and the widgets look the same everywhere. */
@Composable
fun publicSans(): FontFamily {
    val regular = Font(Res.font.public_sans_regular, FontWeight.Normal)
    val medium = Font(Res.font.public_sans_medium, FontWeight.Medium)
    val semibold = Font(Res.font.public_sans_semibold, FontWeight.SemiBold)
    val bold = Font(Res.font.public_sans_bold, FontWeight.Bold)
    return remember(regular, medium, semibold, bold) { FontFamily(regular, medium, semibold, bold) }
}

/** Tabular figures, so times and countdowns don't shift as their digits change. */
private const val TABULAR = "tnum"

private fun style(
    family: FontFamily,
    role: TypeRole,
) = TextStyle(
    fontFamily = family,
    fontWeight = FontWeight(role.weight),
    fontSize = role.size.sp,
    lineHeight = role.line.sp,
    letterSpacing = role.tracking.sp,
    fontFeatureSettings = TABULAR,
)

/** The Fjord type roles, mapped onto Material 3's slots. */
internal fun pulseTypography(family: FontFamily): Typography {
    val display = style(family, FjordType.display)
    val headline = style(family, FjordType.headline)
    val caption = style(family, FjordType.caption)
    return Typography(
        displayLarge = display,
        displayMedium = display,
        displaySmall = display,
        headlineLarge = display,
        headlineMedium = headline,
        headlineSmall = headline,
        titleLarge = style(family, FjordType.title),
        titleMedium = style(family, FjordType.titleSm),
        titleSmall = style(family, FjordType.label),
        bodyLarge = style(family, FjordType.body),
        bodyMedium = style(family, FjordType.bodySm),
        bodySmall = caption.copy(fontWeight = FontWeight.Normal),
        labelLarge = style(family, FjordType.label),
        labelMedium = caption.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = caption,
    )
}

/** The type roles Material 3 has no slot for. Read them with [PulseTheme.type]. */
@Immutable
data class PulseType(
    /** The board's clock. */
    val clock: TextStyle,
    /** Departure countdowns, the largest text after the clock. */
    val countdown: TextStyle,
    /** Line numbers in pills. */
    val pill: TextStyle,
)

internal fun pulseType(family: FontFamily) =
    PulseType(
        clock = style(family, FjordType.clock),
        countdown = style(family, FjordType.countdown),
        pill = style(family, FjordType.pill),
    )

internal val LocalPulseType = staticCompositionLocalOf { pulseType(FontFamily.Default) }

val PulseTheme.type: PulseType
    @Composable @ReadOnlyComposable
    get() = LocalPulseType.current
