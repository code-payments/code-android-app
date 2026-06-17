package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.flipcash.app.core.ui.TokenIconWithName
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.flags.R
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.PriceWithFlag
import com.getcode.ui.core.addIf

internal enum class BubblePosition { Solo, First, Middle, Last }

private const val BUBBLE_MAX_WIDTH_FRACTION = 0.78f
private const val CASH_BUBBLE_MAX_WIDTH_FRACTION = 0.64f

@Composable
internal fun ContentBubble(
    item: ChatListItem.ContentBubble,
    position: BubblePosition,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val bubbleMaxWidth = when (item.content) {
            is MessageContent.Text -> maxWidth * BUBBLE_MAX_WIDTH_FRACTION
            is MessageContent.Cash -> maxWidth * CASH_BUBBLE_MAX_WIDTH_FRACTION
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (item.isFromSelf) Arrangement.End else Arrangement.Start,
        ) {
            when (val content = item.content) {
                is MessageContent.Text -> TextBubble(
                    text = content.text,
                    isFromSelf = item.isFromSelf,
                    position = position,
                    maxWidth = bubbleMaxWidth,
                )

                is MessageContent.Cash -> CashBubble(
                    amount = content.amount,
                    tokenName = content.tokenName,
                    tokenImageUrl = content.tokenImageUrl,
                    isFromSelf = item.isFromSelf,
                    position = position,
                    maxWidth = bubbleMaxWidth,
                )
            }
        }
    }
}

@Composable
private fun TextBubble(
    text: String,
    isFromSelf: Boolean,
    position: BubblePosition,
    maxWidth: Dp,
) {
    Bubble(isFromSelf, position, maxWidth) {
        SelectionContainer {
            Text(
                text = text,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textMain,
            )
        }
    }
}

