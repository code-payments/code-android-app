package com.flipcash.app.messenger.internal.link

import com.flipcash.shared.chat.models.LinkCard
import com.getcode.opencode.model.financial.Token
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
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
 * The query runs in [scope] rather than in the caller's coroutine. The caller is a paging
 * transform, which is re-run and cancelled every time anything upstream of the transcript emits —
 * a delivery receipt, a sender profile, a policy change. A query awaited inline dies with that
 * pass, and the cancellation arrives here as an ordinary failure, so the card renders unresolved
 * for a reason that has nothing to do with the link. Owning the scope means a cancelled pass
 * cancels only its own [Deferred.await]; the query finishes and the next pass reads the answer.
 *
 * Failure of any kind — offline, timeout, malformed entropy, a kill switch — returns the card
 * unchanged, in its unresolved state, and drops the query so a later pass asks again. The card has
 * no error state by design: a link that has not resolved is one the reader can still open.
 */
internal class LinkCardResolver(
    private val scope: CoroutineScope,
    private val lookup: suspend (entropy: String) -> Result<Snapshot>,
) {

    data class Snapshot(
        val amount: String,
        val claim: LinkCard.Cash.Claim,
        val token: Token,
        val issuedByViewer: Boolean,
    )

    private val mutex = Mutex()

    /**
     * The query per entropy, not the answer: memoizing the [Deferred] is what makes the several
     * passes that map the same message at once share one query instead of racing each other to
     * the same result.
     */
    private val queries = mutableMapOf<String, Deferred<LinkCard.Cash.State>>()

    suspend fun resolve(card: LinkCard): LinkCard = when (card) {
        is LinkCard.Cash -> card.copy(state = stateFor(card.entropy))
    }

    /** Ends the queries with the screen that asked for them. */
    fun dispose() {
        scope.cancel()
    }

    private suspend fun stateFor(entropy: String): LinkCard.Cash.State =
        mutex.withLock { queries.getOrPut(entropy) { scope.async { query(entropy) } } }.await()

    private suspend fun query(entropy: String): LinkCard.Cash.State = lookup(entropy).fold(
        onSuccess = { snapshot ->
            LinkCard.Cash.State.Resolved(
                amount = snapshot.amount,
                claim = snapshot.claim,
                token = snapshot.token,
                issuedByViewer = snapshot.issuedByViewer,
            )
        },
        onFailure = {
            // Forgotten rather than remembered as unresolved. A resolved card is worth holding for
            // the visit -- the amount does not move -- but holding a failure means one bad moment
            // decides the card until the reader leaves the chat and comes back.
            mutex.withLock { queries.remove(entropy) }
            LinkCard.Cash.State.Unresolved
        },
    )
}
