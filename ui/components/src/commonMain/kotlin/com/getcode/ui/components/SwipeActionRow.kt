package com.getcode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.getcode.theme.CodeTheme
import com.getcode.theme.DesignSystem
import com.getcode.theme.White50
import com.getcode.util.resources.Res
import com.getcode.util.resources.ic_delete
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource

/**
 * Describes a single action revealed behind the row content on swipe.
 *
 * @param background Background color of the action button.
 * @param onTriggered Callback when the action is tapped, or when a full swipe
 *   triggers the rightmost action automatically.
 * @param resetOnDismiss When true, a full swipe triggers [onTriggered] and animates
 *   the row back instead of settling at the dismissed position. Callbacks used with
 *   this flag should be idempotent (e.g. clipboard writes).
 * @param label Optional title drawn under the circle in small secondary text. With a label the
 *   action is a circle with its title under it, and its slot widens to fit the title; without
 *   one it fills the row's height as before. The label is not announced: the icon's content
 *   description already names the action.
 * @param content Composable content displayed inside the action button (typically an [Icon]).
 */
@Stable
class SwipeAction(
    val background: Color,
    val onTriggered: () -> Unit,
    val resetOnDismiss: Boolean = false,
    val label: String? = null,
    val content: @Composable () -> Unit,
)

// Revealed and Dismissed are the trailing side (content moved left); the Start pair is the leading
// side (content moved right), present only when the row has leading actions.
private enum class SwipeState { Settled, Revealed, Dismissed, RevealedStart, DismissedStart }

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
                    painter = painterResource(Res.drawable.ic_delete),
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
 *
 * [leadingActions] mirror that on the other side: swiping right reveals them, the **last** one
 * sits leftmost, grows on the swipe and fires on a full swipe, honouring its own
 * [SwipeAction.resetOnDismiss]. A full leading swipe also plays the gesture haptic. Without
 * leading actions the row swipes left only, exactly as before.
 */
