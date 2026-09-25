package com.flipcash.shared.chat.models

import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.MessageCapability
import com.flipcash.shared.chat.reactions.ReactionPill
import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

enum class ReceiptStatus { SENDING, SENT, READ, FAILED }

sealed interface ChatListItem {
    val itemKey: Any
    val itemContentType: Any

    data class DateSeparator(val timestamp: Instant) : ChatListItem {
        override val itemKey: Any = "sep-${timestamp.epochSeconds}"
        override val itemContentType: Any = "date-separator"
    }

    /**
     * "N Unread Messages", above the first message that arrived after the viewer last read the
     * chat. [date] is set when a day also changes at that gap: the list takes one item per gap, so
     * the date is drawn above the divider rather than as a separator of its own.
     *
     * At most one per transcript, so its key is fixed.
     */
    data class UnreadDivider(val count: Int, val date: Instant? = null) : ChatListItem {
        override val itemKey: Any = "unread-divider"
        override val itemContentType: Any = "unread-divider"
    }

    data class ContentBubble(
        val messageId: Long,
        val contentIndex: Int,
        val content: MessageContent,
        val isFromSelf: Boolean,
        val timestamp: Instant,
        val receiptStatus: ReceiptStatus? = null,
        val pendingClientIdHex: String? = null,
        /** Drives the corner-pinned "Edited" marker. */
        val isEdited: Boolean = false,
        /** Chooses between "You deleted this message" and "This message was deleted". */
        val deletedByViewer: Boolean = false,
        /**
         * What the viewer may do to the message this bubble belongs to, resolved once in the
         * transcript pipeline by
         * [resolveCapabilities][com.flipcash.shared.chat.resolveCapabilities].
         *
         * Carried on the bubble so no surface re-derives it: the selection bar asks the set what to
         * offer, and a later role taxonomy changes the resolver rather than the menu.
         */
        val capabilities: Set<MessageCapability> = emptySet(),
        /**
         * The message this one cites, resolved in the transcript pipeline, or `null` when it cites
         * nothing — or when it cites a message this device has never stored. The absent case
         * renders the body without a panel rather than an error, so a reply to history that was
         * never synced still reads as a message.
         */
        val quote: ChatQuote? = null,
        /**
         * A link in this bubble's text that renders as a card above it, or `null` when the text
         * carries no card-eligible link. Built in the chat mapper, which is the only layer that
         * can see both the router and the network; `chat-ui` only draws what it is handed.
         */
        val linkCard: LinkCard? = null,
        /**
         * Who sent this, when that is not implied. Null for the viewer's own messages and for every
         * message in a DM; set only for another member's message in a group.
         */
        val sender: SenderIdentity? = null,
        /**
         * The id of whoever sent this, for every incoming message — set whether or not their
         * profile has resolved, and whether or not the chat attributes its bubbles.
         *
         * [sender] cannot stand in for it: a group's profile map arrives after the first page does,
         * so early on every member's bubble has a null [sender] and two members read as one author.
         */
        val senderId: ID? = null,
        /**
         * Which row of a message split around its link card this is, or `null` for a row that is
         * the whole message. See [splitAroundLinkCard].
         *
         * Every row of one message carries the same [messageId], [content] and [capabilities], so
         * the selection bar, copy, edit, reply, retry and a jump act on the message from whichever
         * row they start on.
         */
        val part: MessagePart? = null,
        /**
         * The text a [MessagePart.Leading] or [MessagePart.Trailing] row draws. [content] keeps the
         * whole message, because that is what Copy and Edit act on.
         */
        val partText: String? = null,
        /** Whether this is the topmost row of its message, which is where a reply's citation goes. */
        val isFirstRow: Boolean = true,
        /**
         * Whether this is the bottommost row of its message, which is where the "Edited" marker and
         * the receipt go.
         */
        val isLastRow: Boolean = true,
        /**
         * This message's reaction pills, in display order — the live overlay when one exists,
         * otherwise `MessageReactions.from` applied to the message's own stored summary. Empty
         * for a message with no reactions.
         */
        val reactionPills: List<ReactionPill> = emptyList(),
        /**
         * Whether the viewer may react to this message — system messages and messages still
         * pending send are excluded. See `com.flipcash.shared.chat.canReact`.
         */
        val canReact: Boolean = false,
    ) : ChatListItem {
        /**
         * Who this bubble is attributed to, for grouping. [senderId] is the answer whenever the
         * pipeline supplies it; [sender] covers a caller that builds a bubble from a resolved
         * profile alone.
         */
        val authorId: ID? get() = senderId ?: sender?.userId

        /**
         * Whether [other] was sent by the same person as this bubble.
         *
         * `isFromSelf` cannot answer this in a group, where every other member's message is
         * incoming alike: two members' bubbles would read as one run. [authorId] is what tells them
         * apart, and it is null only for the viewer's own messages — a case `isFromSelf` already
         * decides — and for an incoming message whose sender the backend did not name.
         */
        fun isSameAuthorAs(other: ContentBubble): Boolean = when {
            isFromSelf != other.isFromSelf -> false
            isFromSelf -> true
            else -> authorId == other.authorId
        }

        /** The body a Copy or an Edit acts on, or `null` for a bubble that carries no text. */
        val plainText: String?
            get() = when (val content = content) {
                is MessageContent.Text -> content.text
                // Copy and edit act on what the user wrote, not on the citation around it.
                // PendingMutation.replacingText unwraps the same way when the edit is applied.
                is MessageContent.Reply ->
                    content.content.filterIsInstance<MessageContent.Text>().firstOrNull()?.text
                else -> null
            }

        /**
         * Whether long-pressing this bubble should open the selection bar.
         *
         * Any capability is enough. Reply used to be excluded because it had no surface, which left
         * a cash bubble — whose only capability is Reply — unselectable.
         */
        val isSelectable: Boolean
            get() = capabilities.isNotEmpty()

        /**
         * The message this row draws, shared by every row it is split into. Selection compares on
         * this rather than on [itemKey], so the whole message stays sharp behind the backdrop.
         */
        val messageKey: Any get() = pendingClientIdHex ?: "$messageId-$contentIndex"

        override val itemKey: Any = (pendingClientIdHex ?: "$messageId-$contentIndex")
            .let { key -> part?.let { "$key#${it.id}" } ?: key }

        // A tombstone shares the text bubble's content type on purpose: deleting a message is an
        // in-place update of a row the list already holds, and giving it a type of its own would
        // make the list drop that row and insert a new one.
        //
        // A carded bubble is the exception, and deliberately so: it is a taller, differently
        // shaped subtree, and recycling it against a plain text bubble would hand the card's
        // slot to text. Deleting one does drop and re-insert the row, which is right -- the
        // card has to go, and there is no in-place update that removes it.
        override val itemContentType: Any = when {
            part == MessagePart.Card -> "link-card-bubble"
            else -> when (content) {
                is MessageContent.Text -> "text-bubble"
                is MessageContent.Deleted -> "text-bubble"
                is MessageContent.Cash -> "cash-bubble"
                is MessageContent.Media -> "media"
                is MessageContent.Reply -> "reply-message"
                is MessageContent.System -> "system-message"
            }
        }
    }
}

