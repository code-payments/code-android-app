package com.flipcash.shared.chat.ui

import com.flipcash.services.models.chat.MessageContent

/**
 * One `@handle` found in message text: the span it occupies, covering the `@` and the handle, and
 * the handle it names, lowercased the way usernames are stored.
 *
 * Offsets are UTF-16 code units, half-open, matching [DetectedUrl] and `NSRange` on iOS.
 */
data class DetectedMention(
    val start: Int,
    val end: Int,
    val username: String,
)

/**
 * Every `@handle` in [text] that does not overlap one of [links], in the order they appear.
 *
 * Pure and synchronous: whether the handle belongs to anyone is only known once it is tapped.
 * iOS's `MentionDetector` is the reference, and the pattern is the same one.
 *
 * The character before the `@` must not be a handle character, `.` or `@`, so an email address or
 * `@@name` is not a mention. The handle ends at the first character a handle cannot hold, and a
 * handle running straight into more handle characters or another `@` is dropped rather than cut
 * short. A handle inside a link (`x.com/@jeff`) is left to the link.
 */
fun detectMentions(text: String, links: List<DetectedUrl>): List<DetectedMention> {
    if ('@' !in text) return emptyList()
    return MENTION_PATTERN.findAll(text).mapNotNull { match ->
        val mention = match.groups[1] ?: return@mapNotNull null
        val handle = match.groups[2] ?: return@mapNotNull null
        val start = mention.range.first
        val end = mention.range.last + 1
        if (links.any { it.start < end && start < it.end }) return@mapNotNull null
        DetectedMention(start = start, end = end, username = handle.value.lowercase())
    }.toList()
}

// The preceding character is matched and left out of the mention's range, as iOS does for want of
// a lookbehind in Swift Regex, so the two platforms run one pattern.
private val MENTION_PATTERN =
    Regex("""(?:^|[^A-Za-z0-9_.@])(@([A-Za-z0-9_]{2,15}))(?![A-Za-z0-9_@])""")

/**
 * The mentions in the text this content's bubble draws, as offsets into that text.
 *
 * A photo bubble draws its caption, so that is the text for a photo or a reply carrying one, and
 * never the reply's own text. [linkableText] stays null for a photo on purpose: it also decides
 * which messages get a link card, and a caption is not given one.
 */
fun MessageContent.detectedMentions(): List<DetectedMention> {
    val photo = when (this) {
        is MessageContent.Media -> this
        is MessageContent.Reply -> content.filterIsInstance<MessageContent.Media>().firstOrNull()
        else -> null
    }
    val text = (if (photo != null) photo.caption?.text else linkableText()) ?: return emptyList()
    return detectMentions(text, detectUrls(text))
}
