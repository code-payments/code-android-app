package com.flipcash.app.messenger.internal.link

import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.LinkCardResolution
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Fills a link card in, without acting on the link.
 *
 * For a cash link the whole payload is already in `GetTokenAccountInfos` — amount, claim state,
 * issuer, mint — and `ReceiveGiftCardTransactor` already makes exactly this query before its
 * pre-claim checks. So there is no proto change and no backend work here; there is a query and a
 * hard stop. Nothing on this path may reach `BillController.receiveGiftCard`, because that claims
 * the link, and a card that claimed what it rendered would empty a link by scrolling past it.
 *
 * A token link needs only the mint's metadata, which the wallet already caches.
 *
 * The query runs in [scope] rather than in the caller's coroutine. The caller is a card that has
 * just been composed, and it is cancelled by an ordinary scroll. A query awaited inline dies with
 * the card, and the cancellation arrives here as an ordinary failure, so the next card to ask about
 * the same link starts over. Owning the scope means a card leaving the screen cancels only its own
 * [Deferred.await]; the query finishes and the answer is there for whoever asks next.
 *
 * Failure of any kind — offline, timeout, malformed entropy, an unknown mint, a kill switch —
 * returns the card unchanged, in its unresolved state, and drops the query so a later card asks
 * again. The card has no error state by design: a link that has not resolved is one the reader
 * can still open.
 */
internal class LinkCardResolver(
    private val scope: CoroutineScope,
    private val giftCard: suspend (entropy: String) -> Result<Snapshot>,
    private val tokenMetadata: suspend (mint: Mint) -> Result<Token>,
) : LinkCardResolution {

    data class Snapshot(
        val amount: String,
        val claim: LinkCard.Cash.Claim,
        val token: Token,
    )

    private val mutex = Mutex()

    /**
     * The query per key, not the answer: memoizing the [Deferred] is what makes the several cards
     * that come on screen holding the same link share one query instead of racing each other to the
     * same result. Kept per card kind so an entropy and a mint cannot collide on one key.
     */
    private val cashQueries = mutableMapOf<String, Deferred<LinkCard.Cash.State>>()
    private val tokenQueries = mutableMapOf<Mint, Deferred<LinkCard.TokenInfo.State>>()

    /**
     * The answers that have landed, readable without the lock and without suspending — which is the
     * whole reason they are kept a second time rather than read back off [cashQueries].
     *
     * [peek] is called from composition, and a card that has already resolved has to be able to
     * paint resolved on its first frame. Reaching a [Deferred] means taking [mutex], which means
     * suspending, which means a frame of shimmer on a link the reader watched resolve a moment ago.
     * So the query map stays the thing that dedupes concurrent askers, and this is the thing that
     * answers instantly.
     *
     * Only successes are written. A failure is forgotten rather than remembered, so there is
     * nothing here to stop the next card asking again.
     */
    private val cashAnswers = ConcurrentHashMap<String, LinkCard.Cash.State.Resolved>()
    private val tokenAnswers = ConcurrentHashMap<Mint, LinkCard.TokenInfo.State.Resolved>()

    private val _revision = MutableStateFlow(0)
    override val revision: StateFlow<Int> = _revision.asStateFlow()

    override fun peek(card: LinkCard): LinkCard? = when (card) {
        is LinkCard.Cash -> cashAnswers[card.entropy]?.let { card.copy(state = it) }
        is LinkCard.TokenInfo -> tokenAnswers[card.mint]?.let { card.copy(state = it) }
    }

    override suspend fun resolve(card: LinkCard): LinkCard = when (card) {
        is LinkCard.Cash -> card.copy(state = cashState(card.entropy))
        is LinkCard.TokenInfo -> card.copy(state = tokenState(card.mint))
    }

    /**
     * Drops what is held about [entropy] and tells the cards on screen to ask again.
     *
     * Claim state is the one thing a card draws that moves while the reader is looking at it, and
     * the memo is what makes a scroll cheap. Both hold: the answer is kept until something says it
     * has changed. The [revision] bump is that something reaching the card — it used to be the
     * transcript re-mapping its whole paging window to redraw one voucher.
     */
    suspend fun invalidateCash(entropy: String) {
        cashAnswers.remove(entropy)
        forget(cashQueries, entropy)
        _revision.update { it + 1 }
    }

    /**
     * Drops every cash link still sitting on [LinkCard.Cash.Claim.Claimable], and reports whether
     * it dropped any.
     *
     * The blind spot [invalidateCash] cannot cover: a link claimed by someone else, which nothing
     * tells this device about. Asking is the only way to find out, so the caller asks on a timer —
     * and dropping nothing is what keeps that from being a poll in the usual sense. A transcript
     * with no claimable card drops nothing, bumps nothing, and so asks nothing; the tick costs a
     * walk of a map that is almost always empty. Only a chat actually showing an unclaimed voucher
     * pays for a query, which is the only chat whose answer can still move.
     *
     * [LinkCard.Cash.Claim.Claimed] and [LinkCard.Cash.Claim.Expired] are terminal and kept: a
     * claimed link does not become claimable again. An in-flight query is kept too — it has no
     * answer here yet, it is already asking, and dropping it would only start the same question
     * over.
     */
    suspend fun refreshClaimable(): Boolean {
        val stale = cashAnswers
            .filterValues { it.claim == LinkCard.Cash.Claim.Claimable }
            .keys
        if (stale.isEmpty()) return false

        stale.forEach {
            cashAnswers.remove(it)
            forget(cashQueries, it)
        }
        _revision.update { it + 1 }
        return true
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
                    ).also { resolved -> cashAnswers[entropy] = resolved }
                },
                onFailure = { forget(cashQueries, entropy); LinkCard.Cash.State.Unresolved },
            )
        }

    private suspend fun tokenState(mint: Mint): LinkCard.TokenInfo.State =
        memoized(tokenQueries, mint) {
            tokenMetadata(mint).fold(
                onSuccess = {
                    LinkCard.TokenInfo.State.Resolved(token = it)
                        .also { resolved -> tokenAnswers[mint] = resolved }
                },
                onFailure = { forget(tokenQueries, mint); LinkCard.TokenInfo.State.Unresolved },
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
