package com.flipcash.shared.chat.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalUriHandler
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
import com.flipcash.shared.chat.models.ChatQuote
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.ChatQuoteSnippet
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.LocalChatActionHandler
import com.flipcash.shared.chat.models.SeparatorConfig
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.theme.CodeTheme
import com.getcode.theme.cornerRadius
import com.getcode.theme.tiny
import com.getcode.ui.components.PriceWithFlag
import com.getcode.ui.core.addIf

enum class BubblePosition { Solo, First, Middle, Last }

private const val BUBBLE_MAX_WIDTH_FRACTION = 0.78f
private val EDITED_MARKER_GAP = 6.dp
private const val CASH_BUBBLE_MAX_WIDTH_FRACTION = 0.64f

/**
 * @param onLongClick what a long-press on this bubble reports, or `null` where the row behind it
 * has nothing to select. Only bubbles that install a tap target of their own need it: a gesture the
 * bubble handles is consumed there, so a cash bubble without this swallows the transcript's
 * selection gesture and answers a long press with nothing.
 * @param attention how strongly this bubble is currently being pointed at, 0f to 1f — the flash a
 * jump leaves on the message it landed on. A lambda because it is read while drawing: an animation
 * running through it repaints the bubble without recomposing it or the list carrying it.
 */
@Composable
fun ContentBubble(
    item: ChatListItem.ContentBubble,
    position: BubblePosition,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    attention: () -> Float = { 0f },
) {
    val actionHandler = LocalChatActionHandler.current
    val jumbo = remember(item) { item.rendersBareEmoji() }
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
                    jumbo = jumbo,
                    linkCard = item.linkCard,
                    attention = attention,
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
                    attention = attention,
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
                    // Dropped rather than ignored while the transcript is behind a backdrop: with
                    // no click installed the tap reaches the backdrop and dismisses it, which is
                    // what a tap anywhere else on the dimmed transcript already does.
                    onClick = if (interactive) {
                        { actionHandler(ChatAction.ViewToken(content.mint)) }
                    } else {
                        null
                    },
                    // Gated with the tap, and for the same reason: behind the backdrop the bar is
                    // already acting on a message, and the row drops its own gestures there too.
                    onLongClick = onLongClick?.takeIf { interactive },
                    attention = attention,
                )

                // A reply is a text bubble with a citation above the body. Routing it through
                // the same composable keeps its grouping, edited marker and link handling
                // identical to any other message, which is what it is.
                is MessageContent.Reply -> TextBubble(
                    modifier = modifier,
                    text = content.linkableText().orEmpty(),
                    isFromSelf = item.isFromSelf,
                    position = position,
                    maxWidth = bubbleMaxWidth,
                    isEdited = item.isEdited,
                    quote = item.quote,
                    linkCard = item.linkCard,
                    // Dropped with the backdrop up, as the cash bubble's target is: the tap
                    // should dismiss the backdrop, not jump the transcript out from under it.
                    onQuoteClick = item.quote?.takeIf { interactive }?.let { quote ->
                        { actionHandler(ChatAction.JumpToMessage(quote.messageId)) }
                    },
                    onQuoteLongClick = onLongClick?.takeIf { interactive },
                    jumbo = jumbo,
                    attention = attention,
                )

                // TODO
                is MessageContent.Media -> Unit
                is MessageContent.System -> Unit
            }
        }
    }
}

/**
 * Whether this message draws as a bare emoji rather than inside a bubble — a short all-emoji
 * message, which is a reaction rather than a sentence and is drawn large with nothing around it.
 *
 * Public because the decision reaches past the bubble: with no bubble there is no corner to pin
 * the "Edited" marker into, so the row has to draw it on the line below and needs to know.
 *
 * A tombstone is excluded — its words are the app's, not the sender's — and so is a reply, whose
 * citation is a filled surface: stripping the bubble there would leave a panel with an emoji loose
 * beneath it.
 */
