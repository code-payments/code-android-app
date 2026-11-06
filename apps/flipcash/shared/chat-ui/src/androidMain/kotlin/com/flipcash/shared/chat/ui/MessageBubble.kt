package com.flipcash.shared.chat.ui

// kmp-audit: needs a bubble or link card that renders CodeButton, which lives in :ui:components androidMain (RuntimeShader frosted glass)

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.flipcash.core.resources.Res
import com.flipcash.core.resources.action_sendBack
import com.flipcash.core.resources.label_sentBack
import com.flipcash.core.resources.label_groupSendTotal
import com.flipcash.core.resources.label_messageDeleted
import com.flipcash.core.resources.label_messageDeletedByYou
import com.flipcash.core.resources.subtitle_youTipped
import com.flipcash.core.resources.title_unnamedUser
import org.jetbrains.compose.resources.stringResource
import com.flipcash.app.core.ui.TokenIconWithName
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.services.models.chat.MessageContent
import com.getcode.solana.keys.Mint
import com.flipcash.services.models.chat.WidgetContent
import com.flipcash.shared.chat.RequestCardState
import com.flipcash.shared.chat.ui.request.FetchWhenAbsent
import com.flipcash.shared.chat.ui.request.LocalPaymentRequestCards
import com.flipcash.shared.chat.ui.request.PaymentRequestBubbleDefaults
import com.flipcash.shared.chat.ui.request.PaymentRequestCardBody
import com.flipcash.shared.chat.models.ChatAction
import com.flipcash.shared.chat.models.ChatQuote
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.ChatQuoteSnippet
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.SendBackRow
import com.getcode.ui.theme.CodeCircularProgressIndicator
import androidx.compose.foundation.clickable
import com.flipcash.shared.chat.models.PhotoStack
import com.flipcash.shared.chat.models.LocalChatActionHandler
import com.flipcash.shared.chat.ui.media.MediaMessageBubble
import com.flipcash.shared.chat.ui.media.photoBody
import com.flipcash.shared.chat.models.MessagePart
import com.flipcash.shared.chat.models.isAloneIn
import com.flipcash.shared.chat.models.splitAroundLinkCard
import kotlin.time.Instant
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.addIf


/**
 * @param onLongClick what a long-press on this bubble reports, or `null` where the row behind it
 * has nothing to select. Only bubbles that install a tap target of their own need it: a gesture the
 * bubble handles is consumed there, so a cash bubble without this swallows the transcript's
 * selection gesture and answers a long press with nothing.
 * @param attention how strongly this bubble is currently being pointed at, 0f to 1f — the flash a
 * jump leaves on the message it landed on. A lambda because it is read while drawing: an animation
 * running through it repaints the bubble without recomposing it or the list carrying it.
 * @param leading whether the bubble sits at the leading edge whoever sent it, as in a list of
 * messages lifted out of the transcript; it keeps its own style either way.
 * @param stack the card stack this photo row belongs to, when it was sent together with the rows
 * around it; the row draws its share of the stack, which may be nothing.
 */
