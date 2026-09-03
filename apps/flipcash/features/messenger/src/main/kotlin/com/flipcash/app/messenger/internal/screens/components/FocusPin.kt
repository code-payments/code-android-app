package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.constrainHeight
import androidx.paging.compose.LazyPagingItems
import com.flipcash.shared.chat.models.ChatListItem

/**
 * Keeps the message a long-press has put in focus where the user left it while the keyboard is up.
 *
 * The conversation is inset by `Modifier.imePadding()`, so opening the keyboard shortens the
 * transcript's viewport from the bottom. The list is `reverseLayout`, i.e. anchored to the bottom,
 * so every row rides up by the keyboard height — far enough that a message part-way up the thread
 * leaves the top of the screen the moment the keyboard opens over it.
 *
 * The pin hands the list back exactly the height the keyboard took, letting it overflow behind the
 * keyboard: bottom-anchored content then moves down by the same amount it was pushed up and the
 * focused row lands back where it started. [slack] caps that at the room the row had above the
 * composer, so a message that was already sitting near the bar rides up with the bar rather than
 * being buried under the keyboard.
 *
 * It holds for the whole focus, not just the edit it can lead to. The keyboard comes up on the edit,
 * but it also comes up if the composer is tapped while the selection bar is showing, and a selected
 * message pushed off the top is the same message lost from under the buttons acting on it.
 *
 * The compensation is read in the layout pass ([holdFocusedMessageInPlace]), the same phase
 * `imePadding` resolves in, so the two move together across the IME animation. A counter-scroll
 * driven from an effect would be a frame behind it the whole way down.
 */
@Stable
internal class FocusPin {
    var active by mutableStateOf(false)
        private set

    /** Bottom inset at the moment the focus began; only growth beyond this has to be cancelled. */
    private var imeAtStart by mutableIntStateOf(0)

    /** The list's own bottom padding at the moment the focus began; see [compensation]. */
    private var bottomPadAtStart by mutableIntStateOf(0)

    /**
     * How far the pinned row can travel down before it meets the composer, in px. Measured once, at
     * the start: a message arriving later scrolls the row further up and so only ever leaves this
     * an underestimate, which caps the compensation low rather than pushing the row back down over
     * the scroll that just moved it.
     */
    private var slack by mutableIntStateOf(0)

    /** What the pin is still handing back after a focus ends, on its way to nothing. See [release]. */
    private val unwind = Animatable(0f)

    suspend fun arm(imeBottom: Int, bottomPad: Int, slackPx: Int) {
        imeAtStart = imeBottom
        bottomPadAtStart = bottomPad
        slack = slackPx.coerceAtLeast(0)
        active = true
        // Nothing left of an earlier focus's unwind: while armed the compensation is measured from
        // the keyboard, and this is what it falls back to when the arming ends.
        unwind.snapTo(0f)
    }

    /**
     * Ends the pin, giving the height back over [UNWIND_MS] rather than in one frame.
     *
     * The keyboard outlives the focus — confirming an edit keeps it for the next message, cancelling
     * leaves the composer focused — so the transcript has to end up where a keyboard-up transcript
     * belongs, which is the pinned row's own height above where the pin was holding it. Dropping the
     * compensation is what puts it there; animating the drop is what stops that being a jump.
     *
     * Handing the amount to the scroll position instead would hold the row still now and cost the
     * transcript a keyboard's worth of history later, when the keyboard closes and the height comes
     * back with the scroll offset still carrying it.
     */
    suspend fun release(imeBottom: Int, bottomPad: Int) {
        val carried = compensation(imeBottom, bottomPad).toFloat()
        // Ordered so the measured height never changes across the handover: still the same number,
        // read from the other branch.
        unwind.snapTo(carried)
        active = false
        if (carried > 0f) unwind.animateTo(0f, tween(UNWIND_MS))
    }

