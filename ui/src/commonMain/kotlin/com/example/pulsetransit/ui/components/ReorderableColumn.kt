package com.example.pulsetransit.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import com.example.pulsetransit.data.dropIndex
import com.example.pulsetransit.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * A column whose rows can be dragged into a new order by the handle each row places: [itemContent]
 * gets a `Modifier` to put on its handle. The rows between the dragged one and where it would land
 * move aside as it passes; letting go calls [onMove] once with the start and end indices. For
 * TalkBack each row also offers "Move up" and "Move down".
 *
 * Meant for short lists inside a scrolling screen (it is not lazy): the handle takes the drag, so
 * the rest of a row still scrolls the screen.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: T, dragHandle: Modifier, isDragging: Boolean) -> Unit,
) {
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    val latestItems by rememberUpdatedState(items)
    val latestOnMove by rememberUpdatedState(onMove)

    fun heightsOf(list: List<T>) = list.map { heights[key(it)] ?: 0 }

    val from = items.indexOfFirst { key(it) == draggingKey }.takeIf { it >= 0 }
    val target = from?.let { dropIndex(it, dragOffset, heightsOf(items)) }
    val draggedHeight = draggingKey?.let { heights[it] } ?: 0
    val moveUp = stringResource(Res.string.move_up)
    val moveDown = stringResource(Res.string.move_down)

    Column(modifier = modifier) {
        items.forEachIndexed { index, item ->
            val itemKey = key(item)
            key(itemKey) {
                val isDragging = itemKey == draggingKey
                // Rows the dragged one has passed move one slot towards where it came from.
                val shift =
                    when {
                        from == null || target == null || isDragging -> 0f
                        from < target && index in (from + 1)..target -> -draggedHeight.toFloat()
                        from > target && index in target until from -> draggedHeight.toFloat()
                        else -> 0f
                    }
                val animatedShift by animateFloatAsState(shift, spring(stiffness = Spring.StiffnessMediumLow), label = "rowShift")
                val handle =
                    Modifier.pointerInput(itemKey) {
                        detectDragGestures(
                            onDragStart = {
                                draggingKey = itemKey
                                dragOffset = 0f
                            },
                            onDragEnd = {
                                val list = latestItems
                                val start = list.indexOfFirst { key(it) == draggingKey }
                                if (start >= 0) {
                                    val end = dropIndex(start, dragOffset, heightsOf(list))
                                    if (end != start) latestOnMove(start, end)
                                }
                                draggingKey = null
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                draggingKey = null
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                            },
                        )
                    }
                Box(
                    modifier =
                        Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer { translationY = if (isDragging) dragOffset else animatedShift }
                            .onSizeChanged { heights[itemKey] = it.height }
                            .semantics {
                                customActions =
                                    listOfNotNull(
                                        CustomAccessibilityAction(moveUp) {
                                            latestOnMove(index, index - 1)
                                            true
                                        }.takeIf { index > 0 },
                                        CustomAccessibilityAction(moveDown) {
                                            latestOnMove(index, index + 1)
                                            true
                                        }.takeIf { index < items.lastIndex },
                                    )
                            },
                ) {
                    itemContent(item, handle, isDragging)
                }
            }
        }
    }
}
