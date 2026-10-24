package com.getcode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.clickable
import com.getcode.util.resources.R
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Describes a single action revealed behind the row content on swipe.
 *
 * @param background Background color of the action button.
 * @param onTriggered Callback when the action is tapped, or when a full swipe
 *   triggers the rightmost action automatically.
 * @param resetOnDismiss When true, a full swipe triggers [onTriggered] and animates
 *   the row back instead of settling at the dismissed position. Callbacks used with
 *   this flag should be idempotent (e.g. clipboard writes).
 * @param content Composable content displayed inside the action button (typically an [Icon]).
 */
@Stable
class SwipeAction(
    val background: Color,
    val onTriggered: () -> Unit,
    val resetOnDismiss: Boolean = false,
    val content: @Composable () -> Unit,
)

private enum class SwipeState { Settled, Revealed, Dismissed }

/**
 * Keeps at most one [SwipeActionRow] open among the rows given the same group: starting a swipe on
 * one row closes whichever other row was left revealed.
 *
 * Rows are told apart by their `stateKey`, so give each row in a group a distinct one.
 */
@Stable
class SwipeRevealGroup {
    internal var openKey: Any? by mutableStateOf(null)
}

@Composable
fun rememberSwipeRevealGroup(): SwipeRevealGroup = remember { SwipeRevealGroup() }

/**
 * Convenience overload that wraps [content] with a single red delete action.
 */
@Composable
fun SwipeActionRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    stateKey: Any? = null,
    resetOnDismiss: Boolean = false,
    content: @Composable () -> Unit,
) {
    val errorColor = CodeTheme.colors.error
    val iconSize = CodeTheme.dimens.staticGrid.x5
    val actions = remember(onDelete, resetOnDismiss, errorColor, iconSize) {
        listOf(
            SwipeAction(
                background = errorColor,
                onTriggered = onDelete,
                resetOnDismiss = resetOnDismiss,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.requiredSize(iconSize),
                )
            }
        )
    }
    SwipeActionRow(
        actions = actions,
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
    revealGroup: SwipeRevealGroup? = null,
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

    val dismissAction = actions.last()
    val currentDismissCallback by rememberUpdatedState(dismissAction.onTriggered)
    val currentDismissResets by rememberUpdatedState(dismissAction.resetOnDismiss)

    var pendingResetCallback by remember(stateKey) { mutableStateOf(false) }

    val initialState = if (initiallyRevealed) SwipeState.Revealed else SwipeState.Settled
    val state = remember(stateKey) {
        AnchoredDraggableState(
            initialValue = initialState,
            confirmValueChange = { newValue ->
                if (newValue == SwipeState.Dismissed && currentDismissResets) {
                    pendingResetCallback = true
                    false // reject settle, system animates back
                } else {
                    true
                }
            },
        )
    }
    var rowWidthPx by remember(stateKey) { mutableFloatStateOf(0f) }

    // Fire callback once for rejected dismiss (reset case), then send the row home. A rejected
    // settle does not do that by itself: the drag settles on the nearest anchor it is allowed,
    // which after a full swipe is Revealed, and the row stays open under whatever the callback
    // opened.
    LaunchedEffect(pendingResetCallback) {
        if (pendingResetCallback) {
            currentDismissCallback()
            state.animateTo(SwipeState.Settled)
        }
    }

    val dragInteractions = remember { MutableInteractionSource() }
    // The reset flag is re-armed by the next drag rather than by the effect above finishing. The
    // settle asks to confirm Dismissed on every frame it heads there, so the flag has to stay set
    // for the rest of the gesture to fire the callback once; and the effect can be left suspended
    // by the settle interrupting its animation, which must not leave the flag stuck at true for
    // the next swipe to write again without relaunching anything.
    LaunchedEffect(dragInteractions) {
        dragInteractions.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) pendingResetCallback = false
        }
    }
    if (revealGroup != null) {
        val fallbackKey = remember { Any() }
        val groupKey = stateKey ?: fallbackKey
        // Claimed when the drag starts rather than when it settles, so the other row closes while
        // this one opens. Keyed to the drag and not to the offset, which also moves while a row
        // is closing and would have it claim the group straight back.
        LaunchedEffect(dragInteractions, revealGroup, groupKey) {
            dragInteractions.interactions.collect { interaction ->
                if (interaction is DragInteraction.Start) revealGroup.openKey = groupKey
            }
        }
        LaunchedEffect(state, revealGroup, groupKey) {
            snapshotFlow { revealGroup.openKey }.collect { openKey ->
                if (openKey != null && openKey != groupKey &&
                    state.currentValue != SwipeState.Settled
                ) {
                    state.animateTo(SwipeState.Settled)
                }
            }
        }
    }

    // Fire callback for accepted dismiss (non-reset case, e.g. delete)
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeState.Dismissed) {
            currentDismissCallback()
        }
    }

    LaunchedEffect(rowWidthPx, totalRevealWidthPx) {
        if (rowWidthPx > 0f) {
            state.updateAnchors(DraggableAnchors {
                SwipeState.Settled at 0f
                SwipeState.Revealed at -totalRevealWidthPx
                SwipeState.Dismissed at -rowWidthPx
            })
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { rowWidthPx = it.width.toFloat() },
    ) {
        // Actions panel behind the foreground content
        val actionPaddingDp = actionPadding

        Layout(
            content = {
                actions.forEachIndexed { index, action ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(action.background)
                            .clickable {
                                action.onTriggered()
                                scope.launch { state.animateTo(SwipeState.Settled) }
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
            val minPx = minActionSize.roundToPx()
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
                .anchoredDraggable(
                    state = state,
                    orientation = Orientation.Horizontal,
                    interactionSource = dragInteractions,
                ),
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
