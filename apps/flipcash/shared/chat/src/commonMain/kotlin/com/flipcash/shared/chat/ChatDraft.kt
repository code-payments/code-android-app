package com.flipcash.shared.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The reply strip's own copy of the message it cites, rather than a pointer into the transcript.
 *
 * A draft outlives the loaded message window — it is read back after a process death, before any
 * history is fetched — so the strip has to be able to render from what was stored. A cited message
 * missing from the window is not a deleted message; the strip is dropped only when the message is
 * known to be deleted, and the draft text survives that.
 *
 * [senderIdHex] is here because the strip's accent colours are derived from the sender's id rather
 * than stored with the quote. Null for a sender the composer could not identify, which costs the
 * colours and nothing else.
 */
@Serializable
data class ChatDraftReply(
    val messageId: Long,
    val authorName: String,
    val senderIdHex: String? = null,
    val snippet: ChatDraftSnippet,
)

/** What the reply strip shows of the cited message. */
@Serializable
sealed interface ChatDraftSnippet {
    @Serializable
    @SerialName("text")
    data class Text(val body: String) : ChatDraftSnippet

    /**
     * Cash is stored as its parts rather than as the domain amount type: this JSON sits in a
     * database row that has to keep parsing after that type moves.
     */
    @Serializable
    @SerialName("cash")
    data class Cash(
        val quarks: Long,
        val currencyCode: String,
        val tokenName: String,
    ) : ChatDraftSnippet
}

/**
 * What the composer would leave behind right now: the text as typed, and the reply target if one
 * is aimed. No [ChatDraft.savedAt] — that is the store's to stamp.
 */
data class ChatDraftSnapshot(
    val text: String,
    val replyTarget: ChatDraftReply?,
) {
    /**
     * Whether this snapshot is the absence of a draft. Text that trims away *and* no reply target
     * means the row is deleted rather than written as `""`: a chat typed in once and then cleared
     * would otherwise restore an empty draft forever. A missing row and an empty one are different
     * states, and only the first is reachable.
     */
    val isEmpty: Boolean
        get() = text.isBlank() && replyTarget == null

    companion object {
        val Empty = ChatDraftSnapshot(text = "", replyTarget = null)
    }
}

/** A stored draft, as read back. */
data class ChatDraft(
    val text: String,
    val replyTarget: ChatDraftReply?,
    val savedAt: Long,
)

/**
 * The composer's state reduced to what gets persisted.
 *
 * Two rules live here rather than at the call site, so the ViewModel and the vector suite are
 * running the same code:
 *
 * - **Saved verbatim.** No trimming on the way in; a trailing space mid-sentence is part of what
 *   was being typed. Trimming stays where it already is, at submit.
 * - **An edit is never what persists.** While [editStash] is non-null the composer is showing an
 *   older message being edited, and what the draft holds is the new-message text the edit
 *   displaced — the same text cancelling the edit would put back. Leaving a chat mid-edit
 *   therefore returns you to new-message mode with your own words.
 */
fun chatDraftOf(
    composerText: String,
    replyTarget: ChatDraftReply?,
    editStash: String?,
): ChatDraftSnapshot = if (editStash != null) {
    ChatDraftSnapshot(text = editStash, replyTarget = null)
} else {
    ChatDraftSnapshot(text = composerText, replyTarget = replyTarget)
}
