package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.flipcash.app.messenger.internal.screens.ChatAnimations
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.shared.chat.MessageCapability
import com.flipcash.shared.chat.models.ChatAction
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.LocalChatActionHandler
import com.flipcash.shared.chat.models.ReceiptStatus
import com.flipcash.shared.chat.models.SeparatorConfig
import com.flipcash.features.messenger.R
import com.flipcash.shared.chat.ui.ContentBubble
import com.flipcash.shared.chat.ui.rendersBareEmoji
import com.flipcash.shared.common.ui.ContactAvatar
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
 * sharp, [attention] carries the flash the list points at a jumped-to message, and
 * [animateInsertion] is granted once per message and never again, and [showsSenderGutter] says
 * whether incoming rows reserve the avatar column — true for a group, where every message is
 * attributed, and false for a DM, where none is.
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
    showsSenderGutter: Boolean,
    attention: () -> Float = { 0f },
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

    // Hoisted because two targets report the same gesture: the row, and any bubble that installs a
    // tap target of its own and would otherwise consume the press before the row sees it.
    val select = bubble?.takeIf { it.isSelectable }?.let { target ->
        {
            vibrator.tick()
            onAction(ChatAction.ToggleSelection(target))
        }
    }

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

    val swipe = rememberSwipeToReply(
        enabled = bubble != null &&
            !selecting &&
            MessageCapability.Reply in bubble.capabilities,
        // The avatar column is the target for the person, not for the message: a drag that starts
        // on someone's picture is reaching for them, and the picture is small enough that arming a
        // reply from it would mostly catch fingers that missed the tap.
        senderGutter = if (showsSenderGutter && bubble?.isFromSelf == false) {
            CodeTheme.dimens.staticGrid.x6
        } else {
            0.dp
        },
        onReply = { bubble?.let { onAction(ChatAction.ReplyTo(it)) } },
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
                // Long-press is the whole row's gesture, not just the bubble's: a
                // bubble-sized target is harder to hit, and the top bar is what reports
                // the selection, so nothing about the row has to change. A bubble that
                // takes the press for its own tap target reports the same gesture back.
                Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onLongClick = select,
                    // Only reachable with the backdrop down, so the tap has nothing to
                    // dismiss but the keyboard.
                    onClick = { keyboard.hide() },
                )
            }
            // No swipe with the backdrop up either, and for the same reason: the bar is already
            // acting on a message.
            .then(swipe.modifier),
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
                // Bound to a local: `sender` is a property of another module's public API, so
                // Kotlin will not smart-cast it to non-null inside the branches below.
                val sender = item.sender
                val runStart = startsSenderRun(
                    current = item,
                    // Bounded: `peek` throws off the end of the snapshot, and the oldest loaded
                    // message has no item above it — a one-message transcript crashed here.
                    older = if (index + 1 < messages.itemCount) messages.peek(index + 1) else null,
                )
                val runEnd = endsSenderRun(
                    current = item,
                    // index - 1 is the row drawn below — the newer message, under `reverseLayout`.
                    newer = if (index > 0) messages.peek(index - 1) else null,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
                ) {
                    // The gutter is reserved on every incoming row of a group, not only the
                    // labelled ones, so bubbles line up on the same left edge instead of stepping
                    // in and out as runs start. Keyed off the transcript rather than off this row's
                    // `sender`, which is also null while a member's profile is still resolving — a
                    // row that reserved no gutter would sit out at the inset and then jump inward
                    // when the name arrived.
                    if (showsSenderGutter && !item.isFromSelf) {
                        Box(modifier = Modifier.requiredSize(CodeTheme.dimens.staticGrid.x6)) {
                            if (sender != null && runEnd) {
                                ContactAvatar(
                                    image = sender.picture,
                                    displayName = sender.displayName,
                                    access = BlobAccessContext.profile(sender.userId),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        // The picture is the only handle the transcript gives on
                                        // the person behind a bubble. Inert while the backdrop is
                                        // up, like every other target on the row.
                                        .addIf(!selecting) {
                                            Modifier.clickable {
                                                onAction(ChatAction.ViewMemberProfile(sender.userId))
                                            }
                                        },
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = if (item.isFromSelf) Alignment.End else Alignment.Start,
                    ) {
                        if (sender != null && runStart) {
                            Text(
                                modifier = Modifier.padding(
                                    start = CodeTheme.dimens.grid.x1,
                                    bottom = CodeTheme.dimens.grid.x1,
                                ),
                                text = sender.displayName,
                                style = CodeTheme.typography.textSmall,
                                color = CodeTheme.colors.textSecondary,
                            )
                        }
                        Box(insertionModifier) {
                            ContentBubble(
                                item = item,
                                // The bubble's own targets go with the row's: a cash
                                // bubble behind the backdrop would otherwise open token
                                // info from under the bar.
                                interactive = !selecting,
                                // A bubble with a tap target of its own consumes the press,
                                // so the row's long-press never reaches it. Handing it the
                                // same gesture is what makes a cash bubble selectable.
                                onLongClick = select,
                                position = bubblePositionOf(
                                    index,
                                    item,
                                    messages,
                                    separatorConfig
                                ),
                                attention = attention,
                            )
                        }
                        val showReceipt =
                            shouldShowReceiptLabel(index, item, messages, otherReadPointer)
                        // An emoji-only message has no bubble, so its "Edited" marker has nowhere
                        // to sit inside the message and comes out here instead — on the same line
                        // as the receipt, and ahead of it, so the two read as one trailing note.
                        // It stands alone on rows that carry no receipt, which is every incoming
                        // one.
                        Row(verticalAlignment = Alignment.Top) {
                            if (item.isEdited && item.rendersBareEmoji()) {
                                Text(
                                    modifier = Modifier.padding(
                                        top = CodeTheme.dimens.grid.x1,
                                        end = CodeTheme.dimens.grid.x2,
                                    ),
                                    text = stringResource(R.string.label_edited),
                                    style = CodeTheme.typography.caption,
                                    color = CodeTheme.colors.textSecondary,
                                )
                            }
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

        SwipeToReplyAffordance(
            progress = swipe::progress,
            pullBackPx = swipe::affordanceTranslationPx,
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }
}

/**
 * The mark the swipe uncovers: a circle in the gutter the row is opening, growing and fading in as
 * the drag approaches the distance that fires the reply.
 *
 * Parked at a fixed offset rather than an animated one. The row it sits in is already translated by
 * the drag, so the two move together, and the constant is iOS's per-frame centre
 * (`affordanceInset + radius - maxTranslation`) restated as a leading edge, which drops the radius:
 * the circle lands 20dp from the row's leading edge at full travel, and off that edge — clipped by
 * the list, and transparent besides — at rest.
 *
 * [pullBackPx] is what stops it there. The row runs on past full travel under a hard swipe, and
 * riding that would carry the circle across the space the bubble is opening and out the other side;
 * cancelling the overshoot leaves it at the 20dp it was drawn for while the row keeps moving.
 */
@Composable
private fun SwipeToReplyAffordance(
    progress: () -> Float,
    pullBackPx: () -> Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .offset(x = AFFORDANCE_INSET - SWIPE_MAX_TRANSLATION)
            .size(AFFORDANCE_SIZE)
            .graphicsLayer {
                val fraction = progress()
                translationX = pullBackPx()
                alpha = fraction
                // Never from nothing: the circle is already most of its size when it starts to
                // show, so it reads as arriving rather than as inflating.
                scaleX = 0.6f + 0.4f * fraction
                scaleY = 0.6f + 0.4f * fraction
            }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(AFFORDANCE_SIZE - AFFORDANCE_ICON_INSET * 2),
            imageVector = Icons.AutoMirrored.Filled.Reply,
            // Decorative: the gesture it marks is already reachable from the selection bar, which
            // is what a screen reader drives the reply from.
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.75f),
        )
    }
}

private val AFFORDANCE_SIZE = 32.dp
private val AFFORDANCE_INSET = 20.dp
private val AFFORDANCE_ICON_INSET = 8.dp
private val SWIPE_MAX_TRANSLATION = 64.dp

@Composable
private fun bottomSpacingFor(
    index: Int,
    item: ChatListItem,
    messages: LazyPagingItems<ChatListItem>,
    config: SeparatorConfig,
): Dp {
    // index-1 is the item below (newer) in reverseLayout
    val gap = rowGapBelow(item, if (index > 0) messages.peek(index - 1) else null, config)
    return when (gap) {
        RowGap.Tight -> CodeTheme.dimens.grid.x1
        RowGap.Normal -> CodeTheme.dimens.grid.x2
        RowGap.Wide -> CodeTheme.dimens.grid.x3
    }
}

/** The three gaps the transcript puts between rows, resolved to `grid.x1`/`x2`/`x3`. */
internal enum class RowGap { Tight, Normal, Wide }

/**
 * The gap under [item], where [below] is the row drawn beneath it — the newer message, `peek(index
 * - 1)` under `reverseLayout`.
 *
 * A change of author opens the widest gap, because that is where the next run's name and picture
 * go. `isFromSelf` alone used to stand in for the author, which is right in a DM and wrong in a
 * group: two members' messages are both incoming, so a whole conversation between them ran at the
 * tight same-sender gap.
 *
 * Pure so it can be tested without a `PagingData`, like [startsSenderRun]: the messenger module has
 * no `paging-testing` dependency, and the peek belongs to the caller.
 */
internal fun rowGapBelow(item: ChatListItem, below: ChatListItem?, config: SeparatorConfig): RowGap {
    below ?: return RowGap.Tight

    // Separator adjacent → normal gap
    if (item is ChatListItem.DateSeparator || below is ChatListItem.DateSeparator) {
        return RowGap.Normal
    }

    val current = item as? ChatListItem.ContentBubble ?: return RowGap.Tight
    val newer = below as? ChatListItem.ContentBubble ?: return RowGap.Tight

    return when {
        !current.isSameAuthorAs(newer) -> RowGap.Wide
        // Same sender, outside grouping window → normal
        !config.isGrouped(current.timestamp, newer.timestamp) -> RowGap.Normal
        // Same sender, close together → tight
        else -> RowGap.Tight
    }
}

/**
 * Whether [current] is the bubble that wears its sender's name — the top of a run.
 *
 * `older` is the item drawn *above* [current] — under `reverseLayout` that is `peek(index + 1)`,
 * the mirror of [bottomSpacingFor]'s `index - 1`. A run is named at its top, so the bubble that
 * starts one is the one whose upper neighbour came from someone else.
 *
 * Keyed off `authorId` rather than off the resolved `sender`, so the runs hold their shape from the
 * first frame: a group's profile map arrives after the first page, and until it does every member's
 * bubble has a null `sender`.
 *
 * Pure so it can be tested without a `PagingData`: the messenger module has no `paging-testing`
 * dependency, and the peek belongs to the caller anyway.
 */
internal fun startsSenderRun(current: ChatListItem.ContentBubble, older: ChatListItem?): Boolean {
    val author = current.authorId ?: return false
    val olderBubble = older as? ChatListItem.ContentBubble ?: return true
    return olderBubble.authorId != author
}

/**
 * Whether [current] is the bubble that wears its sender's picture — the bottom of a run.
 *
 * The picture sits on the run's last message, next to the newest thing that person said, while the
 * name stays on its first: the two mark the run's ends rather than stacking on one bubble.
 *
 * `newer` is the item drawn *below* [current] — `peek(index - 1)` under `reverseLayout`, the mirror
 * of [startsSenderRun]'s `older`.
 */
internal fun endsSenderRun(current: ChatListItem.ContentBubble, newer: ChatListItem?): Boolean {
    val author = current.authorId ?: return false
    val newerBubble = newer as? ChatListItem.ContentBubble ?: return true
    return newerBubble.authorId != author
}
