package com.flipcash.app.messenger.internal

import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.handle
import com.flipcash.services.models.nameOrHandle
import com.flipcash.shared.chat.MessageReactions
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.SenderIdentity

/**
 * The one row a message link draws when it lands in a group whose transcript is withheld from the
 * viewer (flow 6): [message] as `GetMessage` in `FULL_OR_REDACTED` answered it, over the blurred
 * placeholder transcript.
 *
 * Display only. It carries no capabilities and cannot be reacted to, so nothing selects, replies
 * to, copies or reacts to it; [ChatListItem.ContentBubble.redacted] keeps a withheld body blurred.
 * The sender, time and reactions are the server's whole answer even to a redacted reader, so they
 * draw as they are.
 *
 * Null for a message with nothing to draw: deleted, or no content at all.
 *
 * @param senderProfile the sender's profile once it has resolved, or null to draw the row without
 * a name until it does.
 * @param unnamed what the name line says for a sender with neither a name nor a handle.
 */
internal fun gatedLandingBubble(
    message: ChatMessage,
    senderProfile: UserProfile?,
    unnamed: String,
): ChatListItem.ContentBubble? {
    val content = message.content.firstOrNull() ?: return null
    if (content is MessageContent.Deleted) return null
    val senderId = message.senderId?.takeIf { !message.isFromSelf }
    val sender = senderId?.let { id ->
        senderProfile?.let { profile ->
            SenderIdentity(
                userId = id,
                displayName = nameOrHandle(profile.displayName, profile.handle) ?: unnamed,
                initialsName = nameOrHandle(profile.displayName, profile.handle),
                picture = profile.profilePicture,
            )
        }
    }
    return ChatListItem.ContentBubble(
        messageId = message.messageId,
        contentIndex = 0,
        content = content,
        isFromSelf = message.isFromSelf,
        timestamp = message.timestamp,
        isEdited = message.lastEditedTs != null,
        capabilities = emptySet(),
        sender = sender,
        senderId = senderId,
        reactionPills = MessageReactions.from(message.reactions).pills,
        canReact = false,
        redacted = message.redacted,
    )
}
