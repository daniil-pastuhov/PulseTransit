package com.example.pulsetransit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.SCRIM_ALPHA

/** From this window width a sheet comes in from the right instead of from the bottom. */
private val SIDE_SHEET_MIN_WINDOW = 700.dp
private val SIDE_SHEET_MAX_WIDTH = 440.dp

/**
 * A sheet for details: from the bottom on a phone; on a wall, fold or desktop window, from the
 * right, at most 440 dp wide, so the board stays in view beside it. Either way over a 60% scrim
 * (40% vanished over OLED black) and outlined, since dark elevation is only a hairline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val windowWidth =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.width
                .toDp()
        }
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA)
    if (windowWidth < SIDE_SHEET_MIN_WINDOW) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            scrimColor = scrim,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            content = content,
        )
        return
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(scrim)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.CenterEnd,
        ) {
            val shape = RoundedCornerShape(topStart = FjordDimens.radiusXl, bottomStart = FjordDimens.radiusXl)
            Column(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .widthIn(max = SIDE_SHEET_MAX_WIDTH)
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
                        // Taps inside stay inside.
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(top = FjordDimens.space6),
                content = content,
            )
        }
    }
}
