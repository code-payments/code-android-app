package com.flipcash.app.messenger.internal.link

/**
 * What a tap on a cash card does in the chat on screen. See `ChatViewModel.State.cashCardTap`.
 *
 * Decided before the link leaves, because once it has left nothing can call it back: the claim
 * path takes an entropy from anywhere and does not know a transcript was involved.
 */
internal sealed interface CashCardTap {

    /**
     * The viewer is reading the group from outside. The link is not opened and the viewer is told
     * to join first.
     *
     * A client-side guard on the card only. The URL is still in the message text, and whoever copies
     * it out can claim it wherever they paste it.
     */
    data object JoinToCollect : CashCardTap

    /**
     * The link opens and the claim runs as it would from anywhere else.
     *
     * [thanks] is whether a collected claim is answered with a reply on the transcript, which is
     * only when the viewer can post one.
     */
    data class Collect(val thanks: Boolean) : CashCardTap
}
