package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.flipcash.services.models.chat.MessageContent
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.ui.utils.sheetResignmentBehavior
import com.getcode.util.toLocalDate
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapNotNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

sealed interface SeparatorConfig {
    val groupingWindow: Duration

    fun shouldSeparate(before: Instant, after: Instant): Boolean
    fun isGrouped(a: Instant, b: Instant): Boolean =
        (a - b).absoluteValue <= groupingWindow

    data object DayOnly : SeparatorConfig {
        override val groupingWindow: Duration = 60.seconds
        override fun shouldSeparate(before: Instant, after: Instant): Boolean =
            before.toLocalDate() != after.toLocalDate()
    }

    data class TimeGap(
        val gap: Duration = 3.hours,
        override val groupingWindow: Duration = 60.seconds,
    ) : SeparatorConfig {
        override fun shouldSeparate(before: Instant, after: Instant): Boolean =
            before.toLocalDate() != after.toLocalDate()
                    || (before - after).absoluteValue > gap
    }
}

internal enum class ReceiptStatus { SENDING, SENT, READ, FAILED }

internal sealed interface ChatListItem {
    val itemKey: Any

    data class DateSeparator(val label: String) : ChatListItem {
        override val itemKey: Any = "sep-$label"
    }

    data class ContentBubble(
        val messageId: Long,
        val contentIndex: Int,
        val content: MessageContent,
        val isFromSelf: Boolean,
        val timestamp: Instant,
        val receiptStatus: ReceiptStatus? = null,
    ) : ChatListItem {
        override val itemKey: Any = "$messageId-$contentIndex"
    }
}


