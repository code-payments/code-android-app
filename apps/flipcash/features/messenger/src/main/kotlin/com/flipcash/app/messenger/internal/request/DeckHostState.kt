package com.flipcash.app.messenger.internal.request

import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.flow.collectLatest

/**
 * Where the deck rests when it isn't expanded, and the owed requests it has already shown the strip
 * for. Once the strip has collapsed into the pill it stays there for the visit, until a request
 * the viewer owes on arrives that the strip hasn't shown.
 */
internal data class DeckRest(
    val host: DeckPlacement = DeckPlacement.Pill,
    val seenOwed: Set<ID> = emptySet(),
) {
    /** The requests the viewer has an unpaid share in, as of now. */
    fun owing(owed: Set<ID>): DeckRest = when {
        owed.isEmpty() -> DeckRest(DeckPlacement.Pill, seenOwed)
        (owed - seenOwed).isNotEmpty() -> DeckRest(DeckPlacement.Strip, seenOwed + owed)
        else -> copy(seenOwed = seenOwed + owed)
    }

    /** The strip has shrunk all the way into the pill. */
    fun collapsed(): DeckRest = copy(host = DeckPlacement.Pill)
}

/**
 * The one transition the deck's three hosts (the pill, the strip and the expanded list) show and
 * hide on, so the front card moves between any two of them as one shared element. Scrolling the
 * timeline away from the newest messages seeks the strip into the pill, following the scroll.
 */
@Stable
internal class DeckHostState(owed: Set<ID>) {
    var rest by mutableStateOf(DeckRest().owing(owed))
        private set

    // Requests already held when the chat opens put it in place on the first frame.
    val transition = SeekableTransitionState(rest.host)

    var expanded by mutableStateOf(false)

    /** The strip's full height with its padding, which a scroll of that length collapses. */
    var stripPx by mutableFloatStateOf(0f)

    private var collapsePx by mutableFloatStateOf(0f)
    private var snapNext = false
    private var hadDeck = owed.isNotEmpty()

    /** Partway between the strip and the pill, where a tap shouldn't expand it. */
    val collapsing: Boolean get() = collapsePx > 0f

    fun owing(owed: Set<ID>, hasDeck: Boolean) {
        val next = rest.owing(owed)
        // A deck held when the chat opens is in place on the first frame rather than moving there.
        if (!hadDeck && hasDeck && next.host == DeckPlacement.Strip) snapNext = true
        if (hasDeck) hadDeck = true
        if (next.host == DeckPlacement.Pill) collapsePx = 0f
        rest = next
    }

    private val target: DeckPlacement
        get() = if (expanded) DeckPlacement.Expanded else rest.host

    private val following: Boolean
        get() = !expanded && rest.host == DeckPlacement.Strip &&
            transition.currentState == DeckPlacement.Strip && stripPx > 0f

    /**
     * Collapses the strip by however far the timeline moved toward older messages, and opens it
     * back by however far it moved toward newer ones. The timeline scrolls as it would anyway.
     * This list is reverseLayout, so toward older is a positive y.
     */
    val scrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!following) return Offset.Zero
            // At the newest messages a drag back toward them moves nothing, so count what's left.
            val moved = consumed.y + minOf(available.y, 0f)
            collapsePx = (collapsePx + moved).coerceIn(0f, stripPx)
            if (collapsePx >= stripPx) pin()
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle()
            return Velocity.Zero
        }
    }

    /** Finishes a collapse released partway, toward whichever end is nearer. */
    private suspend fun settle() {
        val from = collapsePx
        if (from <= 0f || stripPx <= 0f) return
        val to = if (from >= stripPx / 2) stripPx else 0f
        animate(from, to, animationSpec = SETTLE_SPRING) { value, _ -> collapsePx = value }
        if (to > 0f) pin()
    }

    private fun pin() {
        rest = rest.collapsed()
        collapsePx = 0f
    }

    /** Puts the transition where the state says, for as long as the screen is up. */
    suspend fun run() {
        snapshotFlow { Triple(target, collapsePx, stripPx) }.collectLatest { (target, px, full) ->
            when {
                snapNext -> {
                    snapNext = false
                    transition.snapTo(target)
                }
                target == DeckPlacement.Strip && px > 0f && full > 0f &&
                    transition.currentState == DeckPlacement.Strip ->
                    transition.seekTo((px / full).coerceIn(0f, 1f), DeckPlacement.Pill)
                transition.currentState != target || transition.targetState != target ->
                    transition.animateTo(target)
            }
        }
    }
}

/** The deck's settle motion; a reorder after a request ends slides on it too. */
internal const val SETTLE_STIFFNESS = Spring.StiffnessMediumLow
private val SETTLE_SPRING = spring<Float>(stiffness = SETTLE_STIFFNESS)

/** A [DeckHostState] for one visit to a chat; reopening the chat starts it over. */
@Composable
internal fun rememberDeckHostState(chat: Any?, owed: Set<ID>): DeckHostState {
    val state = remember(chat) { DeckHostState(owed) }
    LaunchedEffect(state) { state.run() }
    return state
}
