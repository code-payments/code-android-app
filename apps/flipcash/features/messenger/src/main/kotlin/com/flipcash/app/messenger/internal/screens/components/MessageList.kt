package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.flipcash.services.models.chat.MessageContent
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.drawWithGradient
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.utils.sheetResignmentBehavior
import com.getcode.util.toLocalDate
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
) {
    val listAlpha by animateFloatAsState(
        targetValue = if (messages.itemCount > 0) 1f else 0f,
        label = "messageListAlpha",
    )

    val listState = rememberLazyListState()
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

            Box(modifier = Modifier.padding(bottom = bottomSpacing)) {
                when (item) {
                    is ChatListItem.DateSeparator -> DateSeparatorRow(item.label)
                    is ChatListItem.ContentBubble -> ContentBubble(
                        item = item,
                        position = bubblePositionOf(index, item, messages, separatorConfig),
                    )
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