@Composable
fun ContentBubble(
    item: ChatListItem.ContentBubble,
    position: BubblePosition,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    leading: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    attention: () -> Float = { 0f },
    stack: PhotoStack? = null,
) {
    val actionHandler = LocalChatActionHandler.current
    val updateAction = LocalUpdateAppAction.current
    // A redacted placeholder is never drawn bare: it is not the message, only its shape.
    val jumbo = remember(item) { !item.redacted && item.rendersBareEmoji() }
    // The send time, spoken after the message so a screen reader gets it without the reveal drag.
    // A state description rather than a content description: the row merges the bubble into its
    // click target, and a content description there would replace the message's text, not follow it.
    // Once per message, on its last row, as the reveal column gives it; a tombstone has no time.
    val spokenTime = if (item.isLastRow && item.content !is MessageContent.Deleted) {
        messageFullTime(item.timestamp)
    } else {
        null
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (spokenTime != null) Modifier.semantics { stateDescription = spokenTime } else Modifier),
    ) {
        val bubbleMaxWidth = bubbleMaxWidth(
            content = item.content,
            rowWidth = maxWidth,
            rowInset = CodeTheme.dimens.inset,
        )
        val messageCardWidth = cashCardWidth(rowWidth = maxWidth, rowInset = CodeTheme.dimens.inset)
        // A link card fills its width, so it takes the capped width even on a text message's row.
        val cardMaxWidth = transcriptBubbleMaxWidth(
            rowWidth = maxWidth,
            rowInset = CodeTheme.dimens.inset,
            fillsWidth = true,
        )

        // A split message's citation goes on its first row and its marker on its last, so the
        // pieces read as one message stacked rather than as three.
        val quote = item.quote?.takeIf { item.isFirstRow }
        // Dropped behind the backdrop, as the cash bubble's tap is: there a tap dismisses it.
        val onMentionClick: ((String) -> Unit)? = if (interactive) {
            { username -> actionHandler(ChatAction.OpenMention(username)) }
        } else {
            null
        }
        val onQuoteClick = quote?.takeIf { interactive }?.let { cited ->
            { actionHandler(ChatAction.JumpToMessage(cited.messageId, fromMessageId = item.messageId)) }
        }
        val isEdited = item.isEdited && item.isLastRow
        val card = item.linkCard?.takeIf { item.part == MessagePart.Card }
        // A web card is not split out: it draws in the text bubble under the text.
        val webCard = item.linkCard as? LinkCard.Web
        // Whether the text is only that link; the card still has to resolve for it to draw bare.
        val webCardAlone = remember(item) { item.linkOnlyWebCard() != null }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (item.isFromSelf && !leading) Arrangement.End else Arrangement.Start,
        ) {
            // The card row of a split message, bare whatever the message around it carries -- a
            // text or a reply alike. The rows either side of it hold the prose.
            if (card != null) {
                BareLinkCard(
                    modifier = modifier,
                    card = card,
                    isFromSelf = item.isFromSelf,
                    position = position,
                    // A message card takes the cash card's width rather than a bubble's, so its
                    // states read as one fixed card against the prose either side (iOS matches).
                    maxWidth = if (card is LinkCard.Message) messageCardWidth else cardMaxWidth,
                    quote = quote,
                    onQuoteClick = onQuoteClick,
                    onQuoteLongClick = onLongClick?.takeIf { interactive },
                    onLongClick = onLongClick?.takeIf { interactive },
                    onDoubleClick = onDoubleClick?.takeIf { interactive },
                    interactive = interactive,
                    attention = attention,
                )
                return@Row
            }

            when (val content = item.content) {
                is MessageContent.Text -> TextBubble(
                    modifier = modifier,
                    text = item.partText ?: content.text,
                    mentions = item.mentions,
                    onMentionClick = onMentionClick,
                    isFromSelf = item.isFromSelf,
                    position = position,
                    maxWidth = bubbleMaxWidth,
                    isEdited = isEdited,
                    jumbo = jumbo,
                    webCard = webCard,
                    webCardAlone = webCardAlone,
                    cardMaxWidth = cardMaxWidth,
                    onCardLongClick = onLongClick?.takeIf { interactive },
                    onCardDoubleClick = onDoubleClick?.takeIf { interactive },
                    interactive = interactive,
                    attention = attention,
                    redacted = item.redacted,
                )

                // A tombstone is a text bubble with different words. Rendering it through the same
                // composable is what makes a delete an in-place update of the row already on screen.
                is MessageContent.Deleted -> TextBubble(
                    modifier = modifier,
                    text = stringResource(
                        if (item.deletedByViewer) {
                            Res.string.label_messageDeletedByYou
                        } else {
                            Res.string.label_messageDeleted
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
                    content = content,
                    lines = content.bubbleLines(
                        isFromSelf = item.isFromSelf,
                        viewerPaid = item.receivedPart != null,
                        people = item.cashPeople,
                    ),
                    amount = content.amountLine(),
                    isFromSelf = item.isFromSelf,
                    position = position,
                    maxWidth = bubbleMaxWidth,
                    // Dropped rather than ignored while the transcript is behind a backdrop: with
                    // no click installed the tap reaches the backdrop and dismisses it, which is
                    // what a tap anywhere else on the dimmed transcript already does. A group send
                    // opens its details whether it paid one person or several, even though one
                    // person draws the DM layout.
                    onClick = if (interactive) {
                        val groupSend = content.multiReceiver.isNotEmpty() ||
                            (item.cashPeople is CashBubblePeople.Group && content.receiver != null)
                        if (groupSend) {
                            { actionHandler(ChatAction.ViewGroupSend(item)) }
                        } else {
                            { actionHandler(ChatAction.ViewToken(content.mint)) }
                        }
                    } else {
                        null
                    },
                    // Gated with the tap, and for the same reason: behind the backdrop the bar is
                    // already acting on a message, and the row drops its own gestures there too.
                    onLongClick = onLongClick?.takeIf { interactive },
                    onDoubleClick = onDoubleClick?.takeIf { interactive },
                    attention = attention,
                    sendBack = item.sendBack,
                    onSendBack = if (interactive) {
                        { actionHandler(ChatAction.SendBack(item.messageId)) }
                    } else {
                        null
                    },
                )

                // A reply is a text bubble with a citation above the body. Routing it through
                // the same composable keeps its grouping, edited marker and link handling
                // identical to any other message, which is what it is.
                is MessageContent.Reply -> content.photoBody()?.let { photo ->
                    MediaMessageBubble(
                        item = item,
                        body = photo,
                        position = position,
                        maxWidth = bubbleMaxWidth,
                        modifier = modifier,
                        quote = quote,
                        onQuoteClick = onQuoteClick,
                        onMentionClick = onMentionClick,
                        linkCard = item.linkCard,
                        interactive = interactive,
                        onLongClick = onLongClick?.takeIf { interactive },
                        onDoubleClick = onDoubleClick?.takeIf { interactive },
                        stack = stack,
                    )
                } ?: TextBubble(
                    modifier = modifier,
                    text = item.partText ?: content.linkableText().orEmpty(),
                    mentions = item.mentions,
                    onMentionClick = onMentionClick,
                    isFromSelf = item.isFromSelf,
                    position = position,
                    maxWidth = bubbleMaxWidth,
                    isEdited = isEdited,
                    quote = quote,
                    // Dropped with the backdrop up, as the cash bubble's target is: the tap
                    // should dismiss the backdrop, not jump the transcript out from under it.
                    onQuoteClick = onQuoteClick,
                    onQuoteLongClick = onLongClick?.takeIf { interactive },
                    onQuoteDoubleClick = onDoubleClick?.takeIf { interactive },
                    jumbo = jumbo,
                    webCard = webCard,
                    webCardAlone = webCardAlone,
                    cardMaxWidth = cardMaxWidth,
                    onCardLongClick = onLongClick?.takeIf { interactive },
                    onCardDoubleClick = onDoubleClick?.takeIf { interactive },
                    interactive = interactive,
                    attention = attention,
                    redacted = item.redacted,
                )

                // Ciphertext reaches a bubble only when it didn't open; an opened message carries
                // its plaintext content instead.
                is MessageContent.Encrypted -> UndecryptableBubble(
                    modifier = modifier,
                    hint = item.undecryptableHint,
                    maxWidth = bubbleMaxWidth,
                    actionLabel = updateAction?.label,
                    onAction = { updateAction?.onClick?.invoke() },
                )

                is MessageContent.Media -> MediaMessageBubble(
                    item = item,
                    body = content,
                    position = position,
                    maxWidth = bubbleMaxWidth,
                    modifier = modifier,
                    onMentionClick = onMentionClick,
                    linkCard = item.linkCard,
                    interactive = interactive,
                    onLongClick = onLongClick?.takeIf { interactive },
                    onDoubleClick = onDoubleClick?.takeIf { interactive },
                    stack = stack,
                )
                // Unreachable: the transcript emits System content as ChatListItem.SystemMessage.
                is MessageContent.System -> Unit
                // A widget variant this client doesn't know draws as any message it can't show.
                is MessageContent.Widget -> when (val widget = content.widget) {
                    is WidgetContent.ShareProfile -> ShareProfileWidgetBubble(
                        modifier = modifier,
                        username = widget.username,
                        isFromSelf = item.isFromSelf,
                        position = position,
                        maxWidth = bubbleMaxWidth,
                        onShare = if (interactive) actionHandler else null,
                        onLongClick = onLongClick?.takeIf { interactive },
                        onDoubleClick = onDoubleClick?.takeIf { interactive },
                        attention = attention,
                    )

                    is WidgetContent.PaymentRequest -> {
                        val cards = LocalPaymentRequestCards.current
                        val state = cards?.stateOf(widget.id, item.isFromSelf, item.senderId)
                            ?: RequestCardState.Unsupported
                        if (cards != null) FetchWhenAbsent(widget.id, cards)
                        if (cards == null || state == RequestCardState.Unsupported) {
                            UndecryptableBubble(
                                modifier = modifier,
                                hint = item.undecryptableHint,
                                maxWidth = bubbleMaxWidth,
                                actionLabel = updateAction?.label,
                                onAction = { updateAction?.onClick?.invoke() },
                            )
                        } else {
                            val width = minOf(bubbleMaxWidth, PaymentRequestBubbleDefaults.WIDTH)
                            Bubble(
                                isFromSelf = item.isFromSelf,
                                position = position,
                                minWidth = width,
                                maxWidth = width,
                                horizontalPadding = CodeTheme.dimens.grid.x3,
                                verticalPadding = CodeTheme.dimens.grid.x3,
                                onClick = { cards.onOpen(widget.id) }.takeIf { interactive && state is RequestCardState.Ready },
                                onLongClick = onLongClick?.takeIf { interactive },
                                onDoubleClick = onDoubleClick?.takeIf { interactive },
                                modifier = modifier,
                                attention = attention,
                            ) {
                                PaymentRequestCardBody(state, cards.now)
                            }
                        }
                    }

                    WidgetContent.Unsupported -> UndecryptableBubble(
                        modifier = modifier,
                        hint = item.undecryptableHint,
                        maxWidth = bubbleMaxWidth,
                        actionLabel = updateAction?.label,
                        onAction = { updateAction?.onClick?.invoke() },
                    )
                }

                // A content type added after this build, drawn as an unknown widget is.
                MessageContent.Unsupported -> UndecryptableBubble(
                    modifier = modifier,
                    hint = item.undecryptableHint,
                    maxWidth = bubbleMaxWidth,
                    actionLabel = updateAction?.label,
                    onAction = { updateAction?.onClick?.invoke() },
                )
            }
        }
    }
}


