package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.flipcash.services.models.chat.MessageContent
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall
import com.getcode.ui.core.addIf

internal enum class BubblePosition { Solo, First, Middle, Last }

@Composable
internal fun ContentBubble(
    item: ChatListItem.ContentBubble,
    position: BubblePosition,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (item.isFromSelf) Arrangement.End else Arrangement.Start,
    ) {
        when (val content = item.content) {
            is MessageContent.Text -> TextBubble(
                text = content.text,
                isFromSelf = item.isFromSelf,
                position = position,
            )
            is MessageContent.Cash -> Unit // TODO
        }
    }
}

@Composable
private fun TextBubble(
    text: String,
    isFromSelf: Boolean,
    position: BubblePosition,
) {
    val bubble = if (isFromSelf) {
        CodeTheme.colors.chat.outgoingBubble
    } else {
        CodeTheme.colors.chat.incomingBubble
    }
    val shape = bubbleShape(position, isFromSelf)
    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(shape)
            .addIf(bubble.hasBorder) {
                Modifier.border(1.dp, bubble.border, shape)
            }
            .background(bubble.background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textMain,
        )
    }
}

@Composable
private fun bubbleShape(position: BubblePosition, isFromSelf: Boolean): Shape {
    val l = CodeTheme.shapes.medium.topStart
    val s = CodeTheme.shapes.extraSmall.topStart
    // RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
    return when (position) {
        BubblePosition.Solo -> RoundedCornerShape(l)
        BubblePosition.First -> if (isFromSelf) {
            // Top of group, outgoing: bottom-end connects to next below
            RoundedCornerShape(topStart = l, topEnd = l, bottomEnd = s, bottomStart = l)
        } else {
            RoundedCornerShape(topStart = l, topEnd = l, bottomEnd = l, bottomStart = s)
        }
        BubblePosition.Middle -> if (isFromSelf) {
            RoundedCornerShape(topStart = l, topEnd = s, bottomEnd = s, bottomStart = l)
        } else {
            RoundedCornerShape(topStart = s, topEnd = l, bottomEnd = l, bottomStart = s)
        }
        BubblePosition.Last -> if (isFromSelf) {
            // Bottom of group, outgoing: top-end connects to item above
            RoundedCornerShape(topStart = l, topEnd = s, bottomEnd = l, bottomStart = l)
        } else {
            RoundedCornerShape(topStart = s, topEnd = l, bottomEnd = l, bottomStart = l)
        }
    }
}

internal fun bubblePositionOf(
    index: Int,
    item: ChatListItem.ContentBubble,
    messages: LazyPagingItems<ChatListItem>,
    config: SeparatorConfig,
): BubblePosition {
    // index+1 = visually above (older), index-1 = visually below (newer)
    val above = if (index + 1 < messages.itemCount) {
        messages.peek(index + 1) as? ChatListItem.ContentBubble
    } else null
    val below = if (index > 0) {
        messages.peek(index - 1) as? ChatListItem.ContentBubble
    } else null

    val groupedAbove = above != null &&
            above.isFromSelf == item.isFromSelf &&
            config.isGrouped(item.timestamp, above.timestamp)

    val groupedBelow = below != null &&
            below.isFromSelf == item.isFromSelf &&
            config.isGrouped(item.timestamp, below.timestamp)

    return when {
        groupedAbove && groupedBelow -> BubblePosition.Middle
        groupedAbove -> BubblePosition.Last   // bottom of visual group
        groupedBelow -> BubblePosition.First  // top of visual group
        else -> BubblePosition.Solo
    }
}

@Composable
internal fun DateSeparatorRow(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = CodeTheme.dimens.grid.x2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = CodeTheme.typography.caption,
            color = CodeTheme.colors.textSecondary,
        )
    }
}
