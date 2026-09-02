package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A single thing the viewer is allowed to do to a message.
 *
 * Permissions are modelled as capabilities rather than roles. `Member` on the wire is
 * `{ user_id, user_profile, pointers }` with no role field, and the server only ever answers
 * `DENIED`, `CANNOT_EDIT`, or `CANNOT_DELETE` — so anything the client decides here is scaffolding
 * the server overrules. Resolving a set of capabilities means a later role taxonomy becomes one
 * more input to [resolveCapabilities] and no call site changes: a menu asks what can be done to a
 * message, never who the viewer is.
 *
 * [Reply] is resolved but not yet wired to a surface. It is here so the reply work lands as a new
 * menu row rather than a second capability model.
 */
enum class MessageCapability {
    Copy,
    Reply,
    Edit,
    Delete,
}

/**
 * Client-side limits on what may be done to a message.
 *
 * @param editWindow how long after sending a message stays editable, or `null` for no limit. The
 *   server does not publish a window today, so the default leaves edit open and lets `CANNOT_EDIT`
 *   be the authority.
 */
data class MessagePolicy(
    val editWindow: Duration? = null,
) {
    companion object {
        val Default = MessagePolicy()
    }
}

/**
 * Resolves what [message] allows, per the capability table shared with iOS:
 *
 * | Message | Capabilities |
 * |---|---|
 * | Own text, confirmed, within the edit window | Copy, Reply, Edit, Delete |
 * | Own text, confirmed, outside a configured window | Copy, Reply, Delete |
 * | Own text, unconfirmed (`eventSequence == 0`) | none |
 * | Another participant's text | Copy, Reply |
 * | Any cash or tip message | Reply |
 * | A tombstone | none |
 */
fun resolveCapabilities(
    message: ChatMessage,
    policy: MessagePolicy = MessagePolicy.Default,
    now: Instant = Clock.System.now(),
): Set<MessageCapability> {
    val contents = message.content
    if (contents.isEmpty()) return emptySet()

    // Nothing left to act on: there is no text to copy and the delete already happened.
    if (contents.any { it is MessageContent.Deleted }) return emptySet()

    // `expected_event_sequence` is validated `>= 1`, so no valid edit or delete request can be
    // built for a message the server has not acknowledged. The empty set is not a style choice —
    // offering copy alone on a message that may still fail to send reads as a half-broken menu.
    if (message.eventSequence == 0L) return emptySet()

    // Cash is never editable: `EditMessageRequest.content` accepts Text, Reply, and Media, never
    // Cash. It is deliberately not deletable either, so a payment cannot be hidden from the
    // transcript that records it.
    if (contents.any { it is MessageContent.Cash }) return setOf(MessageCapability.Reply)

    // Server-authored notices, not a participant's message.
    if (contents.all { it is MessageContent.System }) return emptySet()

    val hasText = contents.any { it is MessageContent.Text || it is MessageContent.Reply }

    return buildSet {
        // Media carries no text, and this change edits text only. Not covered by the shared table;
        // revisit when media messages actually ship.
        if (hasText) add(MessageCapability.Copy)
        add(MessageCapability.Reply)
        if (message.isFromSelf) {
            if (hasText && policy.allowsEdit(message, now)) add(MessageCapability.Edit)
            add(MessageCapability.Delete)
        }
    }
}

/** True while [message] is still inside the configured edit window, or always if there is none. */
private fun MessagePolicy.allowsEdit(message: ChatMessage, now: Instant): Boolean {
    val window = editWindow ?: return true
    return now - message.timestamp <= window
}
