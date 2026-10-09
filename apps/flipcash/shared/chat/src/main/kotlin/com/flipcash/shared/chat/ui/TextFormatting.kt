package com.flipcash.shared.chat.ui

import com.flipcash.libs.textformat.FormattedText
import com.flipcash.libs.textformat.ProtectedRange
import com.flipcash.libs.textformat.RangeKind
import com.flipcash.libs.textformat.parseTextFormat

/**
 * [text] run through the shared formatting parser, with the links and mentions this module detects
 * as the ranges a marker may not open or close inside.
 *
 * Every surface that shows parsed text goes through here, so the transcript, the chat list and a
 * notification cannot detect different ranges and format the same message differently.
 */
fun parseChatText(
    text: String,
    links: List<DetectedUrl> = detectUrls(text),
    mentions: List<DetectedMention> = detectMentions(text, links),
): FormattedText {
    val ranges = buildList {
        links.forEach { add(ProtectedRange(it.start, it.end, RangeKind.Link)) }
        mentions.forEach { add(ProtectedRange(it.start, it.end, RangeKind.Mention)) }
    }
    return parseTextFormat(text, ranges)
}

/**
 * [text] as plain words: the markers the parser consumed are gone and no style is applied. This is
 * what a reply quote, the chat list preview and a notification show; copy and edit keep the raw
 * text instead.
 */
fun formattedDisplayText(text: String): String = parseChatText(text).display

/**
 * The links in [text] that may become a link card: every detected link except the URL of a masked
 * `[label](url)`.
 *
 * A masked link shows `label` and opens somewhere else, so a card built from it would show a
 * destination the reader never saw in the message (text-format spec, decision 4). A mask the
 * parser leaves literal is not consumed, and its URL is an ordinary link.
 */
fun cardLinks(text: String, links: List<DetectedUrl> = detectUrls(text)): List<DetectedUrl> {
    if (links.none { it.isMaskShaped(text) }) return links
    val targets = parseChatText(text, links).displayRanges.mapNotNull { it.target }.toSet()
    return links.filterNot { it.isMaskShaped(text) && text.substring(it.start, it.end) in targets }
}

private fun DetectedUrl.isMaskShaped(text: String): Boolean =
    start >= 2 && text[start - 1] == '(' && text[start - 2] == ']' && text.getOrNull(end) == ')'
