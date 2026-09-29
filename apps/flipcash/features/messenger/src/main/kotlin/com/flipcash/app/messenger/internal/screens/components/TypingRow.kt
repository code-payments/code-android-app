package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp
import androidx.paging.compose.LazyPagingItems
import com.flipcash.app.messenger.internal.TypingAvatar
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.shared.chat.ActiveTypist
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.ui.ChatAnimations
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.opencode.model.core.ID
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.TypingIndicator
import kotlinx.datetime.Instant
import kotlin.math.roundToInt

/**
 * Whether the newest of [arrivals] grows out of the typing dots rather than being inserted above
 * them. [arrivals] are the senders of the messages that just landed, oldest first, with null for the
 * viewer's own; [typingBefore] is who the dots showed before they landed; [typing] is who is typing
 * now, before the arrivals clear their senders.
 *
 * The shared rule in the orchestrator's `docs/cross-platform-parity.md` ("typing-dots handoff with
 * several typists"): the newest arrival takes the dots' frame only if its sender was typing and
 * nobody is left typing once the arrivals are counted. Otherwise the dots would have to reappear
 * under the message in the same update, which moves the transcript twice.
 */
internal fun <T : Any> takesTypingDots(
    typingBefore: Set<T>,
    arrivals: List<T?>,
    typing: Set<T>,
): Boolean {
    val newest = arrivals.lastOrNull() ?: return false
    return newest in typingBefore && (typing - arrivals.filterNotNull().toSet()).isEmpty()
}

/**
 * The gap between the newest message and the typing dots under it. The dots never join a run: a
 * normal gap under a typist's own bubble and a wide one under anyone else's, so they read as a new
 * turn, as iOS spaces them (`ChatViewController.interItemSpacing`).
 */
internal fun gapAboveTypingRow(newest: ChatListItem?, typists: Collection<ID>): RowGap {
    val bubble = newest as? ChatListItem.ContentBubble ?: return RowGap.Normal
    return if (!bubble.isFromSelf && bubble.authorId in typists) RowGap.Normal else RowGap.Wide
}

/**
 * The newest row opening from [fromPx] — the height the typing dots had under the row it replaced —
 * to its own height, so the rows above ride up with it instead of jumping. [handoff] is set when the
 * row's bubble grows out of the dots themselves.
 */
@Immutable
internal data class TypingGrow(
    val key: Any,
    val fromPx: Int,
    val handoff: TypingDotsFrame?,
)

/** The typing bubble's size when a message took it over, in px. */
@Immutable
internal data class TypingDotsFrame(val size: Size)

/** What [TypingRowModel.update] decided for this frame. */
@Immutable
internal data class TypingRowFrame(
    /** Who the dots show, oldest first. Empty once nobody is typing. */
    val typists: List<ID>,
    /**
     * Who the dots showed last, the same as [typists] until nobody is typing. The dots close under
     * these, so their gap doesn't change while they go.
     */
    val lastTypists: List<ID>,
    /** The newest row opening from under the dots, until it has. */
    val grow: TypingGrow?,
    /** The dots were handed to a message this frame and leave with no exit of their own. */
    val handedOff: Boolean,
    /** A message opened from under the dots this frame; the list must rest on it for the layout. */
    val anchorNewest: Boolean,
)

/**
 * The typing row's bookkeeping across the frames the paged transcript changes in.
 *
 * Plain fields, folded in from `MessageList`'s composition: [update] runs whenever the typists or the
 * paged snapshot change, both of which already recompose the list, and hands its decision down as an
 * immutable [TypingRowFrame] the items read. The measured sizes are written from layout and only
 * read at the moment a message arrives.
 */
internal class TypingRowModel {
    private data class Session(val userId: ID, val since: Instant)

    private var newestKey: Any? = null
    /** Typing sessions whose message has landed in the transcript. The dots drop them at once. */
    private val landed = mutableSetOf<Session>()
    private var shown: Set<ID> = emptySet()
    private var lastShown: List<ID> = emptyList()
    private var grow: TypingGrow? = null
    private var handOffPending = false
    private var anchorPending = false
    private val grown = mutableSetOf<Any>()

    /** The dots row's height at full presence, gap above included, as last measured. */
    var slotPx: Int = 0
    /** The dots bubble's size, as last measured. */
    var dotsSize: Size = Size.Zero