fun ChatListItem.ContentBubble.rendersBareEmoji(): Boolean {
    val text = when (val content = content) {
        is MessageContent.Text -> content.text
        is MessageContent.Reply -> if (quote != null) {
            return false
        } else {
            content.content.filterIsInstance<MessageContent.Text>().firstOrNull()?.text.orEmpty()
        }

        else -> return false
    }
    return EmojiOnlyText.clusterCountOrNull(text) != null
}

private const val EDITED_MARKER_SLOT = "edited-marker"

/** How white a bubble goes at the peak of the flash a jump leaves on it. */
private const val ATTENTION_SCRIM_ALPHA = 0.14f

/**
 * The citation over the body, with the narrower of the two stretched to the width of the wider.
 *
 * A [Column] would leave a short quote hanging inside a wider reply, and the panel is a filled
 * surface, so the slack reads as a notch cut out of the bubble rather than as a quote that happens
 * to be short. Filling the width outright is no better: it is the incoming maximum, not the
 * sibling's width, so every reply would square off against the widest bubble the transcript allows.
 *
 * So the body is measured at its own width first and that width becomes the panel's floor. A quote
 * longer than the reply keeps its own width and carries it out to the bubble, which is what makes
 * this a floor rather than a fixed width.
 */
@Composable
private fun QuotedBody(
    gap: Dp,
    quote: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    Layout(contents = listOf(quote, body)) { (quoteMeasurables, bodyMeasurables), constraints ->
        val gapPx = gap.roundToPx()
        val bodyPlaceable = bodyMeasurables.first().measure(
            constraints.copy(minWidth = 0, minHeight = 0),
        )
        val quotePlaceable = quoteMeasurables.first().measure(
            constraints.copy(
                minWidth = bodyPlaceable.width.coerceAtMost(constraints.maxWidth),
                minHeight = 0,
            ),
        )
        val width = maxOf(quotePlaceable.width, bodyPlaceable.width)
        val height = quotePlaceable.height + gapPx + bodyPlaceable.height
        layout(width, height) {
            quotePlaceable.place(0, 0)
            bodyPlaceable.place(0, quotePlaceable.height + gapPx)
        }
    }
}

