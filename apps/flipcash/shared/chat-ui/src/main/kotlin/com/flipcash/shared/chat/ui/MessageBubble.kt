package com.flipcash.shared.chat.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.LazyPagingItems
import com.flipcash.app.core.ui.TokenIconWithName
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.models.ChatAction
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.LocalChatActionHandler
import com.flipcash.shared.chat.models.SeparatorConfig
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.PriceWithFlag
import com.getcode.ui.core.addIf

enum class BubblePosition { Solo, First, Middle, Last }

private const val BUBBLE_MAX_WIDTH_FRACTION = 0.78f
private val EDITED_MARKER_GAP = 6.dp
private const val CASH_BUBBLE_MAX_WIDTH_FRACTION = 0.64f

@Composable
fun ContentBubble(
    item: ChatListItem.ContentBubble,
    position: BubblePosition,
    modifier: Modifier = Modifier,
) {
    val actionHandler = LocalChatActionHandler.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val bubbleMaxWidth = when (item.content) {
            is MessageContent.Text -> maxWidth * BUBBLE_MAX_WIDTH_FRACTION
            is MessageContent.Cash -> maxWidth * CASH_BUBBLE_MAX_WIDTH_FRACTION
            is MessageContent.Deleted -> maxWidth * BUBBLE_MAX_WIDTH_FRACTION
            is MessageContent.Media -> maxWidth * CASH_BUBBLE_MAX_WIDTH_FRACTION
            is MessageContent.Reply -> maxWidth * BUBBLE_MAX_WIDTH_FRACTION
            is MessageContent.System -> maxWidth
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (item.isFromSelf) Arrangement.End else Arrangement.Start,
        ) {
            when (val content = item.content) {
                is MessageContent.Text -> TextBubble(
                    modifier = modifier,
                    text = content.text,
                    isFromSelf = item.isFromSelf,
                    position = position,
                    maxWidth = bubbleMaxWidth,
                    isEdited = item.isEdited,
                )

                // A tombstone is a text bubble with different words. Rendering it through the same
                // composable is what makes a delete an in-place update of the row already on screen.
                is MessageContent.Deleted -> TextBubble(
                    modifier = modifier,
                    text = stringResource(
                        if (item.deletedByViewer) {
                            R.string.label_messageDeletedByYou
                        } else {
                            R.string.label_messageDeleted
                        }
                    ),
                    isFromSelf = item.isFromSelf,
                    position = position,
                    maxWidth = bubbleMaxWidth,
                    isTombstone = true,
                )

                is MessageContent.Cash -> CashBubble(
                    modifier = modifier,
                    amount = content.amount,
                    tokenName = content.tokenName,
                    tokenImageUrl = content.tokenImageUrl,
                    isFromSelf = item.isFromSelf,
                    action = content.action,
                    position = position,
                    maxWidth = bubbleMaxWidth,
                    onClick = {
                        actionHandler(ChatAction.ViewToken(content.mint))
                    }
                )

                // TODO
                is MessageContent.Media -> Unit
                is MessageContent.Reply -> Unit
                is MessageContent.System -> Unit
            }
        }
    }
}

private const val EDITED_MARKER_SLOT = "edited-marker"

