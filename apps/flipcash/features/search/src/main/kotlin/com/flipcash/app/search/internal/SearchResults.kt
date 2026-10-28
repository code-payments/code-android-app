package com.flipcash.app.search.internal

import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ui.ConversationReference
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.base58

/** The chips pinned above the results, in the order they show (node 12248:97). */
internal enum class SearchChip { All, Chats, Currencies, Messages, Links }

/** How many rows a section shows under All before its See all. */
internal const val ALL_SECTION_LIMIT = 3

/** A currency card: the currency, and who last shared a link to it, if anyone did. */
internal data class CurrencyResult(val token: Token, val sharedBy: Attribution?)

/** A message hit, with the chat it is in and who sent it. */
internal data class MessageResult(
    val chat: ConversationReference,
    val hit: MessageHit,
    val senderName: String?,
)

/** A shared link, with how its row names where it came from. */
internal data class LinkResult(val link: SharedLink, val attribution: Attribution)

/** Who shared something: the viewer, a named person, or a group. */
internal sealed interface Attribution {
    data object You : Attribution
    data class By(val name: String) : Attribution
    data class In(val group: String) : Attribution
}

/** Everything a query found, per chip. */
internal data class SearchResults(
    val chats: List<ConversationReference> = emptyList(),
    val currencies: List<CurrencyResult> = emptyList(),
    val messages: List<MessageResult> = emptyList(),
    val links: List<LinkResult> = emptyList(),
) {
    /** Whether [chip] has nothing to show. All has no Links section, so links don't count there. */
    fun isEmpty(chip: SearchChip): Boolean = when (chip) {
        SearchChip.All -> chats.isEmpty() && currencies.isEmpty() && messages.isEmpty()
        SearchChip.Chats -> chats.isEmpty()
        SearchChip.Currencies -> currencies.isEmpty()
        SearchChip.Messages -> messages.isEmpty()
        SearchChip.Links -> links.isEmpty()
    }
}

/** The chats whose name or handle contains [query] (already folded), most recently active first. */
internal fun searchChats(chats: List<ConversationReference>, query: String): List<ConversationReference> =
    chats.filter { SearchMatching.matches(query, it.name, it.handle) }
        .sortedByDescending { it.lastActivity }

/**
 * Who shared a link: the viewer for their own messages, the group in a group, otherwise the
 * sender, falling back to the DM's name since its only other member sent it.
 */
internal fun attribution(
    isFromSelf: Boolean,
    chat: ConversationReference,
    senderName: String?,
): Attribution = when {
    isFromSelf -> Attribution.You
    chat.isGroup -> Attribution.In(chat.name.orEmpty())
    else -> Attribution.By(senderName ?: chat.name.orEmpty())
}

/**
 * What a query finds, from what the screen has loaded. Messages and links from a chat the Chats
 * list doesn't show are left out, so a result never opens a chat the viewer can't otherwise reach.
 */
internal fun buildResults(
    query: String,
    chats: List<ConversationReference>,
    messages: List<StoredMessage>,
    links: List<SharedLink>,
    currencies: List<Token>,
    currencyShares: Map<String, StoredMessage>,
    senderName: (ID) -> String?,
    inviteLinkLabel: String,
): SearchResults {
    val nameOf = { id: ID? -> id?.let(senderName) }
    val chatsById: Map<ChatId, ConversationReference> = chats.associateBy { it.chatId }

    val messageResults = searchMessages(messages.filter { it.chatId in chatsById }, query, inviteLinkLabel)
        .map { hit -> MessageResult(chatsById.getValue(hit.chatId), hit, nameOf(hit.senderId)) }

    val linkResults = filterLinks(
        links = links.filter { it.chatId in chatsById },
        query = query,
        sharerName = { link -> if (link.isFromSelf) null else nameOf(link.senderId) },
        chatName = { chatsById[it]?.name },
    ).map { link ->
        LinkResult(link, attribution(link.isFromSelf, chatsById.getValue(link.chatId), nameOf(link.senderId)))
    }

    val currencyResults = currencies.map { token ->
        val share = currencyShares[token.address.base58()]?.takeIf { it.chatId in chatsById }
        CurrencyResult(
            token = token,
            sharedBy = share?.let {
                val chat = chatsById.getValue(it.chatId)
                // On a card the group isn't who shared it, so name the sender there too.
                if (it.message.isFromSelf) Attribution.You
                else Attribution.By(nameOf(it.message.senderId) ?: chat.name.orEmpty())
            },
        )
    }

    return SearchResults(
        chats = searchChats(chats, query),
        currencies = currencyResults,
        messages = messageResults,
        links = linkResults,
    )
}
