package com.flipcash.app.messenger.internal.link

import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.LinkCardResolution
import com.flipcash.services.models.GetChatError
import com.flipcash.services.models.GetUserProfileError
import com.flipcash.services.models.chat.ChatId
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
 * A token link needs only the mint's metadata, which the wallet already caches. A group invite
 * needs the group's public record, read in the redacted view -- see [GroupLinkLookup]. A person's
 * link needs their public profile, fetched by id or handle -- see [UserLinkLookup].
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
 * can still open. The exception is a token, group or person link an earlier visit resolved: a
 * failed request keeps the answer in [LinkCardMemory] rather than taking the card back down. An
 * answer that the link names nothing ([meansLinkIsGone]) is not a failed request, and clears it.
 */
internal class LinkCardResolver(
    private val scope: CoroutineScope,
    private val giftCard: suspend (entropy: String) -> Result<Snapshot>,
    private val tokenMetadata: suspend (mint: Mint) -> Result<Token>,
    private val group: suspend (chatId: ChatId) -> Result<LinkCard.GroupInvite.State.Resolved>,
    private val user: suspend (identity: LinkCard.User.Identity) -> Result<LinkCard.User.State.Resolved>,
    private val message: suspend (card: LinkCard.Message) -> Result<LinkCard.Message.State> = {
        Result.failure(IllegalStateException("no message lookup"))
    },
    /** An invite link's group and status, by its URL; see [InviteLinkLookup]. */
    private val invite: suspend (url: String) -> Result<LinkCard.InviteLink.State.Resolved> = {
        Result.failure(IllegalStateException("no invite lookup"))
    },
    private val memory: LinkCardMemory = LinkCardMemory(),
    /** The entropy when a message's whole text is one cash link; see [LinkCardClassifier.wholeCashLink]. */
    private val wholeCashLink: (text: String) -> String? = { null },
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
    private val groupQueries = mutableMapOf<ChatId, Deferred<LinkCard.GroupInvite.State>>()
    private val userQueries = mutableMapOf<LinkCard.User.Identity, Deferred<LinkCard.User.State>>()
    private val messageQueries = mutableMapOf<MessageKey, Deferred<LinkCard.Message.State>>()
    private val inviteQueries = mutableMapOf<String, Deferred<LinkCard.InviteLink.State>>()

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
     *
     * Only cash is held per visit. Token, group and person answers live in [memory], which outlasts
     * the screen so a card seen on an earlier visit paints resolved on this one's first frame; the
     * query maps above are still per visit, so each visit asks once and writes over what it found.
     */
    private val cashAnswers = ConcurrentHashMap<String, LinkCard.Cash.State.Resolved>()

    /**
     * Message answers are held per visit, like cash: what a card may show turns on the viewer's
     * membership and the group's rules, both of which a join or a purchase moves.
     */
    private val messageAnswers = ConcurrentHashMap<MessageKey, LinkCard.Message.State>()

    /**
     * Invite answers are held per visit and never in [memory], which is written to disk: the key is
     * the link, whose fragment is the group's key.
     */
    private val inviteAnswers = ConcurrentHashMap<String, LinkCard.InviteLink.State.Resolved>()
    private val tokenAnswers get() = memory.tokens
    private val groupAnswers get() = memory.groups
    private val userAnswers get() = memory.users

    private val _revision = MutableStateFlow(0)
    override val revision: StateFlow<Int> = _revision.asStateFlow()

    /** The entropy of this device's claim in flight, drawn as [LinkCard.Cash.Claim.Claiming]. */
    @Volatile
    private var claiming: String? = null

    /**
     * Draws [entropy]'s voucher as [LinkCard.Cash.Claim.Claiming] while its claim runs, or clears
     * that with null. Only an overlay: the stored answer stays what the lookup said, so nothing
     * here can outlive the claim or be mistaken for the server's view of it.
     */
    fun markClaiming(entropy: String?) {
        if (claiming == entropy) return
        claiming = entropy
        _revision.update { it + 1 }
    }

    override fun peek(card: LinkCard): LinkCard? = when (card) {
        is LinkCard.Cash -> cashAnswers[card.entropy]?.let { card.copy(state = it.overlayClaiming(card.entropy)) }
        is LinkCard.TokenInfo -> tokenAnswers[card.mint]?.let { card.copy(state = it) }
        is LinkCard.GroupInvite -> groupAnswers[card.chatId]?.let { card.copy(state = it) }
        is LinkCard.User -> userAnswers[card.identity]?.let { card.copy(state = it) }
        // A web card is drawn from the preview its sender built, which arrives on the card itself.
        is LinkCard.Web -> null
        is LinkCard.Message -> messageAnswers[card.key]?.let { card.copy(state = it) }
        is LinkCard.InviteLink -> inviteAnswers[card.url]?.let { card.copy(state = it) }
    }

    override suspend fun resolve(card: LinkCard): LinkCard = when (card) {
        is LinkCard.Cash -> card.copy(state = cashState(card.entropy).overlayClaiming(card.entropy))
        is LinkCard.TokenInfo -> card.copy(state = tokenState(card.mint))
        is LinkCard.GroupInvite -> card.copy(state = groupState(card.chatId))
        is LinkCard.User -> card.copy(state = userState(card.identity))
        is LinkCard.Web -> card
        is LinkCard.Message -> card.copy(state = messageState(card))
        is LinkCard.InviteLink -> card.copy(state = inviteState(card.url))
    }

    /**
     * The person [identity] names, for a tapped `@handle`: the same lookup and the same stored
     * answers a person card for `flipcash.com/<handle>` uses, so a handle a card or an earlier tap
     * already resolved opens without a round trip.
     *
     * Unlike [resolve], the failure comes back whole. A card draws an unclaimed handle and a
     * dropped connection alike, but a tap has to tell the reader which one happened. A stored
     * answer does not outlive the server saying the handle names nobody.
     */
    suspend fun lookUpUser(identity: LinkCard.User.Identity): Result<LinkCard.User.State.Resolved> {
        userAnswers[identity]?.let { return Result.success(it) }
        return user(identity)
            .onSuccess { memory.putUser(identity, it) }
            .onFailure { if (it.meansLinkIsGone()) memory.removeUser(identity) }
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
     * Drops every answer that carries an amount, and tells the cards on screen to ask again.
     *
     * A cash card and a linked message's payment hold their amount already formatted in the
     * display currency of the moment they resolved, so a new pick on the Money tab restates them
     * all at once. Rates are left alone: a tick that moves a figure by a cent is not worth a
     * request per card.
     */
    suspend fun invalidateAmounts() {
        cashAnswers.clear()
        messageAnswers.clear()
        mutex.withLock {
            cashQueries.clear()
            messageQueries.clear()
        }
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
    private fun LinkCard.Cash.State.overlayClaiming(entropy: String): LinkCard.Cash.State =
        if (this is LinkCard.Cash.State.Resolved && claim == LinkCard.Cash.Claim.Claimable && entropy == claiming) {
            copy(claim = LinkCard.Cash.Claim.Claiming)
        } else {
            this
        }

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
                        .also { resolved -> memory.putToken(mint, resolved) }
                },
                onFailure = {
                    forget(tokenQueries, mint)
                    tokenAnswers[mint] ?: LinkCard.TokenInfo.State.Unresolved
                },
            )
        }

    private suspend fun groupState(chatId: ChatId): LinkCard.GroupInvite.State =
        memoized(groupQueries, chatId) {
            group(chatId).fold(
                onSuccess = { resolved -> resolved.also { memory.putGroup(chatId, it) } },
                // A failed request keeps what an earlier visit found rather than tearing a resolved
                // card down to Unavailable. The server saying the group is gone clears it.
                onFailure = { error ->
                    forget(groupQueries, chatId)
                    if (error.meansLinkIsGone()) {
                        memory.removeGroup(chatId)
                        LinkCard.GroupInvite.State.Unavailable
                    } else {
                        groupAnswers[chatId] ?: LinkCard.GroupInvite.State.Unavailable
                    }
                },
            )
        }

    private suspend fun inviteState(url: String): LinkCard.InviteLink.State =
        memoized(inviteQueries, url) {
            invite(url).fold(
                onSuccess = { resolved -> resolved.also { inviteAnswers[url] = it } },
                onFailure = {
                    forget(inviteQueries, url)
                    inviteAnswers.remove(url)
                    LinkCard.InviteLink.State.Unavailable
                },
            )
        }

    private suspend fun userState(identity: LinkCard.User.Identity): LinkCard.User.State =
        memoized(userQueries, identity) {
            user(identity).fold(
                onSuccess = { resolved -> resolved.also { memory.putUser(identity, it) } },
                onFailure = { error ->
                    forget(userQueries, identity)
                    if (error.meansLinkIsGone()) {
                        memory.removeUser(identity)
                        LinkCard.User.State.NotFound
                    } else {
                        userAnswers[identity] ?: LinkCard.User.State.NotFound
                    }
                },
            )
        }

    private suspend fun messageState(card: LinkCard.Message): LinkCard.Message.State =
        memoized(messageQueries, card.key) {
            message(card).fold(
                onSuccess = { state -> state.withCashLink().also { messageAnswers[card.key] = it } },
                onFailure = { forget(messageQueries, card.key); LinkCard.Message.State.Unresolved },
            )
        }

    /**
     * Draws a linked message that is nothing but a cash link as that link's payment, through the
     * same per-visit cash query a cash card in the transcript uses. A redacted body is a
     * placeholder, so it is never read as a link; a link that does not resolve stays text.
     */
    private suspend fun LinkCard.Message.State.withCashLink(): LinkCard.Message.State {
        val excerpt = when (this) {
            is LinkCard.Message.State.Resolved -> message
            is LinkCard.Message.State.Gated -> message
            else -> return this
        }
        val text = excerpt.body as? LinkCard.Message.Body.Text ?: return this
        if (text.redacted) return this
        val entropy = wholeCashLink(text.text) ?: return this
        val cash = cashState(entropy) as? LinkCard.Cash.State.Resolved ?: return this
        val body = excerpt.copy(body = LinkCard.Message.Body.CashLink(cash.amount, cash.claim))
        return when (this) {
            is LinkCard.Message.State.Resolved -> copy(message = body)
            is LinkCard.Message.State.Gated -> copy(message = body)
        }
    }

    private data class MessageKey(val chatId: ChatId, val messageId: Long)

    private val LinkCard.Message.key get() = MessageKey(chatId, messageId)

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

/**
 * Whether a failed group or person lookup is the server's answer that the link names nothing, as
 * opposed to a request that did not complete. Only the first may clear a stored card: a deleted
 * group has to stop drawing as joinable, and a dropped connection must not take a good card down.
 */
internal fun Throwable.meansLinkIsGone(): Boolean = when (this) {
    is GetChatError.NotFound,
    is GetChatError.Denied,
    is GroupLinkLookup.NotAGroup,
    is GetUserProfileError.NotFound,
    is UserLinkLookup.NoSuchAccount -> true
    else -> false
}