/**
 * [rendersBare], plus the case only composition can answer: a message that is nothing but a web
 * link whose card has resolved, draws as the card alone (see [TextBubble]), so its "Edited" marker
 * comes out below it like a Flipcash card row's. Asks the same [rememberWebLinkCard] the bubble
 * does, so the two agree on every frame.
 *
 * Does not change [groupsWith]: a bare web card keeps its run, as a Flipcash card row does, because
 * it is drawn in the bubble's outline.
 */
@Composable
fun ChatListItem.ContentBubble.rememberRendersBare(): Boolean {
    if (rendersBare()) return true
    val web = linkOnlyWebCard() ?: return false
    val state = rememberWebLinkCard(web)
    return state.resolved != null
}

/**
 * The web card of a message whose text is nothing but its link, or null. "Nothing but" is the rule
 * [splitAroundLinkCard] applies to a Flipcash card (see [isAloneIn]); whether the card then draws
 * bare also depends on its state, which only composition has.
 */
private fun ChatListItem.ContentBubble.linkOnlyWebCard(): LinkCard.Web? {
    val web = linkCard as? LinkCard.Web ?: return null
    // A photo reply draws through the media bubble, which has no web card.
    val textual = when (val content = content) {
        is MessageContent.Text -> true
        is MessageContent.Reply -> content.photoBody() == null
        else -> false
    }
    if (!textual || part != null) return null
    return web.takeIf { plainText?.let(it::isAloneIn) == true }
}

internal const val REPLY_QUOTE_TAG = "bubble_reply_quote"

/** The hairline across the top of a cash bubble's Sent Back footer. */
internal const val SENT_BACK_DIVIDER_TAG = "bubble_sent_back_divider"

/** The Sent Back footer's band, under its divider. */
internal const val SENT_BACK_FOOTER_TAG = "bubble_sent_back_footer"