/**
 * One row of a message the transcript split around its link card, in the order the sender wrote
 * them. [id] is the suffix on the row's [ChatListItem.itemKey]; iOS uses the same three.
 */
enum class MessagePart(val id: String) {
    /** The text before the link. */
    Leading("leading"),

    /** The card, drawn bare. */
    Card("card"),

    /** The text after the link. */
    Trailing("trailing"),
}

/**
 * The rows this bubble draws, top to bottom: the text before its card, the card, and the text after
 * it, each dropped when it would hold nothing but whitespace. A bubble with no card is one row, as
 * it always was.
 *
 * The whitespace on either side of the link goes with it -- it was the gap around a word that now
 * has a row of its own. Other links stay in whichever text row they fell in, underlined.
 *
 * The reply citation goes on the first row and the "Edited" marker and receipt on the last, so a
 * split message reads as one message stacked in three pieces rather than as three messages.
 *
 * A card whose span does not fit the text is dropped rather than split, and the message renders as
 * text with its links underlined.
 */
fun ChatListItem.ContentBubble.splitAroundLinkCard(): List<ChatListItem.ContentBubble> {
    val card = linkCard ?: return listOf(this)
    val text = plainText ?: return listOf(copy(linkCard = null))
    if (card.start < 0 || card.end > text.length || card.start >= card.end) {
        return listOf(copy(linkCard = null))
    }

    var leadingEnd = card.start
    while (leadingEnd > 0 && text[leadingEnd - 1].isWhitespace()) leadingEnd--
    var trailingStart = card.end
    while (trailingStart < text.length && text[trailingStart].isWhitespace()) trailingStart++

    val parts = listOfNotNull(
        text.substring(0, leadingEnd).takeIf { it.isNotBlank() }?.let { MessagePart.Leading to it },
        MessagePart.Card to null,
        text.substring(trailingStart).takeIf { it.isNotBlank() }?.let { MessagePart.Trailing to it },
    )

    return parts.mapIndexed { index, (part, segment) ->
        copy(
            part = part,
            partText = segment,
            linkCard = card.takeIf { part == MessagePart.Card },
            isFirstRow = index == 0,
            isLastRow = index == parts.lastIndex,
        )
    }
}
