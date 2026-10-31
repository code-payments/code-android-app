package com.flipcash.shared.chat.ui

import android.net.Uri
import com.flipcash.app.core.links.FIRST_PARTY_HOSTS

/**
 * [text] with every invite link in it replaced by [label], for the places that show a message as a
 * line of text rather than as a bubble: the chat-list preview, a push and a reply's citation.
 *
 * An invite link's fragment is the group's key, so none of it may reach a preview. That holds for a
 * link whose secret does not parse, too: it is still shaped like an invite, and its fragment is still
 * whatever the sender pasted. The replaced span runs to the next whitespace, past wherever
 * [detectUrls] stopped, so a fragment the URL pattern cut short does not leave a tail behind.
 *
 * The links are found by the same [detectUrls] pass the transcript and the link card read, so a span
 * the card would draw as an invite is the span replaced here. Anything else in [text] is kept.
 */
fun labelInviteLinks(text: String, label: String): String = labelInviteLinks(text) { label }

/** [labelInviteLinks], asking for the label only when [text] has an invite link to replace. */
fun labelInviteLinks(text: String, label: () -> String): String {
    // Most messages carry no invite; skip the detection pass for them.
    if (!text.contains(INVITE_PATH, ignoreCase = true) && !text.contains(ENCODED_INVITE_PATH, ignoreCase = true)) {
        return text
    }
    val invites = detectUrls(text).filter { isInviteLinkUrl(it.url) }
    if (invites.isEmpty()) return text
    val replacement = label()
    return buildString {
        var cursor = 0
        for (link in invites) {
            if (link.start < cursor) continue
            append(text, cursor, link.start)
            append(replacement)
            cursor = text.indexOfFirst(link.end) { it.isWhitespace() }.takeIf { it >= 0 } ?: text.length
        }
        append(text, cursor, text.length)
    }
}

/**
 * Whether [url] is an invite link: `/chat/invite` on one of our hosts, as the router matches it, or
 * a jump link wrapping one. Whether its secret is valid does not matter here.
 */
fun isInviteLinkUrl(url: String): Boolean {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    if (host !in FIRST_PARTY_HOSTS) return false
    if (host == JUMP_HOST) {
        val target = url.substringAfter("#source=", missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
            ?.let(Uri::decode)
            ?: return false
        return runCatching { Uri.parse(target).host?.lowercase() }.getOrNull() != JUMP_HOST &&
            isInviteLinkUrl(target)
    }
    val segments = uri.pathSegments
    return segments.size == 2 &&
        segments[0].equals("chat", ignoreCase = true) &&
        segments[1].equals("invite", ignoreCase = true)
}

private inline fun String.indexOfFirst(from: Int, predicate: (Char) -> Boolean): Int {
    for (i in from until length) if (predicate(this[i])) return i
    return -1
}

private const val INVITE_PATH = "chat/invite"
private const val ENCODED_INVITE_PATH = "chat%2Finvite"
private const val JUMP_HOST = "jump.flipcash.com"
