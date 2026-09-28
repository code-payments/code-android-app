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
                url = withLowercaseScheme(match),
            ),
        )
    }
}

/**
 * [match] with its scheme written lowercase, or with `https://` in front when it has none.
 *
 * `Patterns.WEB_URL` matches a scheme in any case, so `HTTPS://flipcash.com/x` is a link. Checking
 * for a lowercase `http` prefix instead put a second scheme in front of it
 * (`https://HTTPS://flipcash.com/x`), which parses with `HTTPS` as the host: no card, and a tap
 * that opened nothing. The scheme is lowercased rather than just recognised because an intent's
 * scheme is matched case-sensitively, so `HTTPS:` would reach neither the app's own link filters
 * nor the browser. The rest of the URL is left as written; the host and path are the router's to
 * compare.
 */
private fun withLowercaseScheme(match: String): String {
    val schemeEnd = match.indexOf("://")
    if (schemeEnd <= 0) return "https://$match"
    return match.substring(0, schemeEnd).lowercase() + match.substring(schemeEnd)
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
    is MessageContent.Encrypted,
    -> null
}
