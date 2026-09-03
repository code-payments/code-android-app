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
 * Both windows come from `UserFlags` (`message_edit_window`, `message_delete_window`), which sends
 * them with explicit presence: an unset field is no limit rather than a zero-length one. The
 * defaults are therefore both `null`, which leaves `CANNOT_EDIT` / `CANNOT_DELETE` as the
 * authority for a build that has not seen the flags yet.
 *
 * @param editWindow how long after sending a message stays editable, or `null` for no limit.
 * @param deleteWindow how long after sending a message stays deletable, or `null` for no limit.
 */
data class MessagePolicy(
    val editWindow: Duration? = null,
    val deleteWindow: Duration? = null,
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
 * | Own text, confirmed, inside both windows | Copy, Reply, Edit, Delete |
 * | Own text, confirmed, past the edit window | Copy, Reply, Delete |
 * | Own text, confirmed, past both windows | Copy, Reply |
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
            if (hasText) add(MessageCapability.Edit)
            add(MessageCapability.Delete)
        }
    }.withinWindows(message.timestamp, policy, now)
}

/**
 * Drops the capabilities of a message sent at [sentAt] whose window has since closed.
 *
 * Split out of [resolveCapabilities] because resolution happens once, when the transcript is
 * mapped, and the windows keep running afterwards: a menu opened a minute later would otherwise
 * still offer an edit the server is about to answer `CANNOT_EDIT`. A surface holding an
 * already-resolved set re-applies this when it acts on it, and gets the same answer the resolver
 * would give — the rule lives in one place either way.
 */
fun Set<MessageCapability>.withinWindows(
    sentAt: Instant,
    policy: MessagePolicy,
    now: Instant = Clock.System.now(),
): Set<MessageCapability> = filterTo(mutableSetOf()) { capability ->
    when (capability) {
        MessageCapability.Edit -> policy.editWindow.stillOpen(sentAt, now)
        MessageCapability.Delete -> policy.deleteWindow.stillOpen(sentAt, now)
        MessageCapability.Copy, MessageCapability.Reply -> true
    }
}

/** True while a message sent at [sentAt] is inside this window, or always if there is none. */
private fun Duration?.stillOpen(sentAt: Instant, now: Instant): Boolean =
    this == null || now - sentAt <= this