@Composable
private fun TextBubble(
    text: String,
    isFromSelf: Boolean,
    position: BubblePosition,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    isEdited: Boolean = false,
    isTombstone: Boolean = false,
) {
    Bubble(isFromSelf, position, maxWidth, modifier) {
        val linkStyle = SpanStyle(
            color = CodeTheme.colors.textMain,
            textDecoration = TextDecoration.Underline,
        )
        // A tombstone carries no link and nothing worth selecting; it is a notice, not a message.
        val body = if (isTombstone) {
            AnnotatedString(text)
        } else {
            rememberRichText(text = text, annotators = listOf(UrlAnnotator(linkStyle)))
        }
        val bodyStyle = CodeTheme.typography.textMedium.copy(
            fontWeight = FontWeight.Medium,
            fontStyle = if (isTombstone) FontStyle.Italic else FontStyle.Normal,
        )
        val bodyColor = if (isTombstone) {
            CodeTheme.colors.textSecondary
        } else {
            CodeTheme.colors.textMain
        }

        val markerLabel = stringResource(R.string.label_edited)
        val markerStyle = CodeTheme.typography.caption

        // The marker is pinned to the bubble's bottom-trailing corner rather than laid out after
        // the text, so the body has to leave a hole of exactly the marker's width. An empty
        // placeholder in the text flow does that: it sits on the last line where there is room and
        // wraps onto its own line where there isn't, without dragging the last word along with it.
        // It draws nothing and holds no text, so selection and the accessibility tree see only the
        // real marker below.
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val reservation = remember(markerLabel, markerStyle, density) {
            with(density) {
                (measurer.measure(markerLabel, markerStyle).size.width.toDp() + EDITED_MARKER_GAP).toSp()
            }
        }

        val laidOut = if (isEdited) {
            buildAnnotatedString {
                append(body)
                appendInlineContent(EDITED_MARKER_SLOT, "\u2007")
            }
        } else {
            body
        }
        val inlineContent = if (isEdited) {
            mapOf(
                EDITED_MARKER_SLOT to InlineTextContent(
                    Placeholder(
                        width = reservation,
                        height = 1.sp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextBottom,
                    ),
                ) { },
            )
        } else {
            emptyMap()
        }

        // No SelectionContainer: long-press is the transcript's selection gesture, and a text
        // selection handle inside the bubble would consume it before the row ever sees it. Copying
        // a message is the selection bar's Copy action instead — the same trade WhatsApp makes.
        Text(
            text = laidOut,
            inlineContent = inlineContent,
            style = bodyStyle,
            color = bodyColor,
        )

        if (isEdited) {
            Text(
                modifier = Modifier.align(Alignment.BottomEnd),
                text = markerLabel,
                style = markerStyle,
                color = CodeTheme.colors.textSecondary,
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
    action: MessageContent.Cash.Action = MessageContent.Cash.Action.SENT,
    onClick: () -> Unit = { },
    modifier: Modifier = Modifier,
) {
    Bubble(
        isFromSelf = isFromSelf,
        position = position,
        minWidth = maxWidth,
        maxWidth = maxWidth,
        onClick = onClick,
        modifier = modifier
    ) {
        val exchange = LocalExchange.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (LocalInspectionMode.current) {
                Row(
                    modifier = Modifier
                        .align(Alignment.Start),
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(CodeTheme.dimens.staticGrid.x2)
                            .background(Color(0xFF3F3F3F), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .width(CodeTheme.dimens.staticGrid.x5)
                            .height(CodeTheme.dimens.staticGrid.x1)
                            .background(Color(0xFF3F3F3F), CircleShape)
                    )
                }
            } else {
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
            }

            Column(
                modifier = Modifier
                    .padding(top = CodeTheme.dimens.grid.x5, bottom = CodeTheme.dimens.grid.x8),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The verb splits the two only on the sending side. A recipient reads
                // "You received" either way: the money that arrived is the same money, and which
                // button the sender pressed to send it isn't something the thread needs to relay.
                val subtitleRes = if (!isFromSelf) {
                    R.string.subtitle_youReceived
                } else when (action) {
                    MessageContent.Cash.Action.TIPPED -> R.string.subtitle_youTipped
                    MessageContent.Cash.Action.SENT -> R.string.subtitle_youSent
                }
                Text(
                    text = stringResource(subtitleRes),
                    style = CodeTheme.typography.caption.copy(
                        fontWeight = FontWeight.Medium,
                    ),
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
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 20.sp,
                                maxFontSize = CodeTheme.typography.displayMedium.fontSize
                            ),
                            maxLines = 1,
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
    modifier: Modifier = Modifier,
    minWidth: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val bubble = if (isFromSelf) {
        CodeTheme.colors.chat.outgoingBubble
    } else {
        CodeTheme.colors.chat.incomingBubble
    }
    val shape = bubbleShape(position, isFromSelf)
    Box(
        modifier = modifier
            .widthIn(min = minWidth, max = maxWidth)
            .clip(shape)
            .addIf(bubble.hasBorder) {
                Modifier.border(1.dp, bubble.border, shape)
            }
            .background(bubble.background)
            .addIf(onClick != null) {
                Modifier.clip(shape).clickable { onClick?.invoke() }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        content()
    }
}

@Composable
fun bubbleShape(position: BubblePosition, isFromSelf: Boolean): Shape {
    val l = 12.dp
    val s = 4.dp

    val cornerSpec = spring<Dp>(dampingRatio = 0.68f, stiffness = 500f)

    val targets = when (position) {
        BubblePosition.Solo -> BubbleCorners(l, l, l, l)
        BubblePosition.First -> if (isFromSelf) BubbleCorners(l, l, s, l) else BubbleCorners(
            l,
            l,
            l,
            s
        )

        BubblePosition.Middle -> if (isFromSelf) BubbleCorners(l, s, s, l) else BubbleCorners(
            s,
            l,
            l,
            s
        )

        BubblePosition.Last -> if (isFromSelf) BubbleCorners(l, s, l, l) else BubbleCorners(
            s,
            l,
            l,
            l
        )
    }

    val topStart by animateDpAsState(targets.topStart, cornerSpec, label = "cTS")
    val topEnd by animateDpAsState(targets.topEnd, cornerSpec, label = "cTE")
    val bottomEnd by animateDpAsState(targets.bottomEnd, cornerSpec, label = "cBE")
    val bottomStart by animateDpAsState(targets.bottomStart, cornerSpec, label = "cBS")

    return RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
}

private data class BubbleCorners(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomEnd: Dp,
    val bottomStart: Dp
)

fun bubblePositionOf(
    index: Int,
    item: ChatListItem.ContentBubble,
    messages: LazyPagingItems<ChatListItem>,
    config: SeparatorConfig,
): BubblePosition {
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
        groupedAbove -> BubblePosition.Last
        groupedBelow -> BubblePosition.First
        else -> BubblePosition.Solo
    }
}

fun bubblePositionOf(
    index: Int,
    item: ChatListItem.ContentBubble,
    messages: List<ChatListItem>,
    config: SeparatorConfig,
): BubblePosition {
    val above = if (index + 1 < messages.count()) {
        messages[index + 1] as? ChatListItem.ContentBubble
    } else null
    val below = if (index > 0) {
        messages[index - 1] as? ChatListItem.ContentBubble
    } else null

    val groupedAbove = above != null &&
            above.isFromSelf == item.isFromSelf &&
            config.isGrouped(item.timestamp, above.timestamp)

    val groupedBelow = below != null &&
            below.isFromSelf == item.isFromSelf &&
            config.isGrouped(item.timestamp, below.timestamp)

    return when {
        groupedAbove && groupedBelow -> BubblePosition.Middle
        groupedAbove -> BubblePosition.Last
        groupedBelow -> BubblePosition.First
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
private fun Preview_TextBubble_Edited() {
    TextBubble(
        text = "Hey! How's it going?",
        isFromSelf = true,
        position = BubblePosition.Solo,
        maxWidth = 300.dp,
        isEdited = true,
    )
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_TextBubble_EditedLongMessage() {
    TextBubble(
        text = "A long enough message that the last line has no room left for the marker, so the reservation wraps onto a line of its own and the bubble grows to fit it.",
        isFromSelf = true,
        position = BubblePosition.Solo,
        maxWidth = 300.dp,
        isEdited = true,
    )
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_TextBubble_Tombstone() {
    TextBubble(
        text = "You deleted this message",
        isFromSelf = true,
        position = BubblePosition.Solo,
        maxWidth = 300.dp,
        isTombstone = true,
    )
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_TextBubble_WithLink() {
    TextBubble(
        text = "Check out https://flipcash.app for more info!",
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
private fun Preview_CashBubble_Tipped() {
    CompositionLocalProvider(
        LocalExchange provides ExchangeStub(context = LocalContext.current)
    ) {
        CashBubble(
            amount = Fiat(fiat = 5.0),
            tokenName = "USDF",
            tokenImageUrl = "",
            isFromSelf = false,
            position = BubblePosition.Solo,
            maxWidth = 300.dp,
            action = MessageContent.Cash.Action.TIPPED,
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