/** The white Send Back row along a cash bubble's bottom edge. */
internal const val SEND_BACK_ROW_TAG = "bubble_send_back_row"

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
    mentions: List<DetectedMention> = emptyList(),
    onMentionClick: ((String) -> Unit)? = null,
    quote: ChatQuote? = null,
    onQuoteClick: (() -> Unit)? = null,
    onQuoteLongClick: (() -> Unit)? = null,
    onQuoteDoubleClick: (() -> Unit)? = null,
    jumbo: Boolean = false,
    webCard: LinkCard.Web? = null,
    webCardAlone: Boolean = false,
    /** The bubble's width once [webCard] draws: a card fills its width, so this is capped. */
    cardMaxWidth: Dp = maxWidth,
    onCardLongClick: (() -> Unit)? = null,
    onCardDoubleClick: (() -> Unit)? = null,
    interactive: Boolean = true,
    attention: () -> Float = { 0f },
    redacted: Boolean = false,
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

    val bodyString = text

    // A reply hands the bubble the narrower surround, so the citation clears the body's own inset
    // on both sides; the body then puts the difference back and keeps the inset it has without a
    // quote. A bubble with no quote never widens, because the two are equal there.
    val surround = if (quote != null) BubbleDefaults.surroundInset else BubbleDefaults.paddingHorizontal
    val bodyInset = BubbleDefaults.paddingHorizontal - surround
    // Asked here rather than in the card so the bubble can widen for a card that draws: the image
    // would otherwise be squeezed to the text's width. A link with no preview leaves the bubble
    // text-sized.
    val web = webCard?.let { rememberWebLinkCard(it) }
    val cardDrawn = web?.resolved != null
    // A link-only message holds the card's place while its lookup runs, so the link never shows
    // and the card then fills the same slot. An empty or failed answer ends the wait, and the text
    // bubble with the link takes over.
    if (web != null && webCardAlone && cardDrawn) {
        BareWebCard(
            web = web,
            isFromSelf = isFromSelf,
            position = position,
            maxWidth = cardMaxWidth,
            modifier = modifier,
            quote = quote,
            onQuoteClick = onQuoteClick,
            onQuoteLongClick = onQuoteLongClick,
            onQuoteDoubleClick = onQuoteDoubleClick,
            onLongClick = onCardLongClick,
            onDoubleClick = onCardDoubleClick,
            interactive = interactive,
            attention = attention,
        )
        return
    }
    Bubble(
        isFromSelf,
        position,
        if (cardDrawn) cardMaxWidth else maxWidth,
        modifier,
        minWidth = if (cardDrawn) cardMaxWidth else 0.dp,
        horizontalPadding = surround,
        attention = attention,
    ) {
        val linkStyle = SpanStyle(
            color = CodeTheme.colors.textMain,
            textDecoration = TextDecoration.Underline,
        )
        // The pill is what marks a mention, so its letters stay the body's own. A tappable span
        // with no style of its own is drawn as a web link, underlined in the platform's colour.
        val mentionStyle = SpanStyle(
            color = CodeTheme.colors.textMain,
            textDecoration = TextDecoration.None,
        )
        // Read through the latest handler, so a recomposition that hands over a new lambda does
        // not rebuild the body; only whether mentions can be tapped at all is part of its key.
        val currentOnMentionClick by rememberUpdatedState(onMentionClick)
        val mentionsTappable = onMentionClick != null
        // A tombstone carries no link and nothing worth selecting; it is a notice, not a message.
        val mentioned = if (isTombstone) {
            null
        } else {
            remember(bodyString, mentions, linkStyle, mentionStyle, mentionsTappable) {
                buildMentionedText(
                    text = bodyString,
                    mentions = mentions,
                    linkStyle = linkStyle,
                    mentionStyle = mentionStyle,
                    onMentionClick = if (mentionsTappable) {
                        { username -> currentOnMentionClick?.invoke(username) }
                    } else {
                        null
                    },
                )
            }
        }
        val body = mentioned?.text ?: AnnotatedString(bodyString)
        val handles = mentioned?.handles.orEmpty()
        val bodyStyle = CodeTheme.typography.textMessage.copy(
            fontStyle = if (isTombstone) FontStyle.Italic else FontStyle.Normal,
        )
        val bodyColor = if (isTombstone) {
            CodeTheme.colors.textSecondary
        } else {
            CodeTheme.colors.textMain
        }

        val marker = rememberEditedMarker()
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val laidOut = if (isEdited) marker.reserveSpace(body) else body
        val inlineContent = buildMap {
            if (isEdited) {
                val (slot, placeholder) = marker.inlineContent()
                put(slot, placeholder)
            }
            if (handles.isNotEmpty()) {
                val (slot, spacing) = mentionSpacingContent(density)
                put(slot, spacing)
            }
        }
        var bodyLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

        // No SelectionContainer: long-press is the transcript's selection gesture, and a text
        // selection handle inside the bubble would consume it before the row ever sees it. Copying
        // a message is the selection bar's Copy action instead — the same trade WhatsApp makes.
        // The citation sits inside the bubble, above the body, so the two move together and the
        // reply reads as one message rather than as a quote with a message under it.
        val redaction = if (redacted) Modifier.redactedText() else Modifier
        val bodyText = @Composable {
            Text(
                modifier = Modifier
                    .padding(horizontal = bodyInset)
                    .then(redaction)
                    .widestLine(measurer, laidOut, bodyStyle, inlineContent)
                    .then(
                        if (handles.isEmpty()) {
                            Modifier
                        } else {
                            Modifier.drawBehind { bodyLayout?.let { drawMentionPills(it, handles) } }
                        }
                    ),
                text = laidOut,
                inlineContent = inlineContent,
                style = bodyStyle,
                color = if (redacted) redactedTextColor(bodyColor) else bodyColor,
                onTextLayout = { bodyLayout = it },
            )
        }

        Column {
            // The marker is pinned to the text's corner, so the card below does not sit under it.
            Box(modifier = Modifier.addIf(cardDrawn) { Modifier.fillMaxWidth() }) {
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
                                onDoubleClick = onQuoteDoubleClick,
                                // Tagged because the citation repeats the quoted message's own text, so a
                                // UI test matching on that text cannot tell the two apart.
                                modifier = Modifier.testTag(REPLY_QUOTE_TAG),
                            )
                        },
                        body = bodyText,
                    )
                }

                if (isEdited) {
                    with(marker) { Label(endPadding = bodyInset) }
                }
            }
            if (web != null) {
                WebLinkCard(
                    state = web,
                    modifier = Modifier.padding(horizontal = bodyInset),
                    onLongClick = onCardLongClick,
                    onDoubleClick = onCardDoubleClick,
                    interactive = interactive,
                )
            }
        }
    }
}

/**
 * A resolved web card that is the whole message: no bubble and no link text, the card in the
 * bubble's corners for its place in the run. The panel's own fill is the surface.
 *
 * Laid out as [BareLinkCard] is -- through [Bubble] with `bare`, so the jump flash and the width
 * ceiling stay where every bubble gets them, and a reply's citation above the card.
 *
 * Only reached while the card is resolved; a link with no preview draws as the text bubble.
 */
