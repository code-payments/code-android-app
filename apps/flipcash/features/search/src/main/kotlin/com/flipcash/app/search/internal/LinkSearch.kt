package com.flipcash.app.search.internal

import com.flipcash.app.core.links.LinkDestination
import com.flipcash.app.core.links.classifyLink
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.LinkPreview
import com.flipcash.services.models.chat.MessageContent
import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

/**
 * A web link someone shared in a chat. The app keeps no record of which links were opened, so
 * Links lists what was shared: [senderId] is who shared it and [timestamp] is when.
 */
internal data class SharedLink(
    val url: String,
    val host: String,
    val preview: LinkPreview?,
    val chatId: ChatId,
    val messageId: Long,
    val senderId: ID?,
    val isFromSelf: Boolean,
    val timestamp: Instant,
)

private val WebUrl = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
private const val TrailingPunctuation = ".,;:!?)]}'\""

/**
 * The http(s) URLs in [text], in order. Trailing sentence punctuation is dropped, so "see
 * https://x.com." yields `https://x.com`. Kept out of `Patterns.WEB_URL` so it runs in unit tests
 * and matches only links with a scheme, as iOS does.
 */
internal fun extractWebUrls(text: String): List<String> =
    WebUrl.findAll(text).map { it.value.trimEnd { c -> c in TrailingPunctuation } }
        .filter { it.contains("://") && it.substringAfter("://").isNotEmpty() }
        .toList()

/**
 * The host Links shows for [url], or null when [url] does not belong in Links. Every Flipcash host
 * is left out — cash links, group invites, tip cards, profiles, currency and message links all live
 * there — so Links holds third-party web links only.
 */
internal fun searchableLinkHost(url: String): String? =
    when (val destination = classifyLink(url)) {
        LinkDestination.FirstParty -> null
        is LinkDestination.External -> destination.host.takeIf { destination.trustable }
        is LinkDestination.Trusted -> destination.host
    }

/**
 * Every third-party link shared in [messages], newest share first, one row per URL. A link sent
 * twice keeps only its newest share, so it reads as when it last came up.
 */
internal fun collectLinks(messages: List<StoredMessage>): List<SharedLink> =
    messages
        .flatMap { (chatId, message) ->
            linkSources(message.content).flatMap { (text, preview) ->
                extractWebUrls(text).mapNotNull { url ->
                    val host = searchableLinkHost(url) ?: return@mapNotNull null
                    SharedLink(
                        url = url,
                        host = host.removePrefix("www."),
                        preview = preview?.takeIf { it.url == url },
                        chatId = chatId,
                        messageId = message.messageId,
                        senderId = message.senderId,
                        isFromSelf = message.isFromSelf,
                        timestamp = message.timestamp,
                    )
                }
            }
        }
        .sortedByDescending { it.timestamp }
        .distinctBy { it.url }

/** Text that can carry a link, with the preview the sender built for it. */
private fun linkSources(content: List<MessageContent>): List<Pair<String, LinkPreview?>> =
    content.flatMap { item ->
        when (item) {
            is MessageContent.Text -> listOf(item.text to item.linkPreview)
            is MessageContent.Reply -> linkSources(item.content)
            is MessageContent.Media -> listOfNotNull(item.caption?.let { it.text to it.linkPreview })
            else -> emptyList()
        }
    }

/**
 * The links in [links] that match [query] (already folded) on the URL, its host, the preview
 * title, who shared it, or the chat it was shared in.
 */
internal fun filterLinks(
    links: List<SharedLink>,
    query: String,
    sharerName: (SharedLink) -> String?,
    chatName: (ChatId) -> String?,
): List<SharedLink> = links.filter { link ->
    SearchMatching.matches(
        query,
        link.url,
        link.host,
        link.preview?.title,
        sharerName(link),
        chatName(link.chatId),
    )
}

/**
 * The mint a Flipcash currency link (`/token/<mint>`) points at, or null for any other link. What
 * gives a currency card its "Shared by".
 */
internal fun currencyMintOf(url: String): String? {
    if (classifyLink(url) != LinkDestination.FirstParty) return null
    val path = url.substringAfter("://").dropWhile { it !in "/?#" }
        .substringBefore('?').substringBefore('#')
    val segments = path.split('/').filter { it.isNotEmpty() }
    if (segments.size < 2 || segments[0] != "token") return null
    return segments[1]
}

/**
 * For each currency linked in [messages], the newest message that linked it, keyed by mint.
 * [messages] must be newest first.
 */
internal fun latestCurrencyShares(messages: List<StoredMessage>): Map<String, StoredMessage> {
    val shares = mutableMapOf<String, StoredMessage>()
    messages.forEach { stored ->
        linkSources(stored.message.content).forEach { (text, _) ->
            extractWebUrls(text).forEach { url ->
                currencyMintOf(url)?.let { mint -> shares.putIfAbsent(mint, stored) }
            }
        }
    }
    return shares
}
