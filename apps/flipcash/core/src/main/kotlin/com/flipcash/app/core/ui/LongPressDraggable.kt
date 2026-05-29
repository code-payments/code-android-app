package com.flipcash.app.core.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

class LongPressDraggableState internal constructor(
    internal val itemCount: Int,
) {
    internal val draggingIndex = mutableIntStateOf(-1)
    internal val dragOffsetX = mutableFloatStateOf(0f)
    internal val itemWidthPx = mutableIntStateOf(0)
    internal var onReorder: (from: Int, to: Int) -> Unit = { _, _ -> }
}

/**
 * @param key When this key changes the drag state resets. Pass the current order
 *            so that after a reorder propagates, the state clears atomically
 *            with the new layout positions.
 */
@Composable
fun rememberLongPressDraggableState(
    itemCount: Int,
    key: Any? = null,
    onReorder: (from: Int, to: Int) -> Unit,
): LongPressDraggableState {
    val state = remember(itemCount, key) { LongPressDraggableState(itemCount) }
    state.onReorder = onReorder
    return state
}

@Composable
fun Modifier.longPressDraggable(
    state: LongPressDraggableState,
    index: Int,
): Modifier {
    val displacement by remember(state, index) {
        derivedStateOf {
            val currentDragging = state.draggingIndex.intValue
            if (currentDragging == -1 || currentDragging == index) {
                0
            } else {
                val w = state.itemWidthPx.intValue
                if (w <= 0) 0
                else {
                    val draggedVisualSlot = (currentDragging +
                            (state.dragOffsetX.floatValue / w).roundToInt())
                        .coerceIn(0, state.itemCount - 1)
                    when {
                        currentDragging < draggedVisualSlot &&
                                index in (currentDragging + 1)..draggedVisualSlot -> -w
                        currentDragging > draggedVisualSlot &&
                                index in draggedVisualSlot until currentDragging -> w
                        else -> 0
                    }
                }
            }
        }
    }
    val animatedDisplacement by animateIntAsState(
        targetValue = displacement,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    )

    return this
        .zIndex(if (state.draggingIndex.intValue == index) 1f else 0f)
        .offset {
            val currentDragging = state.draggingIndex.intValue
            val dx = when {
                // Dragged item follows finger directly
                currentDragging == index -> state.dragOffsetX.floatValue.roundToInt()
                // Non-dragged items animate during an active drag
                currentDragging != -1 -> animatedDisplacement
                // No drag active — snap to natural position
                else -> 0
            }
            IntOffset(dx, 0)
        }
        .onSizeChanged { state.itemWidthPx.intValue = it.width }
        .pointerInput(state) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    state.draggingIndex.intValue = index
                    state.dragOffsetX.floatValue = 0f
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    state.dragOffsetX.floatValue += dragAmount.x
                },
                onDragEnd = {
                    val w = state.itemWidthPx.intValue
                    if (w > 0) {
                        val from = state.draggingIndex.intValue
                        val to = (from + (state.dragOffsetX.floatValue / w).roundToInt())
                            .coerceIn(0, state.itemCount - 1)
                        if (from != to) {
                            // Snap offset to exact target so item stays visually
                            // in place until the reorder propagates and the state
                            // resets via key change.
                            state.dragOffsetX.floatValue = (to - from).toFloat() * w
                            state.onReorder(from, to)
                            return@detectDragGesturesAfterLongPress
                        }
                    }
                    state.draggingIndex.intValue = -1
                    state.dragOffsetX.floatValue = 0f
                },
                onDragCancel = {
                    state.draggingIndex.intValue = -1
                    state.dragOffsetX.floatValue = 0f
                },
            )
        }
}