@Composable
private fun BareWebCard(
    web: WebLinkCardState,
    isFromSelf: Boolean,
    position: BubblePosition,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    quote: ChatQuote? = null,
    onQuoteClick: (() -> Unit)? = null,
    onQuoteLongClick: (() -> Unit)? = null,
    onQuoteDoubleClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    interactive: Boolean = true,
    attention: () -> Float = { 0f },
) {
    val shape = bubbleShape(position, isFromSelf)
    Bubble(
        isFromSelf = isFromSelf,
        position = position,
        maxWidth = maxWidth,
        minWidth = maxWidth,
        modifier = modifier,
        shape = shape,
        bare = true,
        horizontalPadding = 0.dp,
        verticalPadding = 0.dp,
        attention = attention,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(BubbleDefaults.surroundInset)) {
            if (quote != null) {
                ChatQuotePanel(
                    quote = quote,
                    onClick = onQuoteClick,
                    onLongClick = onQuoteLongClick,
                    onDoubleClick = onQuoteDoubleClick,
                    modifier = Modifier.testTag(REPLY_QUOTE_TAG),
                )
            }
            WebLinkCard(
                state = web,
                onLongClick = onLongClick,
                onDoubleClick = onDoubleClick,
                interactive = interactive,
                bareShape = shape,
            )
        }
    }
}


/**
 * The card row of a split message, with no bubble behind it.
 *
 * Everything the bubble would have contributed is already the card's: the fill, the corners and the
 * tap. The corners are still the bubble's -- [bubbleShape] at this row's [position] -- handed to the
 * card so its frame, its pieces and its border all follow them, and it squares off against its
 * neighbours in a run the way a text bubble does.
 *
 * What is not the card's is the flash a jump leaves on the message it landed on, which belongs to
 * the transcript rather than to the bubble -- so this goes through [Bubble] as the jumbo
 * emoji does, with `bare` dropping the fill and the horizontal inset and leaving the flash, the
 * width ceiling and the corner clip where every other bubble already gets them.
 *
 * A reply whose link came first carries its citation here, above the card, on the same gap the
 * card keeps from its neighbours: the row has no bubble for the panel to sit inside.
 */
@Composable
internal fun BareLinkCard(
    card: LinkCard,
    isFromSelf: Boolean,
    position: BubblePosition,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    quote: ChatQuote? = null,
    onQuoteClick: (() -> Unit)? = null,
    onQuoteLongClick: (() -> Unit)? = null,
    onQuoteDoubleClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    interactive: Boolean = true,
    attention: () -> Float = { 0f },
) {
    val onCardClick = rememberLinkCardClick()
    val shape = bubbleShape(position, isFromSelf)
    Bubble(
        isFromSelf = isFromSelf,
        position = position,
        maxWidth = maxWidth,
        modifier = modifier,
        shape = shape,
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
        Column(
            // A person's card hugs its text, so the column is as wide as the wider of it and a
            // reply's quote, and the card fills that. Every other card fills the bubble's width.
            modifier = Modifier.addIf(card is LinkCard.User) { Modifier.width(IntrinsicSize.Max) },
            verticalArrangement = Arrangement.spacedBy(BubbleDefaults.surroundInset),
        ) {
            if (quote != null) {
                ChatQuotePanel(
                    quote = quote,
                    onClick = onQuoteClick,
                    onLongClick = onQuoteLongClick,
                    onDoubleClick = onDoubleClick,
                    modifier = Modifier.testTag(REPLY_QUOTE_TAG),
                )
            }
            LinkCardView(
                card = card,
                modifier = Modifier.addIf(card is LinkCard.User) { Modifier.fillMaxWidth() },
                shape = shape,
                // Dropped with the backdrop up, like every other target on the row.
                onClick = if (interactive) onCardClick else null,
                // The card takes the press for its own tap, so it has to hand the transcript's
                // selection gesture back or a long press on it would select nothing.
                onLongClick = onLongClick,
                onDoubleClick = onDoubleClick,
            )
        }
    }
}

/**
 * What a tap on a card does.
 *
 * A cash link is handed to the chat, which opens it through the URL handler its link span used
 * unless the reader cannot collect from where they are — a non-member reading a group. So the card
 * changed how the message looks and not what tapping it does, except for that reader.
 *
 * A token link does not. Its URL classifies as a deep link, and the router answers that with the
 * wallet sheet plus the token's card expanded in place — right for a link arriving from outside the
 * app, wrong from inside a chat, where it swaps the transcript for the wallet on the way to a
 * screen the reader asked for directly. There is no wallet card here for the detail to grow out of.
 * So it pushes, exactly as the cash bubble's own token tap does, and back returns to the message.
 * A group invite pushes the group's profile (its conversation until the card has resolved) for
 * the same reason.
 */
@Composable
private fun rememberLinkCardClick(): (LinkCard) -> Unit {
    val actionHandler = LocalChatActionHandler.current
    val uriHandler = LocalUriHandler.current
    return { card ->
        when (card) {
            // The transcript's handler follows a message link in the app: a jump in place when it
            // points into this chat, a push with the way back otherwise.
            is LinkCard.Message -> uriHandler.openUri(card.url)
            // The same handler opens the invite landing in the app, never the browser: the
            // fragment is the group's key. The landing picks the preview, a dead-link or
            // not-found screen, or the chat for a member.
            is LinkCard.InviteLink -> uriHandler.openUri(card.url)
            // The chat opens it, because only the chat knows whether this reader may collect it.
            // A voucher already being claimed would only start the same claim again.
            is LinkCard.Cash -> if ((card.state as? LinkCard.Cash.State.Resolved)?.claim != LinkCard.Cash.Claim.Claiming) {
                actionHandler(ChatAction.CashLinkOpened(card.entropy, card.url))
            }
            is LinkCard.TokenInfo -> actionHandler(ChatAction.ViewToken(card.mint))
            // Pushed over this chat rather than through the chat deep link, which replaces the
            // stack: Back has to return to the message that held the invite.
            is LinkCard.GroupInvite -> actionHandler(
                ChatAction.OpenGroup(
                    chatId = card.chatId,
                    resolved = card.state is LinkCard.GroupInvite.State.Resolved,
                )
            )
            // Only a resolved card takes a tap. Which screen it opens -- the person's DM, your own
            // tip card, or nothing for the person already on the other end -- is the chat's call.
            // A web card's own tap goes through LocalUriHandler (Task 8); nothing to do here.
            is LinkCard.Web -> Unit
            is LinkCard.User -> (card.state as? LinkCard.User.State.Resolved)?.let {
                actionHandler(ChatAction.OpenUser(userId = it.userId, profile = it.profile, isOwn = it.isOwn))
            }
        }
    }
}

