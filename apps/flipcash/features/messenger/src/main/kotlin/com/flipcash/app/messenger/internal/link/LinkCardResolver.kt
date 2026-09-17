package com.flipcash.app.messenger.internal.link

import com.flipcash.shared.chat.models.LinkCard
import com.getcode.opencode.model.financial.Token
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fills a cash link card in, without claiming it.
 *
 * The whole payload is already in `GetTokenAccountInfos` — amount, claim state, issuer, mint —
 * and `ReceiveGiftCardTransactor` already makes exactly this query before its pre-claim checks.
 * So there is no proto change and no backend work here; there is a query and a hard stop.
 * Nothing on this path may reach `BillController.receiveGiftCard`, because that claims the link,
 * and a card that claimed what it rendered would empty a link by scrolling past it.
 *
 * Failure of any kind — offline, timeout, malformed entropy, a kill switch — returns the card
 * unchanged, in its unresolved state. The card has no error state by design: the link underneath
 * is still tappable and still works.
 */
internal class LinkCardResolver(
    private val lookup: suspend (entropy: String) -> Result<Snapshot>,
) {

    data class Snapshot(
        val amount: String,
        val claim: LinkCard.Cash.Claim,
        val token: Token,
        val issuedByViewer: Boolean,
    )

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, LinkCard.Cash.State>()

    suspend fun resolve(card: LinkCard): LinkCard = when (card) {
        is LinkCard.Cash -> card.copy(state = stateFor(card.entropy))
    }

    private suspend fun stateFor(entropy: String): LinkCard.Cash.State {
        mutex.withLock { cache[entropy] }?.let { return it }

        val state = lookup(entropy).fold(
            onSuccess = { snapshot ->
                LinkCard.Cash.State.Resolved(
                    amount = snapshot.amount,
                    claim = snapshot.claim,
                    token = snapshot.token,
                    issuedByViewer = snapshot.issuedByViewer,
                )
            },
            onFailure = { LinkCard.Cash.State.Unresolved },
        )

        mutex.withLock { cache[entropy] = state }
        return state
    }
}
