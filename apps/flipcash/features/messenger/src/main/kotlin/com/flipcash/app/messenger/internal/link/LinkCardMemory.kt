package com.flipcash.app.messenger.internal.link

import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.models.LinkCard
import com.getcode.solana.keys.Mint
import java.util.concurrent.ConcurrentHashMap

/**
 * The last answer each group, token and person link resolved to, kept beyond one visit to a chat.
 *
 * [LinkCardResolver] is scoped to one visit, so without this every visit opened on a loading card
 * that grew into the resolved one a network round trip later, and the transcript, anchored at the
 * bottom, jumped by the difference (156px for a group invite with a requirement). Held here, a card
 * the reader has seen before paints resolved on its first frame, and the visit's own query still
 * runs and writes over it -- a stale member count is corrected in place, where a missing card moved
 * the whole transcript.
 *
 * This class is memory only, which is what a test gets. The app binds [PersistedLinkCardMemory],
 * which also keeps group and person answers on disk so the first visit after a cold start paints
 * them resolved too.
 *
 * Cash is deliberately absent. Claim state moves, and the resolver's per-visit scope is the
 * backstop that bounds a stale `Claimable`; see `LinkCardModule`. Everything here is public: a
 * mint's metadata, a group's redacted record, a person's public profile.
 *
 * The maps are read directly -- [LinkCardResolver.peek] runs during composition and must not
 * suspend -- and written only through the `put` functions, so a subclass sees every answer.
 */
internal open class LinkCardMemory {
    val tokens: Map<Mint, LinkCard.TokenInfo.State.Resolved> get() = _tokens
    val groups: Map<ChatId, LinkCard.GroupInvite.State.Resolved> get() = _groups
    val users: Map<LinkCard.User.Identity, LinkCard.User.State.Resolved> get() = _users

    /** Web page answers by [WebLinks.cacheKey]. Only answers: a failed lookup is never put here. */
    val webs: Map<String, LinkCard.Web.State> get() = _webs

    protected val _webs = ConcurrentHashMap<String, LinkCard.Web.State>()

    protected val _tokens = ConcurrentHashMap<Mint, LinkCard.TokenInfo.State.Resolved>()
    protected val _groups = ConcurrentHashMap<ChatId, LinkCard.GroupInvite.State.Resolved>()
    protected val _users = ConcurrentHashMap<LinkCard.User.Identity, LinkCard.User.State.Resolved>()

    fun putToken(mint: Mint, state: LinkCard.TokenInfo.State.Resolved) {
        _tokens[mint] = state
    }

    open fun putWeb(key: String, state: LinkCard.Web.State) {
        _webs[key] = state
    }

    open fun putGroup(chatId: ChatId, state: LinkCard.GroupInvite.State.Resolved) {
        _groups[chatId] = state
    }

    open fun putUser(identity: LinkCard.User.Identity, state: LinkCard.User.State.Resolved) {
        _users[identity] = state
    }

    /** Forgets [chatId]'s answer: the server said the group is gone. */
    open fun removeGroup(chatId: ChatId) {
        _groups.remove(chatId)
    }

    /** Forgets [identity]'s answer: the server said there is no such account. */
    open fun removeUser(identity: LinkCard.User.Identity) {
        _users.remove(identity)
    }

    /**
     * Suspends until whatever was stored has been read back, so a transcript mapped after it finds
     * every stored answer. Memory alone has nothing to read.
     */
    open suspend fun awaitLoaded() = Unit
}
