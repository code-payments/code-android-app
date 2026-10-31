package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

/**
 * Hands a group card the link it encodes, in memory. The card's route is saved with the back
 * stack, and an invite link's fragment is the chat key's secret, so the link can't ride on it the
 * way the chat id does (spec surface 1, "Show Group Card on every link").
 *
 * The sheet [put]s the link just before it opens the card; the card [take]s it once, which
 * clears it. A card restored with nothing here (after process death) encodes the plain link.
 */
@Singleton
class GroupCardLinks @Inject constructor() {

    /**
     * What one card encodes. [expiresAt] and [isInvite] decide the lines under the QR.
     * [toString] leaves the link out, so this never prints it.
     */
    class Link(val url: String, val isInvite: Boolean, val expiresAt: Instant? = null) {
        override fun toString(): String = "Link(isInvite=$isInvite, expiresAt=$expiresAt)"
    }

    private val links = ConcurrentHashMap<ChatId, Link>()

    fun put(chatId: ChatId, link: Link) {
        links[chatId] = link
    }

    /** The link handed over for [chatId], removed as it is read. */
    fun take(chatId: ChatId): Link? = links.remove(chatId)

    /**
     * What the card for [chatId] encodes: the link handed over, taken so it is used once, or else
     * the group's [plainUrl].
     */
    fun cardLink(chatId: ChatId, plainUrl: String?): Link? =
        take(chatId) ?: plainUrl?.let { Link(it, isInvite = false) }
}
