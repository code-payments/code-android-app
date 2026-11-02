package com.flipcash.shared.chat.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.flipcash.shared.chat.models.MessagePart
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.LazyPagingItems
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.SeparatorConfig
import com.getcode.theme.CodeTheme
import com.getcode.theme.cornerRadius
import com.getcode.theme.tiny
import com.getcode.ui.core.addIf

enum class BubblePosition { Solo, First, Middle, Last }

private const val BUBBLE_MAX_WIDTH_FRACTION = 0.78f

internal const val CASH_CARD_WIDTH_FRACTION = 0.64f

/**
 * The widest a bubble or link card grows in a transcript whose rows are inset by [rowInset] on each
 * side.
 *
 * A fraction of the transcript's full width rather than of the inset row, which is how iOS sizes
 * its bubbles, link cards, and group head card (`ChatViewController.maxBubbleWidthFraction`). Taken
 * of the inset row, they came out about 30dp narrower than iOS's on the same screen.
 */
fun transcriptBubbleMaxWidth(rowWidth: Dp, rowInset: Dp): Dp =
    (rowWidth + rowInset * 2) * BUBBLE_MAX_WIDTH_FRACTION

/**
 * The widest a bubble carrying [content] grows in a row [rowWidth] wide, inset by [rowInset] on each
 * side. A cash card keeps its own narrower fraction of the inset
 * row (iOS's `ChatViewController.cashCardWidthFraction` matches it); everything else takes
 * [transcriptBubbleMaxWidth].
 */
fun bubbleMaxWidth(content: MessageContent, rowWidth: Dp, rowInset: Dp): Dp = when (content) {
    is MessageContent.Cash -> rowWidth * CASH_CARD_WIDTH_FRACTION
    else -> transcriptBubbleMaxWidth(rowWidth = rowWidth, rowInset = rowInset)
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
internal fun JumboEmoji(
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
internal fun Bubble(
    isFromSelf: Boolean,
    position: BubblePosition,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    minWidth: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    horizontalPadding: Dp = BubbleDefaults.paddingHorizontal,
    verticalPadding: Dp = BubbleDefaults.paddingVertical,
    bare: Boolean = false,
    shape: Shape = bubbleShape(position, isFromSelf),
    attention: () -> Float = { 0f },
    content: @Composable BoxScope.() -> Unit,
) {
    val bubble = if (isFromSelf) {
        CodeTheme.colors.chat.outgoingBubble
    } else {
        CodeTheme.colors.chat.incomingBubble
    }
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
            .addIf(onClick != null || onLongClick != null || onDoubleClick != null) {
                // combinedClickable rather than two modifiers: a bubble that takes the tap takes
                // the long press with it, so both gestures are reported from the same target.
                // A double tap here waits out the timeout before a single tap acts: that delay is
                // the price of the tapback on a bubble whose tap already opens something.
                Modifier.clip(shape).combinedClickable(
                    onLongClick = onLongClick,
                    onDoubleClick = onDoubleClick,
                    onClick = { onClick?.invoke() },
                    // The row's select plays the tick; a second, platform haptic doubles it.
                    hapticFeedbackEnabled = false,
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
fun bubbleShape(position: BubblePosition, isFromSelf: Boolean): RoundedCornerShape {
    val cornerSpec = spring<Dp>(dampingRatio = 0.68f, stiffness = 500f)

    val targets = bubbleCorners(position, isFromSelf)

    val topStart by animateDpAsState(targets.topStart, cornerSpec, label = "cTS")
    val topEnd by animateDpAsState(targets.topEnd, cornerSpec, label = "cTE")
    val bottomEnd by animateDpAsState(targets.bottomEnd, cornerSpec, label = "cBE")
    val bottomStart by animateDpAsState(targets.bottomStart, cornerSpec, label = "cBS")

    return RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
}

/** The corners [bubbleShape] settles on for a bubble at [position]. */
@Composable
internal fun bubbleCorners(position: BubblePosition, isFromSelf: Boolean): BubbleCorners {
    val l = BubbleDefaults.cornerLarge
    val s = BubbleDefaults.cornerSmall

    return when (position) {
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
}

internal data class BubbleCorners(
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
 * with the other half missing. Either side being an emoji is enough, so the message under one
 * closes its top corners the same way the message above it closes its bottom ones.
 *
 * A card row does not break it. The card is drawn in the bubble's outline (see [BareLinkCard]), so
 * it has the edge an emoji lacks, and an invite -- a card and then the note sent with it -- reads
 * as one run rather than two unrelated messages.
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

/**
 * [receiptAbove] and [receiptBelow] say whether a status line sits between [item] and the bubble
 * above or below it. A line ends the run there, so the two keep their round corners.
 */
fun bubblePositionOf(
    index: Int,
    item: ChatListItem.ContentBubble,
    messages: LazyPagingItems<ChatListItem>,
    config: SeparatorConfig,
    receiptAbove: Boolean = false,
    receiptBelow: Boolean = false,
): BubblePosition {
    val above = if (index + 1 < messages.itemCount) {
        messages.peek(index + 1) as? ChatListItem.ContentBubble
    } else null
    val below = if (index > 0) {
        messages.peek(index - 1) as? ChatListItem.ContentBubble
    } else null

    val groupedAbove = groupsWith(item, above, config) && !receiptAbove
    val groupedBelow = groupsWith(item, below, config) && !receiptBelow

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

/** How white a bubble goes at the peak of the flash a jump leaves on it. */
internal const val ATTENTION_SCRIM_ALPHA = 0.14f

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
    if (part == MessagePart.Card) return false
    val text = when (val content = content) {
        is MessageContent.Text -> content.text
        // Only the row that draws the citation keeps its bubble for it; the other rows of a split
        // reply are text like any other.
        is MessageContent.Reply -> if (quote != null && isFirstRow) {
            return false
        } else {
            content.content.filterIsInstance<MessageContent.Text>().firstOrNull()?.text.orEmpty()
        }

        else -> return false
    }
    return EmojiOnlyText.clusterCountOrNull(partText ?: text) != null
}

/**
 * Whether this row draws with no bubble behind it: a bare emoji, or the card row of a split
 * message. Either one moves the "Edited" marker to the line under it; only the emoji breaks the
 * bubble run (see [groupsWith]).
 */
fun ChatListItem.ContentBubble.rendersBare(): Boolean =
    part == MessagePart.Card || rendersBareEmoji()