@Composable
private fun TextBubble(
    text: String,
    isFromSelf: Boolean,
    position: BubblePosition,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    isEdited: Boolean = false,
    isTombstone: Boolean = false,
    quote: ChatQuote? = null,
    linkCard: LinkCard? = null,
    onQuoteClick: (() -> Unit)? = null,
    onQuoteLongClick: (() -> Unit)? = null,
    jumbo: Boolean = false,
    attention: () -> Float = { 0f },
) {
    if (jumbo) {
        JumboEmoji(
            text = text,
            isFromSelf = isFromSelf,
            position = position,
            maxWidth = maxWidth,
            modifier = modifier,
            attention = attention,
        )
        return
    }

    // The card is the link, drawn. Leaving the URL in the body underneath it would say the
    // same thing twice, so the span the card was built from goes with it and the prose around
    // it closes up; a message that was nothing but the link leaves no body at all.
    val bodyString = if (linkCard == null) {
        text
    } else {
        text.withoutLinkSpan(linkCard.start, linkCard.end)
    }

    // Nothing left to put in a bubble. The card is already a surface with its own fill and its own
    // rounded shape, so a bubble behind it draws a second, slightly larger card around the first.
    // A citation and the edited marker belong to the message rather than to the link, and either
    // one keeps the bubble.
    if (linkCard != null && bodyString.isEmpty() && quote == null && !isEdited) {
        BareLinkCard(
            card = linkCard,
            isFromSelf = isFromSelf,
            position = position,
            maxWidth = maxWidth,
            modifier = modifier,
            attention = attention,
        )
        return
    }

    // A reply hands the bubble the narrower surround, so the citation clears the body's own inset
    // on both sides; the body then puts the difference back and keeps the inset it has without a
    // quote. A bubble with no quote never widens, because the two are equal there.
    val surround = if (quote != null) BubbleDefaults.surroundInset else BubbleDefaults.paddingHorizontal
    val bodyInset = BubbleDefaults.paddingHorizontal - surround
    Bubble(
        isFromSelf,
        position,
        maxWidth,
        modifier,
        horizontalPadding = surround,
        attention = attention,
    ) {
        val linkStyle = SpanStyle(
            color = CodeTheme.colors.textMain,
            textDecoration = TextDecoration.Underline,
        )
        // A tombstone carries no link and nothing worth selecting; it is a notice, not a message.
        val body = if (isTombstone) {
            AnnotatedString(bodyString)
        } else {
            rememberRichText(text = bodyString, annotators = listOf(UrlAnnotator(linkStyle)))
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
        // The citation sits inside the bubble, above the body, so the two move together and the
        // reply reads as one message rather than as a quote with a message under it.
        val bodyText = @Composable {
            Text(
                modifier = Modifier.padding(horizontal = bodyInset),
                text = laidOut,
                inlineContent = inlineContent,
                style = bodyStyle,
                color = bodyColor,
            )
        }

        val quotedOrPlainBody = @Composable {
            if (quote == null) {
                bodyText()
            } else {
                QuotedBody(
                    gap = BubbleDefaults.surroundInset,
                    quote = {
                        ChatQuotePanel(
                            quote = quote,
                            onClick = onQuoteClick,
                            onLongClick = onQuoteLongClick,
                            // Tagged because the citation repeats the quoted message's own text, so a
                            // UI test matching on that text cannot tell the two apart.
                            modifier = Modifier.testTag("bubble_reply_quote"),
                        )
                    },
                    body = bodyText,
                )
            }
        }

        // Inside the bubble, above what is left of the body, on the same surround the citation
        // uses — the card and the message it came from are one message.
        if (linkCard == null) {
            quotedOrPlainBody()
        } else {
            val onCardClick = rememberLinkCardClick()
            Column(verticalArrangement = Arrangement.spacedBy(BubbleDefaults.surroundInset)) {
                LinkCardView(
                    card = linkCard,
                    onClick = onCardClick,
                )
                // A link on its own never reaches here -- it is drawn bubble-less above -- so
                // what is left is a citation, an edited marker, or prose the link sat inside.
                quotedOrPlainBody()
            }
        }

        if (isEdited) {
            Text(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = bodyInset),
                text = markerLabel,
                style = markerStyle,
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}

/**
 * A message that is only emoji: no bubble, and the emoji drawn at something like its own size.
 *
 * One size at every count rather than a size per count. Stepping down as emoji are added would
 * make the same emoji a different size depending on what was sent beside it, and three at this
 * size still sit well inside the bubble's own width ceiling.
 *
 * Laid out through [Bubble] rather than beside it so the run's corner geometry, the jump flash and
 * that width ceiling stay in one place; `bare` only drops the fill and the wider inset. The corner
 * radius still clips, which is what the flash needs: without it the highlight would be a rectangle
 * floating where no bubble is.
 *
 * The "Edited" marker is not here. With the bubble gone there is no corner to pin it into, so the
 * row draws it below, on the line the receipt label already occupies.
 */
@Composable
private fun JumboEmoji(
    text: String,
    isFromSelf: Boolean,
    position: BubblePosition,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    attention: () -> Float = { 0f },
) {
    Bubble(
        isFromSelf = isFromSelf,
        position = position,
        maxWidth = maxWidth,
        modifier = modifier,
        bare = true,
        horizontalPadding = BubbleDefaults.surroundInset,
        attention = attention,
    ) {
        Text(
            modifier = Modifier.testTag(JUMBO_EMOJI_TAG),
            text = text,
            // An emoji fills its line box, so the line height has to be given explicitly: the body
            // style's would crop the glyph at this size.
            style = CodeTheme.typography.textMedium.copy(
                fontSize = JUMBO_EMOJI_SIZE,
                lineHeight = JUMBO_EMOJI_SIZE * 1.25f,
            ),
            color = CodeTheme.colors.textMain,
        )
    }
}

private val JUMBO_EMOJI_SIZE = 44.sp

internal const val JUMBO_EMOJI_TAG = "bubble_jumbo_emoji"

/**
 * A card standing in for the whole message, with no bubble behind it.
 *
 * Everything the bubble would have contributed is already the card's: the fill, the rounded corners
 * and the tap. What is not the card's is the flash a jump leaves on the message it landed on, which
 * belongs to the transcript rather than to the bubble -- so this goes through [Bubble] as the jumbo
 * emoji does, with `bare` dropping the fill and the horizontal inset and leaving the flash, the
 * width ceiling and the corner clip where every other bubble already gets them.
 */
@Composable
private fun BareLinkCard(
    card: LinkCard,
    isFromSelf: Boolean,
    position: BubblePosition,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    attention: () -> Float = { 0f },
) {
    val onCardClick = rememberLinkCardClick()
    Bubble(
        isFromSelf = isFromSelf,
        position = position,
        maxWidth = maxWidth,
        modifier = modifier,
        bare = true,
        // The card runs to the full width the bubble would have had, and to the full height. Its
        // own inset is inside its frame, so padding here would be a second one -- and with no fill
        // to hide inside, it is drawn outside the card's edge, where it reads as a gap rather than
        // as padding. A text bubble's inset sits within its fill, so leaving these at the bubble's
        // defaults would space a card away from its neighbours further than two bubbles ever are.
        horizontalPadding = 0.dp,
        verticalPadding = 0.dp,
        attention = attention,
    ) {
        LinkCardView(card = card, onClick = onCardClick)
    }
}

/**
 * What a tap on a card does.
 *
 * A cash link goes back out through the URL handler its link span used, so replacing the text with
 * a card changed how the message looks and not what tapping it does.
 *
 * A token link does not. Its URL classifies as a deep link, and the router answers that with the
 * wallet sheet plus the token's card expanded in place — right for a link arriving from outside the
 * app, wrong from inside a chat, where it swaps the transcript for the wallet on the way to a
 * screen the reader asked for directly. There is no wallet card here for the detail to grow out of.
 * So it pushes, exactly as the cash bubble's own token tap does, and back returns to the message.
 *
 * A tip card link opens its owner's profile — the page about the person, which is what a link to
 * someone's card is asking about. The conversation is one button further on, from a profile that
 * says who it would be with; going straight there instead would drop the reader into a DM with
 * someone they have only seen a card for. That needs a user id, so only a resolved card takes this
 * route; an unresolved one still has only a URL and leaves through the URL handler, where the
 * router resolves the owner the long way and diverts a link to your own card.
 */
@Composable
private fun rememberLinkCardClick(): (LinkCard) -> Unit {
    val actionHandler = LocalChatActionHandler.current
    val uriHandler = LocalUriHandler.current
    return { card ->
        when (card) {
            // The entropy is reported and the link still leaves through the URL handler, in that
            // order and unconditionally. Nothing here waits on the report or reads it back, so a
            // tap opens the link whatever the transcript does with the name.
            is LinkCard.Cash -> {
                actionHandler(ChatAction.CashLinkOpened(card.entropy))
                uriHandler.openUri(card.url)
            }
            is LinkCard.TokenInfo -> actionHandler(ChatAction.ViewToken(card.mint))
            is LinkCard.TipCard -> {
                // The id is server-provided, so a profile assembled locally names nobody the app
                // can open. `TipCardDecorator` guards the same null for the same reason.
                val profile = (card.state as? LinkCard.TipCard.State.Resolved)?.profile
                when (val userId = profile?.userId) {
                    null -> uriHandler.openUri(card.url)
                    else -> actionHandler(ChatAction.ViewTipCardOwner(userId, profile))
                }
            }
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
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    attention: () -> Float = { 0f },
) {
    Bubble(
        isFromSelf = isFromSelf,
        position = position,
        minWidth = maxWidth,
        maxWidth = maxWidth,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        attention = attention,
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

/**
 * The bubble's own geometry. Named rather than inlined because the citation panel inside a reply
 * has to be laid out against it: a panel that picks its own corner radius and its own padding stops
 * matching the bubble the moment either of these moves.
 */
internal object BubbleDefaults {
    /** The radius of a corner on the outside of a same-sender run. */
    val cornerLarge: Dp
        @Composable get() = CodeTheme.shapes.medium.cornerRadius()

    /** The flattened radius of a corner facing the rest of the run. */
    val cornerSmall: Dp
        @Composable get() = CodeTheme.shapes.tiny.cornerRadius()

    val paddingHorizontal: Dp
        @Composable get() = CodeTheme.dimens.staticGrid.x3

    val paddingVertical: Dp
        @Composable get() = CodeTheme.dimens.staticGrid.x2

    /**
     * The gap between the citation panel and the bubble, the same on the top, leading and trailing
     * sides and again between the panel and the body beneath it. One number rather than three, so
     * the panel's corner has a single surround to be concentric with and the quote, the body and
     * the bubble's edges sit on one rhythm.
     *
     * The bubble's vertical margin, which is the narrower of its two insets: the body keeps the
     * wider horizontal one, so the panel reaches past the text on both sides the way it does on iOS.
     */
    val surroundInset: Dp
        @Composable get() = paddingVertical
}

@Composable
private fun Bubble(
    isFromSelf: Boolean,
    position: BubblePosition,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    minWidth: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    horizontalPadding: Dp = BubbleDefaults.paddingHorizontal,
    verticalPadding: Dp = BubbleDefaults.paddingVertical,
    bare: Boolean = false,
    attention: () -> Float = { 0f },
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
            .addIf(!bare && bubble.hasBorder) {
                Modifier.border(1.dp, bubble.border, shape)
            }
            .addIf(!bare) {
                Modifier.background(bubble.background)
            }
            // Over the content, not under it: a scrim behind the text would be hidden by the
            // bubble's own fill. Drawn here rather than as a background layer so it also lightens
            // the words, which is what makes a lit bubble read as one thing.
            //
            // Every bubble variant comes through here, so a cash card flashes like a text bubble
            // does, and the shape is the one already clipped above — corners included, mid-animation
            // included.
            .drawWithContent {
                drawContent()
                val strength = attention()
                if (strength > 0f) {
                    drawRect(Color.White, alpha = ATTENTION_SCRIM_ALPHA * strength)
                }
            }
            .addIf(onClick != null || onLongClick != null) {
                // combinedClickable rather than two modifiers: a bubble that takes the tap takes
                // the long press with it, so both gestures are reported from the same target.
                Modifier.clip(shape).combinedClickable(
                    onLongClick = onLongClick,
                    onClick = { onClick?.invoke() },
                )
            }
            .padding(
                horizontal = horizontalPadding,
                vertical = verticalPadding,
            ),
    ) {
        content()
    }
}

@Composable
fun bubbleShape(position: BubblePosition, isFromSelf: Boolean): Shape {
    val l = BubbleDefaults.cornerLarge
    val s = BubbleDefaults.cornerSmall

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

/**
 * Whether [item] tucks into the same run as the bubble [other] next to it.
 *
 * A bare emoji breaks the run on both sides. It draws no bubble, so there is no edge for its
 * neighbour to square itself against, and a squared corner facing open space reads as half a bubble
 * with the other half missing. Either side being bare is enough, so the message under an emoji
 * closes its top corners the same way the message above it closes its bottom ones.
 */
private fun groupsWith(
    item: ChatListItem.ContentBubble,
    other: ChatListItem.ContentBubble?,
    config: SeparatorConfig,
): Boolean = other != null &&
        item.isSameAuthorAs(other) &&
        config.isGrouped(item.timestamp, other.timestamp) &&
        !item.rendersBareEmoji() &&
        !other.rendersBareEmoji()

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

    val groupedAbove = groupsWith(item, above, config)
    val groupedBelow = groupsWith(item, below, config)

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

    val groupedAbove = groupsWith(item, above, config)
    val groupedBelow = groupsWith(item, below, config)

    return when {
        groupedAbove && groupedBelow -> BubblePosition.Middle
        groupedAbove -> BubblePosition.Last
        groupedBelow -> BubblePosition.First
        else -> BubblePosition.Solo
    }
}

// region Previews

private const val PREVIEW_CASH_LINK = "https://send.flipcash.com/c/#/e=KNi8pQr1n5hRU65vKJGge3"
private const val PREVIEW_CASH_TEXT = "here you go $PREVIEW_CASH_LINK"

/** Spans the real detection pass would produce, so the preview strips the same text the app does. */
private fun previewCard(
    state: LinkCard.Cash.State,
    text: String = PREVIEW_CASH_TEXT,
) = LinkCard.Cash(
    url = PREVIEW_CASH_LINK,
    start = text.indexOf(PREVIEW_CASH_LINK),
    end = text.indexOf(PREVIEW_CASH_LINK) + PREVIEW_CASH_LINK.length,
    entropy = "KNi8pQr1n5hRU65vKJGge3",
    state = state,
)

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_TextBubble_LinkCard_Unresolved() {
    TextBubble(
        text = PREVIEW_CASH_TEXT,
        isFromSelf = false,
        position = BubblePosition.Solo,
        maxWidth = 300.dp,
        linkCard = previewCard(state = LinkCard.Cash.State.Unresolved),
    )
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_TextBubble_LinkCard_Claimable() {
    TextBubble(
        text = PREVIEW_CASH_TEXT,
        isFromSelf = false,
        position = BubblePosition.Solo,
        maxWidth = 300.dp,
        linkCard = previewCard(
            state = LinkCard.Cash.State.Resolved(
                amount = "$5.00",
                claim = LinkCard.Cash.Claim.Claimable,
                token = Token.usdf,
            ),
        ),
    )
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_TextBubble_LinkCard_Claimed() {
    TextBubble(
        text = "sent you this $PREVIEW_CASH_LINK",
        isFromSelf = true,
        position = BubblePosition.Solo,
        maxWidth = 300.dp,
        linkCard = previewCard(
            text = "sent you this $PREVIEW_CASH_LINK",
            state = LinkCard.Cash.State.Resolved(
                amount = "$5.00",
                claim = LinkCard.Cash.Claim.Claimed,
                token = Token.usdf,
            ),
        ),
    )
}

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

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_TextBubble_Reply() {
    TextBubble(
        text = "on my way",
        isFromSelf = true,
        position = BubblePosition.Solo,
        maxWidth = 300.dp,
        quote = ChatQuote(
            messageId = 1L,
            authorName = "Alice",
            snippet = ChatQuoteSnippet.Text("are you still coming tonight?"),
            accent = Color(0xFF5B8DEF),
            nameAccent = Color(0xFF8FB4F5),
        ),
    )
}

/**
 * The citation against the bubble corners it is set into: a run's middle bubble flattens the
 * corners the panel sits nearest, which is where an inner radius of its own would show.
 */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_GroupedBubbles_Reply() {
    val quote = ChatQuote(
        messageId = 1L,
        authorName = "Alice",
        snippet = ChatQuoteSnippet.Text("are you still coming tonight?"),
        accent = Color(0xFF5B8DEF),
        nameAccent = Color(0xFF8FB4F5),
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.End) {
        TextBubble("First message", true, BubblePosition.First, 300.dp)
        TextBubble(
            text = "on my way",
            isFromSelf = true,
            position = BubblePosition.Middle,
            maxWidth = 300.dp,
            quote = quote,
        )
        TextBubble("Third message", true, BubblePosition.Last, 300.dp)
    }
}

// endregion