/**
 * A cash message in the transcript (`docs/specs/cash-bubbles.md`, node 13266:348), centered top to
 * bottom: who did what, the amount, the total for a group send to two or more people, the token,
 * and who it went to or came from.
 *
 * A group send to two or more people ([MessageContent.Cash.multiReceiver]) leads with what each
 * person got, "$1.00 each", with the total under it. Anything else, a DM or a group send to one
 * person, shows the whole amount alone.
 */
@Composable
private fun CashBubble(
    content: MessageContent.Cash,
    lines: CashBubbleLines,
    amount: CashAmountLine,
    isFromSelf: Boolean,
    position: BubblePosition,
    maxWidth: Dp,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    onDoubleClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    attention: () -> Float = { 0f },
    sendBack: SendBackRow? = null,
    onSendBack: (() -> Unit)? = null,
) {
    // A Send Back row or Sent Back footer spans the bubble edge to edge along its bottom, so the
    // bubble draws no inset of its own and the lines above carry it on every side, row or no row.
    // Both sit below the bottom padding, so the last line has the same gap above either. Keeping the
    // inset inside the animated column means a row arriving only adds its own height.
    val insetH = BubbleDefaults.paddingHorizontal
    val insetV = BubbleDefaults.paddingVertical
    Bubble(
        isFromSelf = isFromSelf,
        position = position,
        minWidth = maxWidth,
        maxWidth = maxWidth,
        onClick = onClick,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        modifier = modifier,
        attention = attention,
        horizontalPadding = Dp.Hairline,
        verticalPadding = Dp.Hairline,
    ) {
        // The same number the bill's "You received" sheet reads: a USDF payment converts from the
        // USD it settled at, anything else from today's rates.
        val whole = cashInDisplayCurrency(content.amount, content.settledUsd())
        val parts = content.multiReceiver
        val secondary = CodeTheme.typography.caption.copy(fontWeight = FontWeight.Medium)
        val amountStyle = CodeTheme.typography.displayMedium

        // Any line that lands after the bubble has drawn (the counterpart once names resolve, the
        // token, the Send Back row or footer, the row giving way to Sent Back) moves the bubble's
        // height on the transcript's reflow spring. The first measure is taken as is, so what is
        // known when the bubble first draws is there at full height. The bubble's width is fixed,
        // so only the height moves.
        Column(modifier = Modifier.fillMaxWidth().animateContentSize(ChatAnimations.reflowIntSize)) {
        // The gaps use the static grid, like the bubble's own padding, so each spec value maps
        // down to the same 5dp step at every screen width.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = insetH, vertical = insetV)
                .padding(vertical = CodeTheme.dimens.staticGrid.x1),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = lines.caption.text(),
                style = secondary,
                color = CodeTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // The amount and total read as one block: the total sits right under the amount, the
            // tightest pair in the bubble, and the currency sits a small gap below the money, the
            // same gap whether or not there is a total.
            Column(
                modifier = Modifier.padding(top = CodeTheme.dimens.staticGrid.x2),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (parts.size >= 2) {
                    when (amount) {
                        is CashAmountLine.Each -> {
                            val each = content.partInDisplayCurrency(amount.part).formatted()
                            val eachLine = amount.text(each)
                            val secondaryColor = CodeTheme.colors.textSecondary
                            Text(
                                text = remember(eachLine, each, secondaryColor) {
                                    eachAmountText(eachLine, each, secondaryColor)
                                },
                                style = amountStyle,
                                color = CodeTheme.colors.textMain,
                                autoSize = TextAutoSize.StepBased(
                                    minFontSize = 20.sp,
                                    maxFontSize = amountStyle.fontSize,
                                ),
                                maxLines = 1,
                            )
                        }

                        CashAmountLine.Whole -> Text(
                            text = whole.formatted(),
                            style = amountStyle,
                            color = CodeTheme.colors.textMain,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 20.sp,
                                maxFontSize = amountStyle.fontSize,
                            ),
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = stringResource(Res.string.label_groupSendTotal, whole.formatted()),
                        style = secondary,
                        color = CodeTheme.colors.textSecondary,
                        maxLines = 1,
                    )
                } else {
                    Text(
                        text = whole.formatted(),
                        style = amountStyle,
                        color = CodeTheme.colors.textMain,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 20.sp,
                            maxFontSize = amountStyle.fontSize,
                        ),
                        maxLines = 1,
                    )
                }

                val currencyGap = Modifier.padding(top = CodeTheme.dimens.staticGrid.x1)
                // A preview has no token image loader, so it draws the token row's shape instead.
                if (LocalInspectionMode.current) {
                    Row(
                        modifier = currencyGap,
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
                } else if (content.tokenName.isNotBlank()) {
                    TokenIconWithName(
                        modifier = currencyGap,
                        tokenName = content.tokenName,
                        tokenImage = content.tokenImageUrl,
                        imageSize = CodeTheme.dimens.staticGrid.x4,
                        spacing = CodeTheme.dimens.grid.x1,
                        textStyle = CodeTheme.typography.caption,
                        textColor = CodeTheme.colors.textMain,
                    )
                }
            }

            lines.counterpart?.let { counterpart ->
                Text(
                    modifier = Modifier.padding(top = CodeTheme.dimens.staticGrid.x2),
                    text = counterpart.text(),
                    style = secondary,
                    color = CodeTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }

        }

        SendBackFooter(sendBack = sendBack, onSendBack = onSendBack)
        }
    }
}

