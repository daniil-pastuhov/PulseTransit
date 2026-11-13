package com.example.pulsetransit.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val PULSE_MIN_ALPHA = 0.15f
private const val PULSE_MAX_ALPHA = 0.45f
private const val PULSE_MILLIS = 900

/** Alpha that breathes between two values, for skeleton blocks still waiting on data. */
@Composable
fun rememberPlaceholderPulse(): Float {
    val transition = rememberInfiniteTransition(label = "placeholderPulse")
    val alpha by transition.animateFloat(
        initialValue = PULSE_MIN_ALPHA,
        targetValue = PULSE_MAX_ALPHA,
        animationSpec =
            infiniteRepeatable(
                animation = tween(PULSE_MILLIS, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "alpha",
    )
    return alpha
}

/**
 * A rounded skeleton block standing in for a piece of text or a badge. By default it is a solid
 * surface-container block that breathes with [alpha] (a translucent tint vanished on white cards);
 * pass [color] to tint it instead, e.g. on a coloured banner.
 */
@Composable
fun PlaceholderBlock(
    alpha: Float,
    modifier: Modifier = Modifier,
    color: Color? = null,
    cornerRadius: Dp = 6.dp,
) {
    val fill = color?.copy(alpha = alpha) ?: MaterialTheme.colorScheme.surfaceContainer.copy(alpha = SOLID_BASE_ALPHA + alpha)
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(cornerRadius))
                .background(fill),
    )
}

/** With the pulse's 0.15–0.45 added, a default block breathes between 70% and 100%. */
private const val SOLID_BASE_ALPHA = 0.55f

/**
 * Blur-up placeholder: [skeleton] is drawn blurred, the way content looks before it comes into
 * focus, with a sharp spinner and [label] on top so it reads unmistakably as loading. The skeleton
 * is hidden from accessibility; only [label] is announced.
 *
 * `Modifier.blur` needs API 31. Below that it is a no-op and the pulsing skeleton alone remains.
 */
@Composable
fun BlurredPlaceholder(
    label: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    skeleton: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.blur(6.dp), content = skeleton)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = contentColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}

/** Thin indeterminate bar shown over content that is being refreshed in place. */
@Composable
fun RefreshingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    LinearProgressIndicator(
        modifier = modifier,
        color = color,
        trackColor = Color.Transparent,
    )
}
