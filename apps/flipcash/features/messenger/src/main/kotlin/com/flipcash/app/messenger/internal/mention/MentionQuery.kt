package com.flipcash.app.messenger.internal.mention

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.shared.chat.MemberMatch

/**
 * The `@` word the cursor is in, from the `@` up to the cursor.
 *
 * [start] is the `@`'s index and [end] the cursor's. [text] is the whole run including the `@`,
 * which is what goes to the search: it strips one leading `@` itself, so "@@eri" searches "@eri"
 * and finds nobody, as it does on iOS.
 */
internal data class MentionToken(val start: Int, val end: Int, val text: String)

/** Both the ASCII sign and the fullwidth one a CJK keyboard types; search folds them together. */
private fun Char.isMentionSign(): Boolean = this == '@' || this == '＠'

/**
 * The mention being typed at [selection] in [text], or null when there isn't one.
 *
 * A mention is the whitespace-free run ending at the cursor, and it has to start with an `@` that
 * is at the start of the text or right after whitespace, so "a@b" is an email-shaped word and not
 * a mention. A selection that covers text is not a cursor, so it never has one.
 */
internal fun activeMentionToken(text: CharSequence, selection: TextRange): MentionToken? {
    if (!selection.collapsed) return null
    val cursor = selection.end
    if (cursor <= 0 || cursor > text.length) return null
    var start = cursor
    while (start > 0 && !text[start - 1].isWhitespace()) start--
    // Whitespace right before the cursor: no word to be in.
    if (start == cursor || !text[start].isMentionSign()) return null
    return MentionToken(start = start, end = cursor, text = text.substring(start, cursor))
}

/**
 * [text] with [token] replaced by `@`[username] and one space, and the cursor after that space.
 * When whitespace already follows the word, no space is added and the cursor goes after that one.
 *
 * Only the token moves: whatever came before the `@` and after the cursor is kept as it was. The
 * space ends the word, so the picker closes on the same rule as typing one.
 */
internal fun insertMention(text: String, token: MentionToken, username: String): Pair<String, Int> {
    // Whitespace already after the word separates the mention; the cursor goes past it instead of
    // doubling it. Anywhere else, the mention brings its own space.
    val followedBySpace = text.getOrNull(token.end)?.isWhitespace() == true
    val inserted = if (followedBySpace) "@$username" else "@$username "
    val result = text.substring(0, token.start) + inserted + text.substring(token.end)
    return result to token.start + inserted.length + if (followedBySpace) 1 else 0
}

/** Only members with a username can be mentioned: the username is what gets inserted. */
internal fun List<MemberMatch>.mentionable(): List<MemberMatch> = filter { !it.username.isNullOrBlank() }

/** The least room left for the transcript above the picker before it gives up rows for it. */
internal val MentionMinTranscriptHeight = 120.dp

/**
 * How many rows the picker shows before it scrolls: 4, or 3 with a reply strip under it, and 2
 * when showing that many would leave less than [MentionMinTranscriptHeight] of transcript.
 *
 * [roomAboveComposer] is the height between the top bar and the rest of the composer (reply strip
 * and input row), which the picker and the transcript share; [listHeight] is the picker's height
 * at a given row count.
 */
internal fun mentionRowCap(
    replyOpen: Boolean,
    roomAboveComposer: Dp,
    listHeight: (rows: Int) -> Dp,
): Int {
    val cap = if (replyOpen) 3 else 4
    return if (roomAboveComposer - listHeight(cap) < MentionMinTranscriptHeight) 2 else cap
}