    /**
     * Folds [typing] and the paged snapshot into the frame, running [arrive] (a call to [arrive]
     * with the list's measurements) first in case this composition is the first to see a message.
     */
    fun update(typing: Set<ActiveTypist>, arrive: () -> Unit): TypingRowFrame {
        arrive()
        val handedOff = handOffPending
        handOffPending = false
        val anchor = takeAnchor()

        landed.retainAll(typing.map { it.session() }.toSet())
        val next = typing
            .filterNot { it.session() in landed }
            .sortedWith(compareBy({ it.since }, { it.userId.toString() }))
            .map { it.userId }
        shown = next.toSet()
        if (next.isNotEmpty()) lastShown = next
        return TypingRowFrame(
            typists = next,
            lastTypists = lastShown,
            grow = growFor(newestKey),
            handedOff = handedOff,
            anchorNewest = anchor,
        )
    }

    /**
     * Decides, once per newest message, whether it opens from under the dots and whether it takes
     * them over. Idempotent, and called both from [update] and from the arrival's own row: the lazy
     * list can lay a new page out before the composition that runs [update] sees it, and by then it
     * has moved its scroll position on. The row composes inside that
     * layout, while the list still describes the old one.
     *
     * [dotsHeightPx] is how tall the dots stand under the newest row right now, and [resting] says
     * whether the transcript sits on the row that was newest before the arrival, which is now at
     * the index given.
     */
    fun arrive(
        typing: Set<ActiveTypist>,
        messages: LazyPagingItems<ChatListItem>,
        dotsHeightPx: () -> Int,
        resting: (previousKey: Any, previousIndex: Int) -> Boolean,
    ) {
        val newest = if (messages.itemCount > 0) messages.peek(0) else null
        val newestBubble = newest as? ChatListItem.ContentBubble
        if (newestBubble != null && newestBubble.itemKey != newestKey) {
            val previous = newestKey
            newestKey = newestBubble.itemKey
            val previousIndex = previous?.let { key ->
                (1 until minOf(messages.itemCount, ArrivalScanLimit)).firstOrNull { i ->
                    (messages.peek(i) as? ChatListItem.ContentBubble)?.itemKey == key
                }
            }
            if (previousIndex != null) {
                val arrivals = (previousIndex - 1 downTo 0).mapNotNull {
                    messages.peek(it) as? ChatListItem.ContentBubble
                }
                val senders = arrivals.map { if (it.isFromSelf) null else it.authorId }
                val live = typing.filterNot { it.session() in landed }
                val takes = takesTypingDots(
                    typingBefore = shown,
                    arrivals = senders,
                    typing = live.map { it.userId }.toSet(),
                )
                val landedIds = senders.filterNotNull().toSet()
                live.filter { it.userId in landedIds }.forEach { landed += it.session() }

                // Only while resting on the dots: scrolled up, the arrival lands off screen and
                // nothing on screen should move for it.
                val under = dotsHeightPx()
                if (under > 0 && resting(previous, previousIndex)) {
                    // Exactly the dots' height, so nothing above moves on the frame it lands. The
                    // gap between the row above and the arrival eases to its new size on the row's
                    // own reflow spring, riding along with the grow.
                    grow = TypingGrow(
                        key = newestBubble.itemKey,
                        fromPx = under,
                        handoff = if (takes) TypingDotsFrame(dotsSize) else null,
                    )
                    anchorPending = true
                }
                handOffPending = takes
            }
        } else if (newestBubble == null) {
            newestKey = null
        }
    }

    /** The grow [key]'s row plays, if [arrive] opened one for it that hasn't played yet. */
    fun growFor(key: Any?): TypingGrow? = grow?.takeIf { it.key == key && it.key == newestKey && key !in grown }

    /** Whether an arrival still needs the list to rest on it; true once per arrival. */
    fun takeAnchor(): Boolean = anchorPending.also { anchorPending = false }

    /** Whether [key] is the arrival this model just opened, so the list's own push leaves it be. */
    fun grows(key: Any?): Boolean = grow?.key == key && key !in grown

    /** Called when a grow has played, so a row scrolled away and back doesn't replay it. */
    fun finished(key: Any) {
        grown += key
    }

    private fun ActiveTypist.session() = Session(userId, since)

    private companion object {
        // How far back from the newest message an arrival is looked for. A burst bigger than this
        // is a sync, not someone replying, and opens no handoff.
        const val ArrivalScanLimit = 24
    }
}

/**
 * Lays the newest row out at a height opening from [TypingGrow.fromPx] to its own on [progress],
 * bottom-aligned so its lower edge stays put and the rows above ride up with its top. Clipped while
 * it opens, so the part not yet uncovered doesn't draw over the row above.
 */
