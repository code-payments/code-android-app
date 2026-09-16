package com.flipcash.app.notifications

import com.flipcash.services.models.chat.ChatType

/**
 * How a chat push should render as a conversation.
 *
 * Pure for the same reason [planSenderLookup] is: the rule is what's worth testing, and stating
 * it needs neither a database nor a live service.
 */
data class ConversationStyling(
    /** The chat's type, whichever source could answer. Also what the sender lookup keys off. */
    val chatType: ChatType,
    /**
     * Whether `MessagingStyle` should treat this as a group.
     *
     * Without it the platform renders every message as a one-to-one: no conversation title, and
     * the sender's name dropped from the line because in a DM the notification's own title
     * already says who it is.
     */
    val isGroupConversation: Boolean,
    /**
     * What to call the group, or null to leave whatever is already on screen.
     *
     * Null rather than a placeholder because a re-posted notification carries the previous
     * title forward, and a group whose row hasn't synced yet is better untitled than renamed.
     */
    val conversationTitle: String?,
)

/**
 * Decides whether a chat push is a group conversation and what to call it.
 *
 * The type is taken from [payloadChatType] first and [storedChatType] second. `chat_metadata` is
 * optional on the wire, and a push that omits it would otherwise pass for a DM — which is how a
 * group ends up rendered as one, with an arbitrary participant's name on it. The device already
 * knows the type of any chat it has synced, so it only falls through to [ChatType.UNKNOWN] for a
 * group this device has never seen.
 *
 * @param payloadChatType the type the push declares, null when it carries no chat metadata
 * @param storedChatType the type in `chat_metadata`, [ChatType.UNKNOWN] when the chat isn't stored
 * @param storedTitle the group's name in `chat_metadata`, null for a DM or an unstored chat
 */
fun planConversationStyling(
    payloadChatType: ChatType?,
    storedChatType: ChatType,
    storedTitle: String?,
): ConversationStyling {
    val chatType = payloadChatType?.takeUnless { it == ChatType.UNKNOWN } ?: storedChatType
    val isGroup = chatType == ChatType.GROUP
    return ConversationStyling(
        chatType = chatType,
        isGroupConversation = isGroup,
        // A DM is named by its counterparty, so a title on one would be the wrong name in a
        // place the user can't correct. Blank is the same as absent.
        conversationTitle = storedTitle?.takeIf { isGroup && it.isNotBlank() },
    )
}
