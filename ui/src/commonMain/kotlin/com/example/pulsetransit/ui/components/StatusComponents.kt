package com.example.pulsetransit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.theme.PulseTheme

/**
 * A delay as a small warning chip, "+3". The planned time it replaces is shown struck through
 * beside the new one by the caller.
 */
@Composable
fun DelayChip(
    minutes: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "+$minutes",
        style = MaterialTheme.typography.labelSmall,
        color = PulseTheme.colors.onWarningContainer,
        maxLines = 1,
        softWrap = false,
        modifier =
            modifier
                .clip(RoundedCornerShape(FjordDimens.radiusSm))
                .background(PulseTheme.colors.warningContainer)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A callout's meaning, which picks its container colour and default icon. */
enum class CalloutTone { INFO, WARNING, ERROR, SUCCESS }

private data class ToneColors(
    val container: Color,
    val content: Color,
    val icon: ImageVector,
)

@Composable
private fun toneColors(tone: CalloutTone): ToneColors =
    when (tone) {
        CalloutTone.INFO -> ToneColors(PulseTheme.colors.infoContainer, PulseTheme.colors.onInfoContainer, PulseIcons.Info)
        CalloutTone.WARNING ->
            ToneColors(PulseTheme.colors.warningContainer, PulseTheme.colors.onWarningContainer, PulseIcons.Error)
        CalloutTone.ERROR ->
            ToneColors(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, PulseIcons.Error)
        CalloutTone.SUCCESS ->
            ToneColors(PulseTheme.colors.successContainer, PulseTheme.colors.onSuccessContainer, PulseIcons.CheckCircleFilled)
    }

/**
 * A T4 callout: a persistent, inline status ("No internet — shown data is 12 min old") with an
 * optional title and actions. It is announced politely when it appears. Actions are text buttons in
 * [actions]; they inherit the callout's content colour.
 */
@Composable
fun Callout(
    body: String,
    modifier: Modifier = Modifier,
    tone: CalloutTone = CalloutTone.INFO,
    title: String? = null,
    icon: ImageVector? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = toneColors(tone)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(FjordDimens.radiusMd))
                .background(colors.container)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = FjordDimens.space4, vertical = FjordDimens.space3),
        horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3),
    ) {
        Icon(icon ?: colors.icon, contentDescription = null, tint = colors.content, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = colors.content)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.content)
            if (actions != null) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                    CompositionLocalProvider(
                        LocalContentColor provides colors.content,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(FjordDimens.space2), content = actions)
                    }
                }
            }
        }
    }
}

/**
 * A T5 failure or an empty state that takes the place of a screen or a panel's content: icon,
 * title, body and at most one action, centred.
 */
@Composable
fun ScreenMessage(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = FjordDimens.space6, vertical = FjordDimens.space8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FjordDimens.space3),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(48.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
        action?.invoke()
    }
}

/** A transport notice from Entur ("Track work, buses replace trains"), marked with a warning icon. */
@Composable
fun NoticeText(
    notice: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(FjordDimens.space1)) {
        Icon(
            PulseIcons.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 2.dp).size(14.dp),
        )
        Text(text = notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
