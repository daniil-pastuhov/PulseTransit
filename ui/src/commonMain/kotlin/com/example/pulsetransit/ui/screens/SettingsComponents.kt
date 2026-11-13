package com.example.pulsetransit.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons

// The pieces Settings is built from: a titled section holding a card of rows, and the three kinds
// of row: a switch, a segmented choice and a link to a screen of its own. Every change applies at
// once, so none of them has a Save.

/**
 * A group of settings: its [title] (a heading) with an optional [action] beside it, a [hint] under
 * it, then a card of rows. Rows are separated by [SettingsDivider].
 */
@Composable
internal fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FjordDimens.space2)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = FjordDimens.space1).heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            action?.invoke()
        }
        hint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = FjordDimens.space1),
            )
        }
        Surface(
            shape = RoundedCornerShape(FjordDimens.radiusLg),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = FjordDimens.space1), content = content)
        }
    }
}

/** The line between two rows of a section's card. */
@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = FjordDimens.space4),
    )
}

/** A row's title and the line under it that says what it does. */
@Composable
private fun RowText(
    title: String,
    hint: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        hint?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** An on/off setting; the whole row toggles it. */
@Composable
internal fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = FjordDimens.touch)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = FjordDimens.space4, vertical = FjordDimens.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3),
    ) {
        RowText(title, hint, Modifier.weight(1f).alpha(if (enabled) 1f else DISABLED_ALPHA))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * One of a few values, as segmented buttons under the row's title. Disabled, the hint says why,
 * and the row keeps its place so nothing shifts when it is enabled.
 */
@Composable
internal fun <T> SettingSegmented(
    title: String?,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = FjordDimens.space4, vertical = FjordDimens.space3),
        verticalArrangement = Arrangement.spacedBy(FjordDimens.space2),
    ) {
        if (title != null) RowText(title, hint, Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    // No check mark: the fill shows the choice, and five labels need the room.
                    icon = {},
                    // Two lines before an ellipsis: "Fewer transfers" in a third of a phone.
                    label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) },
                )
            }
        }
    }
}

/** A link to a screen of its own: icon, title, what it is, the current value and a chevron. */
@Composable
internal fun SettingLink(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    hint: String? = null,
    value: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = FjordDimens.touch)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = FjordDimens.space4, vertical = FjordDimens.space3)
                .alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        RowText(title, hint, Modifier.weight(1f))
        value?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(PulseIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** How faded a disabled row's text is. */
internal const val DISABLED_ALPHA = 0.5f
