package com.getcode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import com.getcode.theme.CodeTheme
import com.getcode.theme.DesignSystem
import com.getcode.theme.White50
import com.getcode.ui.core.rememberedClickable
import com.getcode.util.resources.R
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Describes a single action revealed behind the row content on swipe.
 *
 * @param background Background color of the action button.
 * @param onTriggered Callback when the action is tapped, or when a full swipe
 *   triggers the rightmost action automatically.
 * @param content Composable content displayed inside the action button (typically an [Icon]).
 */
class SwipeAction(
    val background: Color,
    val onTriggered: () -> Unit,
    val content: @Composable () -> Unit,
)

private enum class SwipeState { Settled, Revealed, Dismissed }

/**
 * Convenience overload that wraps [content] with a single red delete action.
 */
@Composable
fun SwipeActionRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    stateKey: Any? = null,
    content: @Composable () -> Unit,
) {
    SwipeActionRow(
        actions = listOf(
            SwipeAction(
                background = CodeTheme.colors.error,
                onTriggered = onDelete,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.requiredSize(CodeTheme.dimens.staticGrid.x5),
                )
            }
        ),
        modifier = modifier,
        stateKey = stateKey,
        content = content,
    )
}

/**
 * A swipe-to-reveal row that reveals one or more [actions] behind the foreground [content].
 *
 * Swiping left reveals all actions. The **last** action in the list occupies the
 * rightmost position, grows from a circle into a rounded rectangle as the swipe
 * progresses, and is automatically triggered on a full swipe (dismiss). All other
 * actions remain fixed-size circles and are click-only.
 */
@Composable
fun SwipeActionRow(
    actions: List<SwipeAction>,
    modifier: Modifier = Modifier,
    stateKey: Any? = null,
    initiallyRevealed: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (actions.isEmpty()) {
        Box(modifier) { content() }
        return
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val actionPadding = CodeTheme.dimens.inset
    val minActionSize = CodeTheme.dimens.staticGrid.x10

    // Each action slot = minSize + padding on each side.
    // Total reveal = N slots + one extra padding on the outer edge.
    val totalRevealWidth = (minActionSize + actionPadding) * actions.size + actionPadding
    val totalRevealWidthPx = with(density) { totalRevealWidth.toPx() }

    val initialState = if (initiallyRevealed) SwipeState.Revealed else SwipeState.Settled
    val state = remember(stateKey) { AnchoredDraggableState(initialValue = initialState) }
    var rowWidthPx by remember(stateKey) { mutableFloatStateOf(0f) }

    LaunchedEffect(rowWidthPx, totalRevealWidthPx) {
        if (rowWidthPx > 0f) {
            state.updateAnchors(DraggableAnchors {
                SwipeState.Settled at 0f
                SwipeState.Revealed at -totalRevealWidthPx
                SwipeState.Dismissed at -rowWidthPx
            })
        }
    }

    val dismissAction = actions.last()
    val currentDismissCallback by rememberUpdatedState(dismissAction.onTriggered)
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeState.Dismissed) {
            currentDismissCallback()
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { rowWidthPx = it.width.toFloat() },
    ) {
        // Actions panel behind the foreground content
        val actionPaddingDp = actionPadding
        val minActionSizeDp = minActionSize

        Layout(
            content = {
                actions.forEachIndexed { index, action ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(action.background)
                            .rememberedClickable {
                                scope.launch { state.snapTo(SwipeState.Settled) }
                                action.onTriggered()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        action.content()
                    }
                }
            },
            modifier = Modifier.matchParentSize(),
        ) { measurables, constraints ->
            val absOffset = abs(state.offset)
            val padPx = actionPaddingDp.roundToPx()
            val minPx = minActionSizeDp.roundToPx()
            val n = measurables.size
            val actionH = (constraints.maxHeight - padPx * 2).coerceAtLeast(minPx)

            // Non-dismiss actions each occupy a fixed slot: padding + minSize.
            // The dismiss action (last) gets the remaining width and grows on full swipe.
            val fixedSlotWidth = minPx + padPx
            val nonDismissTotal = (n - 1) * fixedSlotWidth
            val dismissAvailable = (absOffset - padPx - nonDismissTotal).coerceAtLeast(0f)
            val dismissW = (dismissAvailable.toInt() - padPx).coerceAtLeast(minPx)

            val placeables = measurables.mapIndexed { index, measurable ->
                val w = if (index == n - 1) dismissW else minPx
                measurable.measure(Constraints.fixed(w, actionH))
            }

            layout(constraints.maxWidth, constraints.maxHeight) {
                val yCenter = (constraints.maxHeight - actionH) / 2
                var x = constraints.maxWidth - padPx

                // Dismiss action (rightmost)
                x -= placeables.last().width
                placeables.last().place(x, yCenter)

                // Remaining actions, right to left
                for (i in n - 2 downTo 0) {
                    x -= padPx + placeables[i].width
                    placeables[i].place(x, yCenter)
                }
            }
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

@Preview
@Composable
private fun SwipeActionRowPreview() {
    DesignSystem {
        Column(
            modifier = Modifier
                .background(CodeTheme.colors.background)
                .padding(vertical = CodeTheme.dimens.grid.x4),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            val editAction = SwipeAction(
                background = White50,
                onTriggered = {},
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.requiredSize(CodeTheme.dimens.staticGrid.x5),
                )
            }
            val deleteAction = SwipeAction(
                background = CodeTheme.colors.error,
                onTriggered = {},
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.requiredSize(CodeTheme.dimens.staticGrid.x5),
                )
            }

            // Two actions — revealed
            SwipeActionRow(
                actions = listOf(editAction, deleteAction),
                initiallyRevealed = true,
            ) {
                FakeRowContent(title = "Swiped row with two actions", subtitle = "edit + delete")
            }

            // Single action — settled (swipe to interact)
            SwipeActionRow(onDelete = {}) {
                FakeRowContent(title = "Single delete action", subtitle = "swipe left to reveal")
            }
        }
    }
}

@Composable
private fun FakeRowContent(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CodeTheme.colors.background)
            .padding(CodeTheme.dimens.inset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(CodeTheme.dimens.staticGrid.x10)
                .clip(RoundedCornerShape(CodeTheme.dimens.grid.x3))
                .background(White50),
        )
        Spacer(Modifier.width(CodeTheme.dimens.grid.x3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )
            Text(
                text = subtitle,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}