@Composable
fun SwipeActionRow(
    actions: List<SwipeAction>,
    modifier: Modifier = Modifier,
    stateKey: Any? = null,
    initiallyRevealed: Boolean = false,
    revealGroup: SwipeRevealGroup? = null,
    leadingActions: List<SwipeAction> = emptyList(),
    content: @Composable () -> Unit,
) {
    if (actions.isEmpty() && leadingActions.isEmpty()) {
        Box(modifier) { content() }
        return
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val actionPadding = CodeTheme.dimens.inset
    val minActionSize = CodeTheme.dimens.staticGrid.x10

    // Each action slot = its width + padding on each side, where the width is the circle's unless
    // a label is wider. Total reveal = N slots + one extra padding on the outer edge.
    val labelStyle = CodeTheme.typography.caption
    val textMeasurer = rememberTextMeasurer()
    val trailingLabelWidths = actions.map { action ->
        action.label?.let { textMeasurer.measure(it, labelStyle, maxLines = 1).size.width }
    }
    val leadingLabelWidths = leadingActions.map { action ->
        action.label?.let { textMeasurer.measure(it, labelStyle, maxLines = 1).size.width }
    }
    val totalRevealWidthPx = with(density) {
        revealWidthPx(trailingLabelWidths, minActionSize.toPx(), actionPadding.toPx())
    }

    val dismissAction = actions.lastOrNull()
    val currentDismissCallback by rememberUpdatedState(dismissAction?.onTriggered ?: {})
    val currentDismissResets by rememberUpdatedState(dismissAction?.resetOnDismiss ?: false)

    var pendingResetCallback by remember(stateKey) { mutableStateOf(false) }

    val leadingRevealWidthPx = with(density) {
        revealWidthPx(leadingLabelWidths, minActionSize.toPx(), actionPadding.toPx())
    }
    val leadingDismissAction = leadingActions.lastOrNull()
    val currentLeadingCallback by rememberUpdatedState(leadingDismissAction?.onTriggered ?: {})
    val currentLeadingResets by rememberUpdatedState(leadingDismissAction?.resetOnDismiss ?: false)
    var pendingLeadingReset by remember(stateKey) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val initialState = if (initiallyRevealed) SwipeState.Revealed else SwipeState.Settled
    val state = remember(stateKey) {
        AnchoredDraggableState(
            initialValue = initialState,
            confirmValueChange = { newValue ->
                if (newValue == SwipeState.Dismissed && currentDismissResets) {
                    pendingResetCallback = true
                    false // reject settle, system animates back
                } else if (newValue == SwipeState.DismissedStart && currentLeadingResets) {
                    pendingLeadingReset = true
                    false
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
            if (interaction is DragInteraction.Start) {
                pendingResetCallback = false
                pendingLeadingReset = false
            }
        }
    }
    // The leading side's version of the reset above. The threshold is confirmed while the finger is
    // still down, so the callback and haptic fire then; sending the row home at that point would
    // cancel the drag and leave the settle that follows on RevealedStart. Instead the row goes home
    // once nothing is dragging or animating it, which also covers a swipe begun from the revealed row.
    LaunchedEffect(pendingLeadingReset) {
        if (pendingLeadingReset) {
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            currentLeadingCallback()
        }
    }
    val isDragged by dragInteractions.collectIsDraggedAsState()
    // The drag's own settle can start just after this does and interrupt it, landing on
    // RevealedStart, so each attempt re-reads the state and waits for that settle to finish.
    LaunchedEffect(state) {
        while (true) {
            snapshotFlow {
                pendingLeadingReset && !isDragged && !state.isAnimationRunning && state.offset > 0f
            }.first { it }
            try {
                state.animateTo(SwipeState.Settled)
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
            }
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
        if (state.currentValue == SwipeState.DismissedStart) {
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            currentLeadingCallback()
        }
    }

    val hasTrailing = actions.isNotEmpty()
    val hasLeading = leadingActions.isNotEmpty()
    LaunchedEffect(rowWidthPx, totalRevealWidthPx, leadingRevealWidthPx, hasTrailing, hasLeading) {
        if (rowWidthPx > 0f) {
            state.updateAnchors(DraggableAnchors {
                SwipeState.Settled at 0f
                if (hasTrailing) {
                    SwipeState.Revealed at -totalRevealWidthPx
                    SwipeState.Dismissed at -rowWidthPx
                }
                if (hasLeading) {
                    SwipeState.RevealedStart at leadingRevealWidthPx
                    SwipeState.DismissedStart at rowWidthPx
                }
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
                    // The inner actions fade as a full swipe commits; the outermost grows instead.
                    val fade = if (index < actions.lastIndex) {
                        Modifier.graphicsLayer {
                            alpha = innerActionAlpha(-state.offset, totalRevealWidthPx, rowWidthPx)
                        }
                    } else {
                        Modifier
                    }
                    SwipeActionSlot(action, labelStyle, minActionSize, fade) {
                        action.onTriggered()
                        scope.launch { state.animateTo(SwipeState.Settled) }
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

            // Non-dismiss actions each occupy a fixed slot: padding + their width (the circle's,
            // or the label's when wider). The dismiss action (last) gets the remaining width and
            // grows on full swipe.
            val slots = trailingLabelWidths.map { slotWidthPx(it, minPx) }
            val nonDismissTotal = slots.dropLast(1).sumOf { it + padPx }
            val dismissAvailable = (absOffset - padPx - nonDismissTotal).coerceAtLeast(0f)
            val dismissW = (dismissAvailable.toInt() - padPx).coerceAtLeast(slots.lastOrNull() ?: minPx)

            val placeables = measurables.mapIndexed { index, measurable ->
                val w = if (index == n - 1) dismissW else slots[index]
                // A labelled action lays out its circle and title in the row's full height.
                val h = if (trailingLabelWidths[index] != null) constraints.maxHeight else actionH
                measurable.measure(Constraints.fixed(w, h))
            }

            layout(constraints.maxWidth, constraints.maxHeight) {
                // Hidden, and so not tappable, while the leading side is open.
                if (state.offset > 0f || n == 0) return@layout
                var x = constraints.maxWidth - padPx

                // Dismiss action (rightmost)
                x -= placeables.last().width
                placeables.last().place(x, (constraints.maxHeight - placeables.last().height) / 2)

                // Remaining actions, right to left
                for (i in n - 2 downTo 0) {
                    x -= padPx + placeables[i].width
                    placeables[i].place(x, (constraints.maxHeight - placeables[i].height) / 2)
                }
            }
        }

        if (hasLeading) {
            Layout(
                content = {
                    leadingActions.forEachIndexed { index, action ->
                        val fade = if (index < leadingActions.lastIndex) {
                            Modifier.graphicsLayer {
                                alpha = innerActionAlpha(state.offset, leadingRevealWidthPx, rowWidthPx)
                            }
                        } else {
                            Modifier
                        }
                        SwipeActionSlot(action, labelStyle, minActionSize, fade) {
                            action.onTriggered()
                            scope.launch { state.animateTo(SwipeState.Settled) }
                        }
                    }
                },
                modifier = Modifier.matchParentSize(),
            ) { measurables, constraints ->
                // The trailing measure policy, mirrored: the last action is outermost (leftmost).
                val offset = state.offset.coerceAtLeast(0f)
                val padPx = actionPaddingDp.roundToPx()
                val minPx = minActionSize.roundToPx()
                val n = measurables.size
                val actionH = (constraints.maxHeight - padPx * 2).coerceAtLeast(minPx)
                val slots = leadingLabelWidths.map { slotWidthPx(it, minPx) }
                val outerAvailable = (offset - padPx - slots.dropLast(1).sumOf { it + padPx }).coerceAtLeast(0f)
                val outerW = (outerAvailable.toInt() - padPx).coerceAtLeast(slots.last())
                val placeables = measurables.mapIndexed { index, measurable ->
                    val h = if (leadingLabelWidths[index] != null) constraints.maxHeight else actionH
                    measurable.measure(Constraints.fixed(if (index == n - 1) outerW else slots[index], h))
                }
                layout(constraints.maxWidth, constraints.maxHeight) {
                    if (offset <= 0f) return@layout
                    var x = padPx
                    placeables.last().place(x, (constraints.maxHeight - placeables.last().height) / 2)
                    x += placeables.last().width
                    for (i in n - 2 downTo 0) {
                        x += padPx
                        placeables[i].place(x, (constraints.maxHeight - placeables[i].height) / 2)
                        x += placeables[i].width
                    }
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

/**
 * A labelled action draws its background as a pill: the circle's height and [PillWidthRatio] times
 * its width, matching the titled swipe actions iOS draws.
 */
private const val PillWidthRatio = 1.4f

private fun pillWidthPx(circlePx: Float): Float = circlePx * PillWidthRatio

/**
 * An action's slot width: the circle's when unlabelled; when labelled, the pill's, or the label's
 * when the label is wider.
 */
private fun slotWidthPx(labelWidthPx: Int?, circlePx: Int): Int =
    if (labelWidthPx == null) circlePx else maxOf(pillWidthPx(circlePx.toFloat()).roundToInt(), labelWidthPx)

/**
 * The alpha of an action that is not the outermost, for a side open by [openPx]: 1 up to the
 * reveal width, falling linearly to 0 at the point past which releasing commits the full swipe
 * (halfway from the reveal to the row's width, the drag's default positional threshold).
 */
internal fun innerActionAlpha(openPx: Float, revealPx: Float, rowWidthPx: Float): Float {
    val span = (rowWidthPx - revealPx) / 2f
    if (span <= 0f) return 1f
    return 1f - ((openPx - revealPx) / span).coerceIn(0f, 1f)
}

/** The reveal width for [labelWidths] (one per action, null when unlabelled), as a float like the anchors. */
private fun revealWidthPx(labelWidths: List<Int?>, circlePx: Float, padPx: Float): Float =
    labelWidths.sumOf { slotWidthPx(it, circlePx.roundToInt()).toDouble() }.toFloat() +
        padPx * (labelWidths.size + 1)

/**
 * One action behind the row. Unlabelled, it is the circle (or, stretched by a full swipe, the pill)
 * the row measures it to. Labelled, it is a pill the circle's height and [PillWidthRatio] times as
 * wide, with the label centred under it and the pair centred in the row's height; when the
 * outermost action is stretched by a full swipe the pill widens by the same amount and the label
 * stays under it. The whole slot, label included, takes the tap; the ripple stays on the pill.
 */
@Composable
private fun SwipeActionSlot(
    action: SwipeAction,
    labelStyle: TextStyle,
    circleSize: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val label = action.label
    if (label == null) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .background(action.background)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            action.content()
        }
        return
    }
    val interactions = remember { MutableInteractionSource() }
    val gap = CodeTheme.dimens.grid.x1
    Layout(
        content = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(action.background)
                    .indication(interactions, ripple()),
                contentAlignment = Alignment.Center,
            ) {
                action.content()
            }
            Text(
                text = label,
                style = labelStyle,
                color = CodeTheme.colors.textSecondary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clearAndSetSemantics {},
            )
        },
        modifier = modifier.clickable(interactionSource = interactions, indication = null, onClick = onClick),
    ) { measurables, constraints ->
        val circlePx = circleSize.roundToPx()
        val gapPx = gap.roundToPx()
        val text = measurables[1].measure(Constraints())
        val slot = slotWidthPx(text.width, circlePx)
        val pillPx = pillWidthPx(circlePx.toFloat()).roundToInt()
        val circle = measurables[0].measure(
            Constraints.fixed(pillPx + (constraints.maxWidth - slot).coerceAtLeast(0), circlePx)
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            val top = (constraints.maxHeight - circle.height - gapPx - text.height) / 2
            circle.place((constraints.maxWidth - circle.width) / 2, top)
            text.place((constraints.maxWidth - text.width) / 2, top + circle.height + gapPx)
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
