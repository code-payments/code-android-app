package com.flipcash.app.messenger.internal.link

import com.flipcash.app.session.SettledClaim
import java.util.concurrent.ConcurrentHashMap

/**
 * Pairs a claim back up with the message whose voucher was tapped, so the claimer's device can reply
 * to it.
 *
 * The pairing exists nowhere else. A tap on a cash card leaves the app through the URL handler and
 * returns as a deeplink the shell claims, which is what keeps a rendered card from being able to
 * claim anything — and the price of that boundary is that the claim arrives knowing an entropy and
 * nothing about a transcript. The chat is holding the other half while the reader is still looking
 * at it, so it is the chat that remembers.
 *
 * Two steps rather than one, because a tap is not a claim. The reader can cancel the bill, the link
 * can turn out to be already taken, or they can put the phone down; only [settled] answers with a
 * target, and only for a claim that moved money.
 */
internal class ClaimReplyTargets {

    /**
     * Written from the paging transform, which runs off the main thread and concurrently with
     * itself — hence a concurrent map. It holds every voucher the transcript has drawn, which is
     * bounded by what has been paged in and dies with the screen.
     */
    private val vouchers = ConcurrentHashMap<String, Long>()

    private val pending = ConcurrentHashMap<String, Long>()

    /** Records that [messageId] carried the voucher for [entropy]. */
    fun note(entropy: String, messageId: Long) {
        vouchers[entropy] = messageId
    }

    /**
     * Records a tap, capturing the reply target now rather than at the claim.
     *
     * Now is when the transcript is certainly still holding the voucher; the claim arrives from the
     * shell an unbounded time later, over a bill, possibly after a scroll that dropped the page.
     *
     * A voucher with nothing noted against it is one this transcript did not draw from someone else
     * — see [note]'s caller, which skips the reader's own messages — so there is nobody here to
     * thank and the tap is not recorded.
     */
    fun tapped(entropy: String) {
        vouchers[entropy]?.let { pending[entropy] = it }
    }

    /**
     * The message to reply to, or null if this claim is not one to thank anyone for.
     *
     * Consumed either way: the attempt is over, and a second tap registers itself again. Null for a
     * link that was not tapped here — pasted elsewhere, scanned from a code — and null for
     * [SettledClaim.collected] being false, which is every way a claim can fail and so covers both
     * the reader collecting back their own link and a link someone else already took.
     */
    fun settled(claim: SettledClaim): Long? {
        val target = pending.remove(claim.entropy) ?: return null
        return target.takeIf { claim.collected }
    }
}
