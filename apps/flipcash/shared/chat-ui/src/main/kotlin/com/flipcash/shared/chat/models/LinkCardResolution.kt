package com.flipcash.shared.chat.models

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * How a card fills itself in.
 *
 * The lookup lives in `:apps:flipcash:features:messenger`, which `chat-ui` cannot see, and the
 * suspension belongs to the card rather than to whoever built it — so this is the seam between
 * them. `LinkCardResolver` on the far side is the implementation; nothing else implements it.
 *
 * The card view calls [peek] during composition and [resolve] off it. That split is the whole
 * point of the interface: a card scrolled back into view has an answer already, and a shimmer for
 * one frame on a link the reader has seen resolve is worse than the wait it stands in for.
 */
interface LinkCardResolution {

    /**
     * Bumped when an answer this has already given has stopped being true, so a card on screen
     * asks again.
     *
     * The transcript used to carry this: a claim evicted the answer and the whole paging window
     * re-mapped to redraw one voucher. With the lookup inside the card there is nothing to re-map,
     * and this is what reaches the card instead. It says only *that* something moved, not what —
     * the cards re-ask and the memo answers every one whose answer did not change.
     *
     * A [StateFlow] rather than a plain flow so a card composed after a claim reads the count that
     * is current at its first frame. Starting every card at zero would have each of them see one
     * spurious change on its first collection, and re-ask for an answer it had just peeked.
     */
    val revision: StateFlow<Int>

    /**
     * The answer already held for [card], or null if none is — never a query, never a suspension.
     *
     * Read during composition, so a card whose link has already resolved paints resolved on its
     * first frame.
     */
    fun peek(card: LinkCard): LinkCard?

    /** [card] with its state filled in, or the card unchanged if the lookup could not answer. */
    suspend fun resolve(card: LinkCard): LinkCard
}

/**
 * Leaves every card exactly as it was handed in.
 *
 * What a preview gets, which is why it is identity rather than a stub that resolves or fails: a
 * preview states the card it wants to draw and this hands it straight back, loading state included.
 */
private object NoLinkCardResolution : LinkCardResolution {
    override val revision: StateFlow<Int> = MutableStateFlow(0)
    override fun peek(card: LinkCard): LinkCard = card
    override suspend fun resolve(card: LinkCard): LinkCard = card
}

val LocalLinkCardResolution =
    staticCompositionLocalOf<LinkCardResolution> { NoLinkCardResolution }
