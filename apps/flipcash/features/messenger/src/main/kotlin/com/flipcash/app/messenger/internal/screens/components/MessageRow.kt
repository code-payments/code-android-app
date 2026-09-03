package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.flipcash.app.messenger.internal.screens.ChatAnimations
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.shared.chat.models.ChatAction
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.LocalChatActionHandler
import com.flipcash.shared.chat.models.ReceiptStatus
import com.flipcash.shared.chat.models.SeparatorConfig
import com.flipcash.shared.chat.ui.ContentBubble
import com.flipcash.shared.chat.ui.bubblePositionOf
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.addIf
import com.getcode.ui.utils.rememberKeyboardController
import com.getcode.util.vibration.LocalVibrator

/**
 * One row of the transcript: a date separator, or a bubble with the receipt label that can sit under
 * it.
 *
 * The row owns what is a function of itself — its insertion animation, its gestures, its spacing to
 * the row below — and takes the rest as flags, because they are decided across the whole list:
 * [selecting] is true for every row while the backdrop is up, [focused] for the single row it leaves
 * sharp, and [animateInsertion] is granted once per message and never again.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageRow(
    index: Int,
    item: ChatListItem,
    messages: LazyPagingItems<ChatListItem>,
    separatorConfig: SeparatorConfig,
    otherReadPointer: MessagePointer?,
    selecting: Boolean,
    focused: Boolean,
    animateInsertion: Boolean,
) {
    val onAction = LocalChatActionHandler.current
    val vibrator = LocalVibrator.current
    val keyboard = rememberKeyboardController()
    val bottomSpacing = bottomSpacingFor(index, item, messages, separatorConfig)

    val isOutgoing = (item as? ChatListItem.ContentBubble)?.isFromSelf ?: false

    // Message insertion animation — scale from 0.95 + opacity with edge anchor.
    var appeared by remember(item.itemKey) { mutableStateOf(!animateInsertion) }
    LaunchedEffect(Unit) { if (!appeared) appeared = true }
    val insertionAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = ChatAnimations.insertion,
        label = "insertAlpha",
    )
    val insertionScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.95f,
        animationSpec = ChatAnimations.insertion,
        label = "insertScale",
    )

    val insertionModifier = Modifier.graphicsLayer {
        alpha = insertionAlpha
        scaleX = insertionScale
        scaleY = insertionScale
        transformOrigin = if (isOutgoing) {
            TransformOrigin(1f, 0.5f) // anchor trailing
        } else {
            TransformOrigin(0f, 0.5f) // anchor leading
        }
    }

    val bubble = item as? ChatListItem.ContentBubble
    val interactionSource = remember { MutableInteractionSource() }

    val dimAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0.4f,
        label = "messageDim",
    )
    val dimBlur by animateDpAsState(
        targetValue = if (focused) 0.dp else 8.dp,
        label = "messageBlur",
    )

    // The row answers the finger before the long-press resolves: it dips while held,
    // then springs up and stays lifted for as long as it is the selected message.
    val pressed by interactionSource.collectIsPressedAsState()
    val lift by animateFloatAsState(
        targetValue = when {
            selecting && focused -> 1.04f
            pressed && bubble?.isSelectable == true -> 0.97f
            else -> 1f
        },
        animationSpec = ChatAnimations.lift,
        label = "messageLift",
    )

    Box(
        modifier = Modifier
            .padding(bottom = bottomSpacing)
            // Unbounded: the rectangle treatment would clip the blur at the row's own
            // edges and leave a hard seam between neighbouring rows.
            .blur(dimBlur, BlurredEdgeTreatment.Unbounded)
            .graphicsLayer {
                alpha = dimAlpha
                scaleX = lift
                scaleY = lift
                // Anchored to the bubble's own edge, as the insertion animation is, so
                // the lift grows the bubble in place instead of sliding it inward.
                transformOrigin = if (isOutgoing) {
                    TransformOrigin(1f, 0.5f)
                } else {
                    TransformOrigin(0f, 0.5f)
                }
            }
            // No row gestures while the backdrop is up: the rows are behind it, and a
            // press there would move the selection out from under the message the bar —
            // or the composer — is already acting on.
            .addIf(bubble != null && !selecting) {
                // Long-press is the whole row's gesture, not the bubble's: a
                // bubble-sized target is harder to hit, and the top bar is what reports
                // the selection, so nothing about the row has to change.
                Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onLongClick = bubble?.takeIf { it.isSelectable }?.let { target ->
                        {
                            vibrator.tick()
                            onAction(ChatAction.ToggleSelection(target))
                        }
                    },
                    // Only reachable with the backdrop down, so the tap has nothing to
                    // dismiss but the keyboard.
                    onClick = { keyboard.hide() },
                )
            },
    ) {
        when (item) {
            is ChatListItem.DateSeparator -> Box(insertionModifier) {
                DateSeparatorRow(item.timestamp)
            }

            is ChatListItem.ContentBubble -> {
                val effectiveStatus = effectiveReceiptStatus(item, otherReadPointer)
                // Track whether this item was ever seen as SENDING so we
                // can animate the receipt label entrance on the
                // SENDING→SENT transition. This remember persists across
                // recompositions of the same item (keyed by LazyColumn),
                // surviving the status change that gates the label.
                var wasSending by remember { mutableStateOf(false) }
                if (item.receiptStatus == ReceiptStatus.SENDING) {
                    wasSending = true
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (item.isFromSelf) Alignment.End else Alignment.Start,
                ) {
                    Box(insertionModifier) {
                        ContentBubble(
                            item = item,
                            // The bubble's own targets go with the row's: a cash
                            // bubble behind the backdrop would otherwise open token
                            // info from under the bar.
                            interactive = !selecting,
                            position = bubblePositionOf(
                                index,
                                item,
                                messages,
                                separatorConfig
                            ),
                        )
                    }
                    val showReceipt =
                        shouldShowReceiptLabel(index, item, messages, otherReadPointer)
                    AnimatedVisibility(
                        visible = showReceipt && effectiveStatus != null,
                        enter = EnterTransition.None,
                        exit = ChatAnimations.receiptExit,
                    ) {
                        if (effectiveStatus != null) {
                            ReceiptLabel(
                                status = effectiveStatus,
                                readPointer = otherReadPointer,
                                animateEntrance = wasSending,
                                onRetryFailed = if (effectiveStatus == ReceiptStatus.FAILED) {
                                    { onAction(ChatAction.RetryMessage(item)) }
                                } else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun bottomSpacingFor(
    index: Int,
    item: ChatListItem,
    messages: LazyPagingItems<ChatListItem>,
    config: SeparatorConfig,
): Dp {
    val tight = CodeTheme.dimens.grid.x1
    val normal = CodeTheme.dimens.grid.x2
    val wide = CodeTheme.dimens.grid.x3
    // index-1 is the item below (newer) in reverseLayout
    val itemBelow = (if (index > 0) messages.peek(index - 1) else null) ?: return tight

    // Separator adjacent → normal gap
    if (item is ChatListItem.DateSeparator || itemBelow is ChatListItem.DateSeparator) {
        return normal
    }

    val current = item as? ChatListItem.ContentBubble ?: return tight
    val below = itemBelow as? ChatListItem.ContentBubble ?: return tight

    return when {
        // Different sender → wide
        current.isFromSelf != below.isFromSelf -> wide
        // Same sender, outside grouping window → normal
        !config.isGrouped(current.timestamp, below.timestamp) -> normal
        // Same sender, close together → tight
        else -> tight
    }
}
