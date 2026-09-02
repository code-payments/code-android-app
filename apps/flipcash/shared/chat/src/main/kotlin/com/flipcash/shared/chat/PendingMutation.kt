package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

/**
 * An edit or delete the user has asked for and the server has not yet answered.
 *
 * The database holds the server's version and only the server's version. A mutation in flight is
 * held here instead and composed over the stored row on the way to the screen, so the transcript
 * updates on the tap while the row underneath stays truthful. Reconciliation is then a matter of
 * dropping the overlay — there is no local write to undo.
 *
 * @param expectedSequence the message's `eventSequence` at the moment the request was built. It is
 *   what goes on the wire as `expected_event_sequence`, and it is what makes the overlay
 *   self-expiring: see [applying].
 */
data class PendingMutation(
    val messageId: Long,
    val expectedSequence: Long,
    val kind: Kind,
) {
    sealed interface Kind {
        /** The body is shown as [text] until the server confirms it. */
        data class Edited(val text: String, val editedAt: Instant) : Kind

        /** The bubble is shown as a tombstone until the server confirms it. */
        data class Deleted(val deletedAt: Instant, val deletedBy: ID?) : Kind
    }
}

/**
 * Composes [mutation] over this message, or returns it untouched when there is nothing pending.
 *
 * The overlay expires on a strictly higher `eventSequence`: once the stored row has moved past the
 * sequence the request was built against, the server has spoken — whether it agreed, or something
 * else changed the message first — and the row is the better answer. That guard is what lets a
 * caller persist the server's reply and drop the overlay in either order without flashing stale
 * text at the reader.
 */
fun ChatMessage.applying(mutation: PendingMutation?): ChatMessage {
    if (mutation == null) return this
    if (eventSequence > mutation.expectedSequence) return this

    return when (val kind = mutation.kind) {
        is PendingMutation.Kind.Edited -> copy(
            content = content.replacingText(kind.text),
            lastEditedTs = kind.editedAt,
        )

        is PendingMutation.Kind.Deleted -> copy(
            content = listOf(MessageContent.Deleted(kind.deletedAt, kind.deletedBy)),
        )
    }
}

/**
 * Swaps the body text while leaving everything wrapping it in place — so editing a reply keeps its
 * citation instead of flattening it to a bare text message.
 */
internal fun List<MessageContent>.replacingText(text: String): List<MessageContent> =
    map { content ->
        when (content) {
            is MessageContent.Text -> MessageContent.Text(text)
            is MessageContent.Reply -> content.copy(content = content.content.replacingText(text))
            else -> content
        }
    }
