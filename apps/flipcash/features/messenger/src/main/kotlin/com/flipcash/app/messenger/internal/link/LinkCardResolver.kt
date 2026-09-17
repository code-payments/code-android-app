package com.flipcash.app.messenger.internal.link

import com.flipcash.app.core.tipping.TipCardOwner
import com.flipcash.services.models.UserProfile
import com.flipcash.shared.chat.models.LinkCard
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fills a link card in, without acting on the link.
 *
 * For a cash link the whole payload is already in `GetTokenAccountInfos` — amount, claim state,
 * issuer, mint — and `ReceiveGiftCardTransactor` already makes exactly this query before its
 * pre-claim checks. So there is no proto change and no backend work here; there is a query and a
 * hard stop. Nothing on this path may reach `BillController.receiveGiftCard`, because that claims
 * the link, and a card that claimed what it rendered would empty a link by scrolling past it.
 *
 * A token link needs only the mint's metadata, which the wallet already caches. A tip card link
 * needs its owner's profile, which is one `GetProfile` and nothing else — the card the link opens to
 * is assembled from the same answer.
 *
 * The query runs in [scope] rather than in the caller's coroutine. The caller is a paging
 * transform, which is re-run and cancelled every time anything upstream of the transcript emits —
 * a delivery receipt, a sender profile, a policy change. A query awaited inline dies with that
 * pass, and the cancellation arrives here as an ordinary failure, so the card renders unresolved
 * for a reason that has nothing to do with the link. Owning the scope means a cancelled pass
 * cancels only its own [Deferred.await]; the query finishes and the next pass reads the answer.
 *
 * Failure of any kind — offline, timeout, malformed entropy, an unknown mint, a kill switch —
 * returns the card unchanged, in its unresolved state, and drops the query so a later pass asks
 * again. The card has no error state by design: a link that has not resolved is one the reader
 * can still open.
 */
internal class LinkCardResolver(
    private val scope: CoroutineScope,
    private val giftCard: suspend (entropy: String) -> Result<Snapshot>,
    private val tokenMetadata: suspend (mint: Mint) -> Result<Token>,
    private val profile: suspend (owner: TipCardOwner) -> Result<UserProfile>,
) {

    data class Snapshot(
        val amount: String,
        val claim: LinkCard.Cash.Claim,
        val token: Token,
    )

    private val mutex = Mutex()

    /**
     * The query per key, not the answer: memoizing the [Deferred] is what makes the several passes
     * that map the same message at once share one query instead of racing each other to the same
     * result. Kept per card kind so an entropy, a mint and a card owner cannot collide on one key.
     */
    private val cashQueries = mutableMapOf<String, Deferred<LinkCard.Cash.State>>()
    private val tokenQueries = mutableMapOf<Mint, Deferred<LinkCard.TokenInfo.State>>()
    private val tipCardQueries = mutableMapOf<TipCardOwner, Deferred<LinkCard.TipCard.State>>()

    suspend fun resolve(card: LinkCard): LinkCard = when (card) {
        is LinkCard.Cash -> card.copy(state = cashState(card.entropy))
        is LinkCard.TokenInfo -> card.copy(state = tokenState(card.mint))
        is LinkCard.TipCard -> card.copy(state = tipCardState(card.owner))
    }

    /**
     * Drops what is held about [entropy], so the next pass over the transcript asks again.
     *
     * Claim state is the one thing a card draws that moves while the reader is looking at it, and
     * the memo is what makes a scroll cheap. Both hold: the answer is kept until something says it
     * has changed. Invalidating alone does not redraw anything — it makes the next mapping pass
     * honest, and the caller is responsible for there being one.
     */
    suspend fun invalidateCash(entropy: String) = forget(cashQueries, entropy)

    /**
     * Drops every cash link still sitting on [LinkCard.Cash.Claim.Claimable], and reports whether
     * it dropped any.
     *
     * The blind spot [invalidateCash] cannot cover: a link claimed by someone else, which nothing
     * tells this device about. Asking is the only way to find out, so the caller asks on a timer —
     * and the return value is what keeps that from being a poll in the usual sense. A transcript
     * with no claimable card drops nothing, the caller re-maps nothing, and the tick costs a lock
     * and a walk of a map that is almost always empty. Only a chat that is actually showing an
     * unclaimed voucher pays for a query, which is the only chat whose answer can still move.
     *
     * [LinkCard.Cash.Claim.Claimed] and [LinkCard.Cash.Claim.Expired] are terminal and kept: a
     * claimed link does not become claimable again. An in-flight query is kept too — it is already
     * asking, and dropping it would only start the same question over.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun refreshClaimable(): Boolean = mutex.withLock {
        val stale = cashQueries.filterValues { query ->
            val state = query.takeIf { it.isCompleted }
                ?.runCatching { getCompleted() }
                ?.getOrNull()
            (state as? LinkCard.Cash.State.Resolved)?.claim == LinkCard.Cash.Claim.Claimable
        }.keys.toList()
        stale.forEach { cashQueries.remove(it) }
        stale.isNotEmpty()
    }

    /** Ends the queries with the screen that asked for them. */
    fun dispose() {
        scope.cancel()
    }

    private suspend fun cashState(entropy: String): LinkCard.Cash.State =
        memoized(cashQueries, entropy) {
            giftCard(entropy).fold(
                onSuccess = {
                    LinkCard.Cash.State.Resolved(
                        amount = it.amount,
                        claim = it.claim,
                        token = it.token,
                    )
                },
                onFailure = { forget(cashQueries, entropy); LinkCard.Cash.State.Unresolved },
            )
        }

    private suspend fun tokenState(mint: Mint): LinkCard.TokenInfo.State =
        memoized(tokenQueries, mint) {
            tokenMetadata(mint).fold(
                onSuccess = { LinkCard.TokenInfo.State.Resolved(token = it) },
                onFailure = { forget(tokenQueries, mint); LinkCard.TokenInfo.State.Unresolved },
            )
        }

    /**
     * Keyed on the owner as the link named them, which is the only key there is: a handle and an id
     * for the same person are indistinguishable until the profile comes back, and by then both
     * queries have been made. A transcript linking one person both ways is rare enough to pay for.
     */
    private suspend fun tipCardState(owner: TipCardOwner): LinkCard.TipCard.State =
        memoized(tipCardQueries, owner) {
            profile(owner).fold(
                onSuccess = { LinkCard.TipCard.State.Resolved(profile = it) },
                onFailure = { forget(tipCardQueries, owner); LinkCard.TipCard.State.Unresolved },
            )
        }

    private suspend fun <K, V> memoized(
        queries: MutableMap<K, Deferred<V>>,
        key: K,
        query: suspend () -> V,
    ): V = mutex.withLock { queries.getOrPut(key) { scope.async { query() } } }.await()

    /**
     * Forgotten rather than remembered as unresolved. A resolved card is worth holding for the
     * visit -- neither an amount nor a mint's name moves -- but holding a failure means one bad
     * moment decides the card until the reader leaves the chat and comes back.
     */
    private suspend fun <K, V> forget(queries: MutableMap<K, Deferred<V>>, key: K) {
        mutex.withLock { queries.remove(key) }
    }
}
