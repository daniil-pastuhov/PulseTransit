package com.example.pulsetransit.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.theme.PulseTransitTheme
import com.example.pulsetransit.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * The first-launch choice between following the device and the wall board. Tapping a card
 * chooses it; there is no Continue. [selected] marks the mode chosen before, when the user came
 * back from the intro. From 600 dp the cards sit side by side.
 */
@Composable
fun ModeSelectionScreen(
    onChoose: (AppMode) -> Unit,
    modifier: Modifier = Modifier,
    selected: AppMode? = null,
) {
    PulseTransitTheme {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val sideBySide = maxWidth >= SIDE_BY_SIDE_FROM
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = FjordDimens.space4, vertical = FjordDimens.space6),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Column(
                        modifier = Modifier.widthIn(max = ONBOARDING_COLUMN_WIDTH).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(FjordDimens.space4),
                    ) {
                        BrandRow()
                        Text(
                            text = stringResource(Res.string.mode_choose_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        val cards: List<@Composable (Modifier) -> Unit> =
                            listOf(
                                { cardModifier ->
                                    ModeCard(
                                        icon = PulseIcons.NearMe,
                                        title = stringResource(Res.string.mode_on_spot),
                                        description = stringResource(Res.string.mode_on_spot_description),
                                        selected = selected == AppMode.ON_SPOT,
                                        tall = sideBySide,
                                        onClick = { onChoose(AppMode.ON_SPOT) },
                                        modifier = cardModifier,
                                    )
                                },
                                { cardModifier ->
                                    ModeCard(
                                        icon = PulseIcons.Dashboard,
                                        title = stringResource(Res.string.mode_board),
                                        description = stringResource(Res.string.mode_board_description),
                                        selected = selected == AppMode.BOARD,
                                        tall = sideBySide,
                                        onClick = { onChoose(AppMode.BOARD) },
                                        modifier = cardModifier,
                                    )
                                },
                            )
                        if (sideBySide) {
                            Row(horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3)) {
                                cards.forEach { it(Modifier.weight(1f)) }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(FjordDimens.space3)) {
                                cards.forEach { it(Modifier.fillMaxWidth()) }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(FjordDimens.space2),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                PulseIcons.Info,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(Res.string.mode_choose_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The app's mark and name, above the first question. */
@Composable
private fun BrandRow() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(FjordDimens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(FjordDimens.radiusMd))
                    .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(PulseIcons.DepartureBoard, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        }
        Text(
            text = stringResource(Res.string.app_name_brand),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * One mode: its icon, name and what it does. Selected, it is outlined in the primary colour with a
 * check. [tall] stacks the icon above the text, for the side-by-side layout.
 */
@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    tall: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(FjordDimens.radiusLg)
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (selected) colors.primaryContainer else colors.surface,
        border = if (selected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant),
        modifier =
            modifier
                .heightIn(min = if (tall) 200.dp else 96.dp)
                .semantics {
                    this.selected = selected
                    role = Role.Button
                },
    ) {
        Box {
            val iconTile: @Composable () -> Unit = {
                Box(
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(FjordDimens.radiusMd))
                            .background(if (selected) colors.primary else colors.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = if (selected) colors.onPrimary else colors.onPrimaryContainer,
                    )
                }
            }
            val text: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(FjordDimens.space1)) {
                    Text(text = title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    )
                }
            }
            if (tall) {
                Column(
                    modifier = Modifier.padding(FjordDimens.space5),
                    verticalArrangement = Arrangement.spacedBy(FjordDimens.space3),
                ) {
                    iconTile()
                    text()
                }
            } else {
                Row(
                    modifier = Modifier.padding(start = FjordDimens.space4, top = FjordDimens.space4, bottom = FjordDimens.space4, end = FjordDimens.space8),
                    horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    iconTile()
                    text()
                }
            }
            if (selected) {
                Icon(
                    PulseIcons.CheckCircleFilled,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(FjordDimens.space3).size(24.dp),
                )
            }
        }
    }
}

/** The onboarding screens' content column; on wide windows it stays centred at this width. */
internal val ONBOARDING_COLUMN_WIDTH = 560.dp

private val SIDE_BY_SIDE_FROM = 600.dp