@Composable
private fun CashBubble(
    amount: Fiat,
    tokenName: String,
    tokenImageUrl: String,
    isFromSelf: Boolean,
    position: BubblePosition,
    maxWidth: Dp,
) {
    Bubble(isFromSelf = isFromSelf, position = position, minWidth = maxWidth, maxWidth = maxWidth) {
        val exchange = LocalExchange.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (tokenName.isNotBlank()) {
                TokenIconWithName(
                    modifier = Modifier.align(Alignment.Start),
                    tokenName = tokenName,
                    tokenImage = tokenImageUrl,
                    imageSize = CodeTheme.dimens.staticGrid.x4,
                    spacing = CodeTheme.dimens.grid.x1,
                    textStyle = CodeTheme.typography.caption,
                    textColor = CodeTheme.colors.textSecondary,
                )
            }

            Column(
                modifier = Modifier
                    .padding(vertical = CodeTheme.dimens.grid.x5),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (isFromSelf) {
                        stringResource(R.string.subtitle_youSent)
                    } else {
                        stringResource(R.string.subtitle_youReceived)
                    },
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textSecondary,
                )
                PriceWithFlag(
                    amount = amount.formatted(),
                    currencyCode = amount.currencyCode.name,
                    iconSize = CodeTheme.dimens.staticGrid.x5,
                    flag = exchange.getFlagByCurrency(amount.currencyCode.name),
                    text = { text ->
                        Text(
                            text = text,
                            style = CodeTheme.typography.displayMedium,
                            color = CodeTheme.colors.textMain,
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun Bubble(
    isFromSelf: Boolean,
    position: BubblePosition,
    maxWidth: Dp,
    minWidth: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val bubble = if (isFromSelf) {
        CodeTheme.colors.chat.outgoingBubble
    } else {
        CodeTheme.colors.chat.incomingBubble
    }
    val shape = bubbleShape(position, isFromSelf)
    Box(
        modifier = Modifier
            .widthIn(min = minWidth, max = maxWidth)
            .clip(shape)
            .addIf(bubble.hasBorder) {
                Modifier.border(1.dp, bubble.border, shape)
            }
            .background(bubble.background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        content()
    }
}

@Composable
private fun bubbleShape(position: BubblePosition, isFromSelf: Boolean): Shape {
    // CodeTheme.shapes.medium = 12dp, tiny = 4dp
    val l = 12.dp
    val s = 4.dp

    // Corner radius morph — animate each corner with spring matching prototype
    val cornerSpec = spring<Dp>(dampingRatio = 0.68f, stiffness = 500f)

    // Target corners: (topStart, topEnd, bottomEnd, bottomStart)
    val targets = when (position) {
        BubblePosition.Solo -> BubbleCorners(l, l, l, l)
        BubblePosition.First -> if (isFromSelf) BubbleCorners(l, l, s, l) else BubbleCorners(l, l, l, s)
        BubblePosition.Middle -> if (isFromSelf) BubbleCorners(l, s, s, l) else BubbleCorners(s, l, l, s)
        BubblePosition.Last -> if (isFromSelf) BubbleCorners(l, s, l, l) else BubbleCorners(s, l, l, l)
    }

    val topStart by animateDpAsState(targets.topStart, cornerSpec, label = "cTS")
    val topEnd by animateDpAsState(targets.topEnd, cornerSpec, label = "cTE")
    val bottomEnd by animateDpAsState(targets.bottomEnd, cornerSpec, label = "cBE")
    val bottomStart by animateDpAsState(targets.bottomStart, cornerSpec, label = "cBS")

    return RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
}

private data class BubbleCorners(val topStart: Dp, val topEnd: Dp, val bottomEnd: Dp, val bottomStart: Dp)

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

// region Previews

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_TextBubble_Outgoing() {
    TextBubble(
        text = "Hey! How's it going?",
        isFromSelf = true,
        position = BubblePosition.Solo,
        maxWidth = 300.dp,
    )
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_TextBubble_Incoming() {
    TextBubble(
        text = "Not bad, just shipped a new feature!",
        isFromSelf = false,
        position = BubblePosition.Solo,
        maxWidth = 300.dp,
    )
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_TextBubble_LongMessage() {
    TextBubble(
        text = "This is a much longer message that should wrap across multiple lines to show how the bubble handles overflow text content gracefully.",
        isFromSelf = false,
        position = BubblePosition.Solo,
        maxWidth = 300.dp,
    )
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_CashBubble_Outgoing() {
    CompositionLocalProvider(
        LocalExchange provides ExchangeStub(context = LocalContext.current)
    ) {
        CashBubble(
            amount = Fiat(fiat = 5.0),
            tokenName = "USDF",
            tokenImageUrl = "",
            isFromSelf = true,
            position = BubblePosition.Solo,
            maxWidth = 300.dp,
        )
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_CashBubble_Incoming() {
    CompositionLocalProvider(
        LocalExchange provides ExchangeStub(context = LocalContext.current)
    ) {
        CashBubble(
            amount = Fiat(fiat = 1.0),
            tokenName = "Waylon",
            tokenImageUrl = "",
            isFromSelf = false,
            position = BubblePosition.Solo,
            maxWidth = 300.dp,
        )
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_CashBubble_NoTokenName() {
    CompositionLocalProvider(
        LocalExchange provides ExchangeStub(context = LocalContext.current)
    ) {
        CashBubble(
            amount = Fiat(fiat = 25.0),
            tokenName = "",
            tokenImageUrl = "",
            isFromSelf = true,
            position = BubblePosition.Solo,
            maxWidth = 300.dp,
        )
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_GroupedBubbles() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.End) {
        TextBubble("First message", true, BubblePosition.First, 300.dp)
        TextBubble("Second message", true, BubblePosition.Middle, 300.dp)
        TextBubble("Third message", true, BubblePosition.Last, 300.dp)
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_Conversation() {
    CompositionLocalProvider(
        LocalExchange provides ExchangeStub(context = LocalContext.current)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                TextBubble("Hey!", false, BubblePosition.Solo, 300.dp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextBubble("What's up?", true, BubblePosition.Solo, 300.dp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                CashBubble(Fiat(fiat = 5.0), "USDF", "", false, BubblePosition.Solo, 300.dp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextBubble("Thanks!", true, BubblePosition.Solo, 300.dp)
            }
        }
    }
}

// endregion
