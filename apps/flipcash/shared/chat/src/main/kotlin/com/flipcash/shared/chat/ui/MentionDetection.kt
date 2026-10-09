package com.flipcash.shared.chat.ui

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
 * `@@name` is not a mention. A single `_` may stand between them, so `_@jeff_` is an italic
 * mention; `jeff_@gmail.com` and `__@jeff__` are still not. The handle ends at the first character a handle cannot hold, and a
 * handle running straight into more handle characters or another `@` is dropped rather than cut
 * short. A handle inside a link (`x.com/@jeff`) is left to the link.
 */
fun detectMentions(text: String, links: List<DetectedUrl>): List<DetectedMention> {
    if ('@' !in text) return emptyList()
    return MENTION_PATTERN.findAll(text).mapNotNull { match ->
        val opener = match.groups[1]?.value.orEmpty()
        val mention = match.groups[2] ?: return@mapNotNull null
        val handle = match.groups[3] ?: return@mapNotNull null
        val start = mention.range.first
        var end = mention.range.last + 1
        var username = handle.value
        // `_@jeff_`: the final `_` closes the italic the leading one opened, as long as a handle
        // of at least two characters is left. Without a leading `_` nothing is trimmed.
        if (opener.isNotEmpty() && username.endsWith('_') && username.length - 1 >= MIN_HANDLE_LENGTH) {
            username = username.dropLast(1)
            end--
        }
        if (links.any { it.start < end && start < it.end }) return@mapNotNull null
        DetectedMention(start = start, end = end, username = username.lowercase())
    }.toList()
}

private const val MIN_HANDLE_LENGTH = 2

// The preceding character is matched and left out of the mention's range, as iOS does for want of
// a lookbehind in Swift Regex, so the two platforms run one pattern. One `_` may sit between that
// character and the `@` (text-format spec, rule 6); it is not part of the mention.
private val MENTION_PATTERN =
    Regex("""(?:^|[^A-Za-z0-9_.@])(_?)(@([A-Za-z0-9_]{2,15}))(?![A-Za-z0-9_@])""")