@Composable
internal fun MessageList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    messages: LazyPagingItems<ChatListItem>,
    separatorConfig: SeparatorConfig,
    otherReadPointer: Long = 0L,
    onAdvanceReadPointer: ((Long) -> Unit)? = null,
) {
    val listAlpha by animateFloatAsState(
        targetValue = if (messages.itemCount > 0) 1f else 0f,
        label = "messageListAlpha",
    )

    val listState = rememberLazyListState()

    if (onAdvanceReadPointer != null) {
        HandleMessageReads(listState, messages, onAdvanceReadPointer)
    }

    LazyColumn(
        modifier = modifier
            .alpha(listAlpha)
            .sheetResignmentBehavior(listState),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(
            top = CodeTheme.dimens.grid.x2 + contentPadding.calculateTopPadding(),
            bottom = CodeTheme.dimens.grid.x2 + contentPadding.calculateBottomPadding(),
            start = CodeTheme.dimens.inset,
            end = CodeTheme.dimens.inset,
        ),
        verticalArrangement = Arrangement.Top,
    ) {
        items(
            count = messages.itemCount,
            key = messages.itemKey { it.itemKey }
        ) { index ->
            val item = messages[index] ?: return@items
            val bottomSpacing = bottomSpacingFor(index, item, messages, separatorConfig)

            Box(
                modifier = Modifier
                    .padding(bottom = bottomSpacing)
                    .animateItem(placementSpec = null),
            ) {
                when (item) {
                    is ChatListItem.DateSeparator -> DateSeparatorRow(item.label)
                    is ChatListItem.ContentBubble -> {
                        val effectiveStatus = effectiveReceiptStatus(item, otherReadPointer)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (item.isFromSelf) Alignment.End else Alignment.Start,
                        ) {
                            ContentBubble(
                                item = item,
                                position = bubblePositionOf(index, item, messages, separatorConfig),
                            )
                            val showReceipt =
                                shouldShowReceiptLabel(index, item, messages, otherReadPointer)
                            if (showReceipt && effectiveStatus != null) {
                                ReceiptLabel(effectiveStatus)
                            }
                        }
                    }
                }
            }
        }
    }

    // opts out of the list maintaining
    // scroll position when adding elements before the first item
    // we are checking first visible item index to ensure
    // the list doesn't shift when viewing scroll back
    Snapshot.withoutReadObservation {
        if (listState.firstVisibleItemIndex == 0) {
            listState.requestScrollToItem(
                index = listState.firstVisibleItemIndex,
                scrollOffset = listState.firstVisibleItemScrollOffset
            )
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

@Composable
private fun HandleMessageReads(
    listState: LazyListState,
    messages: LazyPagingItems<ChatListItem>,
    onAdvanceReadPointer: (Long) -> Unit,
) {
    var lastAdvanced by remember { mutableLongStateOf(0L) }

    LaunchedEffect(listState, messages) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val count = messages.itemCount
            val visibleRange = layout.visibleItemsInfo
            if (visibleRange.isEmpty() || count == 0) return@snapshotFlow null

            var highestId = 0L
            for (info in visibleRange) {
                if (info.index !in 0 until count) continue
                val bubble = messages.peek(info.index) as? ChatListItem.ContentBubble ?: continue
                if (!bubble.isFromSelf && bubble.messageId > highestId) {
                    highestId = bubble.messageId
                }
            }
            if (highestId > 0L) highestId else null
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collectLatest { messageId ->
                if (messageId > lastAdvanced) {
                    lastAdvanced = messageId
                    onAdvanceReadPointer(messageId)
                }
            }
    }
}

private fun effectiveReceiptStatus(
    bubble: ChatListItem.ContentBubble,
    otherReadPointer: Long,
): ReceiptStatus? {
    val base = bubble.receiptStatus ?: return null
    if (base == ReceiptStatus.SENT && bubble.messageId in 1..otherReadPointer) {
        return ReceiptStatus.READ
    }
    return base
}

/**
 * Show a receipt label below a self-message only within the last 2 contiguous
 * self-message groups. In reverseLayout, index 0 is the newest message.
 *
 * Within a group, labels appear at status boundaries (SENT↔READ).
 * At group boundaries, labels are suppressed when the nearest self-group
 * below already shows the same status (avoids duplicate "Read" labels).
 */
private fun shouldShowReceiptLabel(
    index: Int,
    item: ChatListItem.ContentBubble,
    messages: LazyPagingItems<ChatListItem>,
    otherReadPointer: Long,
): Boolean {
    if (!item.isFromSelf) return false
    val status = effectiveReceiptStatus(item, otherReadPointer) ?: return false
    if (status != ReceiptStatus.SENT && status != ReceiptStatus.READ) return false

    // index - 1 is the item below (newer) in reverseLayout
    val below = if (index > 0) messages.peek(index - 1) else null
    val belowBubble = below as? ChatListItem.ContentBubble

    // Within a self-group: show at intra-group status boundaries only
    if (belowBubble != null && belowBubble.isFromSelf) {
        return effectiveReceiptStatus(belowBubble, otherReadPointer) != status
    }

    // At a group boundary — count which self-group this is.
    var selfGroups = 0
    var prevWasSelf = false
    for (i in 0 until index) {
        val peek = messages.peek(i) ?: break
        val bubble = peek as? ChatListItem.ContentBubble
        val isSelf = bubble != null && bubble.isFromSelf
        if (isSelf && !prevWasSelf) selfGroups++
        prevWasSelf = isSelf
    }
    if (!prevWasSelf) selfGroups++ // current item starts a new group
    if (selfGroups > 2) return false

    // Bottommost self-group always shows its label
    if (selfGroups == 1) return true

    // For group 2: show only if status differs from the nearest self-group below
    for (i in (index - 1) downTo 0) {
        val peek = messages.peek(i) ?: break
        val bubble = peek as? ChatListItem.ContentBubble ?: continue
        if (bubble.isFromSelf) return effectiveReceiptStatus(bubble, otherReadPointer) != status
    }
    return true
}

@Composable
private fun ReceiptLabel(status: ReceiptStatus, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = status,
        modifier = modifier.padding(top = CodeTheme.dimens.grid.x1),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "receiptStatus",
    ) { animatedStatus ->
        val text = when (animatedStatus) {
            ReceiptStatus.SENT -> "Delivered"
            ReceiptStatus.READ -> "Read"
            else -> return@AnimatedContent
        }
        Text(
            text = text,
            style = CodeTheme.typography.caption,
            color = CodeTheme.colors.textSecondary,
        )
    }
}