    /**
     * Extra height the list needs right now to hold the focused row still, in px.
     *
     * Two things move the row when the keyboard opens, and both are measured from the start of the
     * focus. The keyboard shortens the list, lifting the bottom-anchored content by its height. The
     * input bar the list is padded for gets shorter at the same time: it is inside the scaffold's
     * `imePadding`, and its own `navigationBarsPadding` subtracts insets already consumed there, so
     * the navigation bar it was padding for goes to zero the moment the keyboard covers it. Less
     * bottom padding drops the content back down by that much. Handing back the sum of the two
     * cancels both; correcting only for the keyboard leaves the row sitting a navigation bar low.
     */
    fun compensation(imeBottom: Int, bottomPad: Int): Int =
        if (active) {
            ((imeBottom - imeAtStart) + (bottomPad - bottomPadAtStart)).coerceIn(0, slack)
        } else {
            unwind.value.toInt()
        }

    private companion object {
        const val UNWIND_MS = 200
    }
}

/**
 * The pin for [focusedMessageId] — the selected message, or the one being edited.
 *
 * Selecting and then editing is one focus, not two: the id doesn't change across that step, so the
 * pin isn't re-armed and the measurements it took at the long-press still describe the row.
 */
@Composable
internal fun rememberFocusPin(
    listState: LazyListState,
    messages: LazyPagingItems<ChatListItem>,
    focusedMessageId: Long?,
    imeInsets: WindowInsets,
    bottomPadPx: Int,
): FocusPin {
    val pin = remember { FocusPin() }
    val density = LocalDensity.current

    LaunchedEffect(focusedMessageId) {
        if (focusedMessageId != null) {
            // The focus starts from a long-press, so the row is on screen and still laid out from
            // the frame before. Its offset is the distance from the list's content start, which for
            // a reverse list is the bottom edge — and the bottom content padding already covers the
            // composer, so that distance is the gap between the row and the bar.
            val row = listState.layoutInfo.rowFor(messages, focusedMessageId)
            pin.arm(
                imeBottom = imeInsets.getBottom(density),
                bottomPad = bottomPadPx,
                slackPx = row?.offset ?: 0,
            )
        } else if (pin.active) {
            pin.release(imeInsets.getBottom(density), bottomPadPx)
        }
    }

    return pin
}

/**
 * Measures the transcript [FocusPin.compensation] px taller than it reports, so its bottom-anchored
 * content extends behind the keyboard instead of being pushed up by it. The overflow is only ever
 * the strip the keyboard covers, so nothing lands where it can be seen.
 */
internal fun Modifier.holdFocusedMessageInPlace(
    pin: FocusPin,
    imeInsets: WindowInsets,
    bottomPadPx: Int,
): Modifier =
    layout { measurable, constraints ->
        val extra = pin.compensation(imeInsets.getBottom(this), bottomPadPx)
        val placeable = measurable.measure(
            constraints.copy(
                minHeight = constraints.minHeight + extra,
                maxHeight = if (constraints.hasBoundedHeight) {
                    constraints.maxHeight + extra
                } else {
                    constraints.maxHeight
                },
            )
        )
        layout(placeable.width, constraints.constrainHeight(placeable.height - extra)) {
            placeable.place(0, 0)
        }
    }

/**
 * The laid-out row carrying [messageId], if it is on screen. Indices past [messages]'s own count are
 * the trailing separator and the contact card, which have no message behind them to peek at.
 */
internal fun LazyListLayoutInfo.rowFor(
    messages: LazyPagingItems<ChatListItem>,
    messageId: Long,
): LazyListItemInfo? = visibleItemsInfo.firstOrNull { info ->
    info.index < messages.itemCount &&
        (messages.peek(info.index) as? ChatListItem.ContentBubble)?.messageId == messageId
}

/**
 * Signed gap between the row carrying [messageId] and the lower edge of the top bar, in px:
 * positive while the row still has that much room to rise, negative once it has passed under the
 * bar by that much. Zero when the row is not laid out.
 *
 * The list is `reverseLayout`, so an item's offset is measured from the bottom of the content area
 * and grows as the item rises. The top of that area is [LazyListLayoutInfo.viewportEndOffset] less
 * the padding past it — which is the overlap the top bar reports, so the boundary sits level with
 * the bar's lower edge rather than behind it.
 */
internal fun LazyListLayoutInfo.headroomAbove(
    messages: LazyPagingItems<ChatListItem>,
    messageId: Long,
): Int {
    val row = rowFor(messages, messageId) ?: return 0
    return viewportEndOffset - afterContentPadding - (row.offset + row.size)
}