/**
 * The Send Back row or Sent Back footer along a cash bubble's bottom edge, or nothing.
 *
 * Present when the bubble first draws, it is drawn as is. Arriving or changing after that (the
 * return state loads after the transcript does, or a return lands), it fades in on the reflow
 * spring while the bubble's [animateContentSize] grows or shrinks the room for it, so the size is
 * animated once, by the bubble.
 */
@Composable
private fun SendBackFooter(
    sendBack: SendBackRow?,
    onSendBack: (() -> Unit)?,
) {
    val drawn = remember { SendBackDrawn(first = sendBack) }
    if (sendBack != drawn.first) drawn.changed = true
    key(sendBack) {
        val alpha = remember { Animatable(if (drawn.changed) 0f else 1f) }
        LaunchedEffect(Unit) { alpha.animateTo(1f, ChatAnimations.reflowFloat) }
        Column(modifier = Modifier.graphicsLayer { this.alpha = alpha.value }) {
            when (sendBack) {
                SendBackRow.SentBack -> {
                    // The divider every sheet and card in the app draws between sections, edge to
                    // edge: the bubble drops its horizontal inset for this footer.
                    HorizontalDivider(
                        modifier = Modifier.testTag(SENT_BACK_DIVIDER_TAG),
                        thickness = CodeTheme.dimens.border,
                        color = CodeTheme.colors.divider,
                    )
                    Box(
                        modifier = Modifier
                            .testTag(SENT_BACK_FOOTER_TAG)
                            .fillMaxWidth()
                            .height(CodeTheme.dimens.grid.x9),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.label_sentBack),
                            style = CodeTheme.typography.caption,
                            color = CodeTheme.colors.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
                SendBackRow.Available, SendBackRow.Sending -> SendBackActionRow(
                    sending = sendBack == SendBackRow.Sending,
                    onClick = onSendBack,
                )
                null -> Unit
            }
        }
    }
}

/** Not state: [SendBackFooter] writes [changed] while composing, only when its Send Back state moved off [first]. */
private class SendBackDrawn(val first: SendBackRow?) {
    var changed = false
}

/**
 * The white row along a cash bubble's bottom edge that offers Send Back, or holds a spinner while
 * the return is submitted. Not clickable while sending, or when there is nothing to call.
 */
@Composable
private fun SendBackActionRow(
    sending: Boolean,
    onClick: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .testTag(SEND_BACK_ROW_TAG)
            .fillMaxWidth()
            .height(CodeTheme.dimens.grid.x11)
            .background(CodeTheme.colors.textMain)
            .then(
                if (!sending && onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (sending) {
            CodeCircularProgressIndicator(
                modifier = Modifier.size(CodeTheme.dimens.grid.x4),
                color = CodeTheme.colors.background,
                strokeWidth = CodeTheme.dimens.thickBorder,
            )
        } else {
            Text(
                text = stringResource(Res.string.action_sendBack),
                style = CodeTheme.typography.textSmall.copy(fontWeight = FontWeight.SemiBold),
                color = CodeTheme.colors.background,
            )
        }
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

/**
 * [text] as the transcript would draw it: the bubble split around its card, top row first, with
 * the positions the list would give each row.
 */
@Composable
private fun PreviewSplitMessage(
    text: String,
    card: LinkCard,
    isFromSelf: Boolean = false,
    isEdited: Boolean = false,
) {
    val rows = ChatListItem.ContentBubble(
        messageId = 1,
        contentIndex = 0,
        content = MessageContent.Text(text),
        isFromSelf = isFromSelf,
        timestamp = Instant.fromEpochSeconds(0),
        isEdited = isEdited,
        linkCard = card,
    ).splitAroundLinkCard()
    Column(
        modifier = Modifier.width(360.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row -> ContentBubble(item = row, position = BubblePosition.Solo) }
    }
}

/**
 * What the card looks like while its lookup is out — and so what every cash link looks like for the
 * moment after the transcript paints, since the transcript no longer waits for the answer.
 *
 * The default `LocalLinkCardResolution` hands a card back untouched, so a preview draws the state
 * it names rather than resolving out from under itself.
 */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_LinkCard_Loading() {
    PreviewSplitMessage(
        text = PREVIEW_CASH_LINK,
        card = previewCard(state = LinkCard.Cash.State.Loading, text = PREVIEW_CASH_LINK),
    )
}

/** A lookup that failed: the loading card with the shimmer stopped, and nothing else moved. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_LinkCard_Unresolved() {
    PreviewSplitMessage(
        text = PREVIEW_CASH_LINK,
        card = previewCard(state = LinkCard.Cash.State.Unresolved, text = PREVIEW_CASH_LINK),
    )
}

/** Text before the link: a text row above a bare card row. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_LinkCard_Claimable() {
    PreviewSplitMessage(
        text = PREVIEW_CASH_TEXT,
        card = previewCard(
            state = LinkCard.Cash.State.Resolved(
                amount = "$5.00",
                claim = LinkCard.Cash.Claim.Claimable,
                token = Token.usdf,
            ),
        ),
    )
}

/**
 * Text on both sides of the link, edited: three rows, with "Edited" inside the last bubble only and
 * a second link left underlined in the trailing row.
 */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_LinkCard_SplitMessage() {
    val text = "sent you this $PREVIEW_CASH_LINK go grab it before https://flipcash.app closes"
    PreviewSplitMessage(
        text = text,
        isFromSelf = true,
        isEdited = true,
        card = previewCard(
            text = text,
            state = LinkCard.Cash.State.Resolved(
                amount = "$5.00",
                claim = LinkCard.Cash.Claim.Claimed,
                token = Token.usdf,
            ),
        ),
    )
}

/**
 * A group invite split around its card, above a cash link's card: the two cards at one width, the
 * group's at least the cash card's height, and only the group's button taking a tap.
 */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_LinkCard_GroupInviteBesideCash() {
    val group = previewGroupCard(PreviewGroupResolved)
    val text = "come hang ${group.url} we're planning friday"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PreviewSplitMessage(
            text = text,
            card = group.copy(start = text.indexOf(group.url), end = text.indexOf(group.url) + group.url.length),
        )
        PreviewSplitMessage(
            text = PREVIEW_CASH_LINK,
            isFromSelf = true,
            card = previewCard(
                text = PREVIEW_CASH_LINK,
                state = LinkCard.Cash.State.Resolved(
                    amount = "$5.00",
                    claim = LinkCard.Cash.Claim.Claimable,
                    token = Token.usdf,
                ),
            ),
        )
    }
}

/**
 * A person's link, trailing punctuation and all, between a group invite and a cash link: the person
 * card hugs its text where the other two fill the column.
 */
@Preview(heightDp = 900)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_LinkCard_UserBesideGroupAndCash() {
    val group = previewGroupCard(PreviewGroupResolved)
    val user = previewUserCard(previewUserResolved())
    val userText = "say hi to ${user.url}."
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PreviewSplitMessage(
            text = userText,
            card = user.copy(start = userText.indexOf(user.url), end = userText.indexOf(user.url) + user.url.length),
        )
        PreviewSplitMessage(text = group.url, card = group)
        PreviewSplitMessage(
            text = PREVIEW_CASH_LINK,
            isFromSelf = true,
            card = previewCard(
                text = PREVIEW_CASH_LINK,
                state = LinkCard.Cash.State.Resolved(
                    amount = "$5.00",
                    claim = LinkCard.Cash.Claim.Claimable,
                    token = Token.usdf,
                ),
            ),
        )
    }
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

private fun previewCash(
    amount: Double,
    tokenName: String = "Jeffy",
    receiver: List<Byte>? = null,
    recipients: Int = 0,
    action: MessageContent.Cash.Action = MessageContent.Cash.Action.SENT,
) = MessageContent.Cash(
    intentId = listOf(0),
    amount = Fiat(fiat = amount * maxOf(recipients, 1)),
    mint = Mint.usdf,
    tokenName = tokenName,
    action = action,
    receiver = receiver,
    multiReceiver = List(recipients) { MessageContent.Cash.Payment(listOf(it.toByte()), amount, 0L) },
)

@Composable
private fun PreviewCashBubble(
    content: MessageContent.Cash,
    isFromSelf: Boolean,
    viewerPaid: Boolean = false,
    people: CashBubblePeople?,
    sendBack: SendBackRow? = null,
) {
    CompositionLocalProvider(
        LocalExchange provides ExchangeStub()
    ) {
        CashBubble(
            content = content,
            lines = content.bubbleLines(isFromSelf = isFromSelf, viewerPaid = viewerPaid, people = people),
            amount = content.amountLine(),
            isFromSelf = isFromSelf,
            position = BubblePosition.Solo,
            maxWidth = 250.dp,
            onClick = null,
            onLongClick = null,
            onDoubleClick = null,
            sendBack = sendBack,
            onSendBack = {},
        )
    }
}

/** Send Back frames S1, S2, S5 and S6, and the return bubble both ways. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_CashBubble_SendBack() {
    val offered = previewCash(5.0).copy(
        paymentReturn = MessageContent.Cash.PaymentReturn(offer = MessageContent.Cash.PaymentReturn.Offer),
    )
    val returned = previewCash(5.0).copy(
        paymentReturn = MessageContent.Cash.PaymentReturn(returnOf = MessageContent.Cash.PaymentReturn.ReturnOf(1)),
    )
    val fred = CashBubblePeople.Direct("Fred Wilson")
    Column(verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)) {
        PreviewCashBubble(offered, isFromSelf = false, people = fred, sendBack = SendBackRow.Available)
        PreviewCashBubble(offered, isFromSelf = false, people = fred, sendBack = SendBackRow.Sending)
        PreviewCashBubble(offered, isFromSelf = false, people = fred, sendBack = SendBackRow.SentBack)
        PreviewCashBubble(offered, isFromSelf = true, people = fred, sendBack = SendBackRow.SentBack)
        PreviewCashBubble(returned, isFromSelf = true, people = fred)
        PreviewCashBubble(returned, isFromSelf = false, people = fred)
    }
}

/** Frame C. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_CashBubble_DmSender() {
    PreviewCashBubble(previewCash(5.0), isFromSelf = true, people = CashBubblePeople.Direct("Noah Parker"))
}

/** Frame D, as a received tip. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_CashBubble_DmTipReceived() {
    PreviewCashBubble(
        previewCash(5.0, action = MessageContent.Cash.Action.TIPPED),
        isFromSelf = false,
        people = CashBubblePeople.Direct("Noah Parker"),
    )
}

/** Frame A. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_CashBubble_GroupSender() {
    PreviewCashBubble(
        previewCash(1.0, recipients = 3),
        isFromSelf = true,
        people = CashBubblePeople.Group(sender = null, leadRecipient = "Alex Morgan"),
    )
}

/** Frame B. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_CashBubble_GroupRecipient() {
    PreviewCashBubble(
        previewCash(1.0, recipients = 3),
        isFromSelf = false,
        viewerPaid = true,
        people = CashBubblePeople.Group(sender = "Fred Wilson", leadRecipient = "Alex Morgan"),
    )
}

/** Frame G3. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_CashBubble_GroupOneMemberNotPaid() {
    PreviewCashBubble(
        previewCash(5.0, receiver = listOf(9)),
        isFromSelf = false,
        people = CashBubblePeople.Group(sender = "Fred Wilson", leadRecipient = "Noah Parker"),
    )
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
        LocalExchange provides ExchangeStub()
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
                PreviewCashBubble(previewCash(5.0), isFromSelf = false, people = CashBubblePeople.Direct("Noah Parker"))
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