internal fun Modifier.openFromTypingRow(fromPx: Int, progress: () -> Float): Modifier =
    this
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val height = lerp(fromPx.toFloat(), placeable.height.toFloat(), progress())
                .roundToInt()
                .coerceAtLeast(0)
            layout(placeable.width, height) { placeable.place(0, height - placeable.height) }
        }
        .graphicsLayer { clip = progress() < 1f }

/**
 * An incoming bubble growing out of the typing bubble it replaced, as iOS #908 does: its outline
 * grows from the dots bubble's [from] size to its own about its bottom-leading corner, in the chrome
 * the dots were drawn in, while its content fades in behind it.
 */
@Composable
internal fun Modifier.growFromTypingDots(
    from: TypingDotsFrame,
    progress: () -> Float,
    contentAlpha: () -> Float,
): Modifier {
    val chrome = CodeTheme.colors.chat.incomingBubble.background
    val radius = with(LocalDensity.current) { CodeTheme.shapes.medium.topStart.toPx(Size.Unspecified, this) }
    fun Size.outline(): Rect {
        val p = progress()
        val width = lerp(from.size.width, width, p).coerceAtLeast(0f)
        val height = lerp(from.size.height, height, p).coerceAtLeast(0f)
        return Rect(Offset(0f, this.height - height), Size(width, height))
    }
    return this
        .drawBehind {
            // Hands over to the bubble's own fill as the content fades in, so the two don't stack.
            drawRoundRect(
                color = chrome,
                topLeft = size.outline().topLeft,
                size = size.outline().size,
                cornerRadius = CornerRadius(radius),
                alpha = 1f - contentAlpha(),
            )
        }
        .graphicsLayer {
            alpha = contentAlpha()
            clip = true
            shape = GenericShape { size, _ -> addRoundRect(RoundRect(size.outline(), CornerRadius(radius))) }
        }
}

/**
 * The typing dots under the newest row, for everyone in [avatars] (a group's faces; a DM shows the
 * dots alone), in the incoming bubble's chrome so a message can grow out of them.
 *
 * [presence] is the row's share of its own height: 1 while it is in, 0 when it is out. Reading it in
 * layout lets the newest row open and close with it, which moves the rows above on the same spring
 * the insertion push uses. [enters] plays the dots' own scale-in, for the frame they first appear;
 * dots carried under a newer message keep their place instead.
 */
@Composable
internal fun TypingRow(
    avatars: List<TypingAvatar>,
    gapAbove: Dp,
    model: TypingRowModel,
    presence: () -> Float,
    enters: Boolean,
    modifier: Modifier = Modifier,
) {
    var appeared by remember { mutableStateOf(!enters) }
    LaunchedEffect(Unit) { appeared = true }
    val entry by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = ChatAnimations.typingIndicator,
        label = "typingRowEntry",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                model.slotPx = placeable.height
                val height = (placeable.height * presence().coerceIn(0f, 1f)).roundToInt()
                layout(placeable.width, height) { placeable.place(0, height - placeable.height) }
            }
            .graphicsLayer {
                alpha = (presence() * entry).coerceIn(0f, 1f)
                val scale = lerp(TypingEntryScale, 1f, entry)
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 1f)
            }
            // The row above keeps the newest row's tight gap; the dots add the rest of theirs.
            // Their own lower gap is the newest row's.
            .padding(top = gapAbove, bottom = CodeTheme.dimens.grid.x1),
    ) {
        TypingIndicator(
            typists = avatars,
            key = { it.key },
            background = CodeTheme.colors.chat.incomingBubble.background,
            border = Color.Transparent,
            modifier = Modifier.onSizeChanged { model.dotsSize = Size(it.width.toFloat(), it.height.toFloat()) },
        ) { typist ->
            // Drawn the way the sender gutter draws the same member, so the face beside the dots is
            // the face beside their messages.
            val profile = typist.profile
            ContactAvatar(
                image = profile?.profilePicture,
                displayName = profile?.displayName.orEmpty(),
                access = BlobAccessContext.profile(typist.userId),
            )
        }
    }
}

private const val TypingEntryScale = 0.95f

/**
 * A message growing out of the typing dots, handed to its row: [progress] runs its outline from the
 * dots bubble's [from] size to its own, [contentAlpha] fades its content in behind that, and
 * [dotsAlpha] fades the dots out where they stood.
 */
@androidx.compose.runtime.Stable
internal class TypingHandoff(
    val from: TypingDotsFrame,
    val progress: () -> Float,
    val contentAlpha: () -> Float,
    val dotsAlpha: () -> Float,
)
