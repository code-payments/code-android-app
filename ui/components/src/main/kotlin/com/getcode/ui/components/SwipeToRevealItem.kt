package com.getcode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.rememberedClickable
import com.getcode.util.resources.R
import kotlin.math.abs
import kotlinx.coroutines.launch

private enum class RevealValue { Settled, Revealed, Dismissed }

/**
 * A swipe-to-reveal container that reveals a delete action behind the content.
 * Swiping left reveals a growing circular delete button; swiping past the threshold
 * dismisses the item and triggers [onDelete].
 */
@Composable
fun SwipeToRevealItem(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val actionWidth = CodeTheme.dimens.staticGrid.x10 + CodeTheme.dimens.inset * 2
    val actionWidthPx = with(density) { actionWidth.toPx() }

    val state = remember { AnchoredDraggableState(initialValue = RevealValue.Settled) }
    var rowWidthPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(rowWidthPx, actionWidthPx) {
        if (rowWidthPx > 0f) {
            state.updateAnchors(DraggableAnchors {
                RevealValue.Settled at 0f
                RevealValue.Revealed at -actionWidthPx
                RevealValue.Dismissed at -rowWidthPx
            })
        }
    }

    val currentOnDelete by rememberUpdatedState(onDelete)
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == RevealValue.Dismissed) {
            currentOnDelete()
        }
    }

    val actionPadding = CodeTheme.dimens.inset
    val minActionSize = CodeTheme.dimens.staticGrid.x10

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { rowWidthPx = it.width.toFloat() },
    ) {
        // Action area — grows from circle to rounded rect as swipe progresses
        Box(
            modifier = Modifier
                .matchParentSize()
                .layout { measurable, constraints ->
                    val absOffset = abs(state.offset)
                    val paddingPx = actionPadding.roundToPx()
                    val minPx = minActionSize.roundToPx()

                    val w = (absOffset.toInt() - paddingPx * 2).coerceAtLeast(minPx)
                    val h = (constraints.maxHeight - paddingPx * 2).coerceAtLeast(minPx)

                    val placeable = measurable.measure(
                        constraints.copy(
                            minWidth = w, maxWidth = w,
                            minHeight = h, maxHeight = h,
                        )
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(
                            constraints.maxWidth - placeable.width - paddingPx,
                            (constraints.maxHeight - placeable.height) / 2,
                        )
                    }
                }
                .clip(RoundedCornerShape(50))
                .background(CodeTheme.colors.error)
                .rememberedClickable {
                    scope.launch {
                        state.snapTo(RevealValue.Settled)
                    }
                    onDelete()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.requiredSize(CodeTheme.dimens.staticGrid.x5),
            )
        }

        // Foreground content that slides
        Box(
            modifier = Modifier
                .offset { IntOffset(state.offset.toInt(), 0) }
                .anchoredDraggable(state, Orientation.Horizontal),
        ) {
            content()
        }
    }
}
