package com.flipcash.shared.chat.ui

import android.util.Patterns
import com.flipcash.services.models.chat.MessageContent

/** A link found in message text: the span it occupies, and the URL it resolves to. */
data class DetectedUrl(
    val start: Int,
    val end: Int,
    val url: String,
)

/**
 * Every link in [text], in the order they appear.
 *
 * One pass, shared: the transcript underlines these and the chat mapper picks the card from the
 * same list, so "the first card-eligible link" and "the link the reader can see is a link" cannot
 * come apart. A second `Patterns.WEB_URL` pass elsewhere would drop the guards below and could
 * offer a card for a span the text never underlined.
 *
 * Offsets are UTF-16 code units into [text], half-open, matching `NSRange` on iOS.
 */
fun detectUrls(text: String): List<DetectedUrl> = buildList {
    val matcher = Patterns.WEB_URL.matcher(text)
    while (matcher.find()) {
        val match = matcher.group() ?: continue
        if (!authorityIsAscii(match)) continue
        if (!endsOnAsciiBoundary(text, matcher.end())) continue
        add(
            DetectedUrl(
                start = matcher.start(),
                end = matcher.end(),
                url = if (match.startsWith("http")) match else "https://$match",
            ),
        )
    }
}

/**
 * A non-ASCII character glued to the host retargets the link: the reader sees
 * `send.flipcash.com😀/c/...` and the resolver gets a different host once the authority
 * is IDNA-mapped. iOS rejects the same message through `LinkDetector.authorityIsASCII`; this is
 * the same guard.
 *
 * Authority only. A non-ASCII path or query is ordinary content and changes nothing about where
 * the link points.
 */
private fun authorityIsAscii(url: String): Boolean {
    val schemeEnd = url.indexOf("://")
    var i = if (schemeEnd < 0) 0 else schemeEnd + 3
    while (i < url.length) {
        val c = url[i]
        if (c == '/' || c == '?' || c == '#') return true
        if (c.code >= 0x80) return false
        i++
    }
    return true
}

/**
 * The same attack in the shape where the matcher stops short of the non-ASCII character instead of
 * swallowing it. `Patterns.WEB_URL` does that when there is no scheme: `example.com😀`
 * matches only `example.com`, which [authorityIsAscii] then sees as clean. The character is still
 * glued to the host in the text the reader is looking at.
 *
 * Trailing side only. A leading emoji is ordinary message text and the link after it is exactly
 * the link it appears to be.
 */
private fun endsOnAsciiBoundary(text: String, end: Int): Boolean =
    end >= text.length || text.codePointAt(end) < 0x80

/**
 * The text of this content that the transcript renders as rich text, or `null` where it renders
 * none. A reply carries its body nested, and the bubble draws the first text in it; a tombstone's
 * words are a string resource, not the message.
 *
 * Shared with the chat mapper so a card can only ever be offered for text the reader is looking at.
 */
fun MessageContent.linkableText(): String? = when (this) {
    is MessageContent.Text -> text
    is MessageContent.Reply -> content.filterIsInstance<MessageContent.Text>().firstOrNull()?.text
    is MessageContent.Cash,
    is MessageContent.Deleted,
    is MessageContent.Media,
    is MessageContent.System,
    -> null
}

/**
 * This text with the half-open span `[start, end)` cut out and the gap it left closed up.
 *
 * Used where a link renders as a card: the card *is* the link, so leaving the URL in the body
 * below it says the same thing twice. What surrounds the link is ordinary prose, and cutting a
 * word out of a sentence leaves the spaces on either side of it behind — "check example.com out"
 * would render as "check  out". So the whitespace the cut orphaned goes with it, and the two
 * halves are rejoined by a single separator: a newline if either side had one, a space otherwise.
 * A message that was nothing but the link comes back empty, and the bubble draws the card alone.
 *
 * Out-of-range bounds return the text unchanged rather than throwing. The span is measured on one
 * pass over the message and used on another, and a body that quietly keeps its link is a better
 * failure than a transcript that crashes on one message.
 */
fun String.withoutLinkSpan(start: Int, end: Int): String {
    if (start < 0 || end > length || start > end) return this

    val leading = substring(0, start)
    val trailing = substring(end)
    val head = leading.trimEnd()
    val tail = trailing.trimStart()

    return when {
        head.isEmpty() -> tail
        tail.isEmpty() -> head
        else -> {
            val gap = leading.substring(head.length) + trailing.substring(0, trailing.length - tail.length)
            head + (if (gap.contains('\n')) "\n" else " ") + tail
        }
    }
}
