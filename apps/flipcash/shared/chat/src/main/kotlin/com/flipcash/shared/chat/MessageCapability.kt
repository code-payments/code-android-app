package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
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
 * The server publishes both windows in `account.v1.UserFlags` with explicit presence, so an unset
 * field reaches the client as `null` meaning "the server said nothing" — not "no limit". [from]
 * turns that silence into [FallbackEditWindow] / [FallbackDeleteWindow] rather than leaving the
 * action open. The same substitution covers a failed flags fetch without a second code path:
 * `UserFlagsCoordinator.resolvedFlags` falls back to `UserFlags.Default` whenever the server flags
 * are absent, and `UserFlags.Default` carries `null` for both windows.
 *
 * This inverts the rule this class shipped with, which left edit open and made the server's
 * `CANNOT_EDIT` the sole authority. What that bought was never offering less than the server would
 * allow; what it cost was a menu that confidently offers Edit on a week-old message and fails on
 * tap. The fallback trades one for the other: with it in force a server that sends nothing has the
 * client's window imposed on it, so an edit the server would have accepted 20 minutes after sending
 * is hidden at 15. That is the cheaper failure — a missing row is legible, a row that errors on tap
 * is not — and the inversion is only in what the client *offers*. The server remains the authority
 * for everything the client does offer: `CANNOT_EDIT` and `CANNOT_DELETE` still backstop the gap
 * between resolving a menu and the request landing.
 *
 * A `null` window still means no limit, and is now reachable only by naming it — [from] never
 * produces one, and both defaults are windows rather than `null`, so a call site that forgets to
 * pass a policy is gated rather than unbounded.
 *
 * @param editWindow how long after sending a message stays editable, or `null` for no limit.
 * @param deleteWindow how long after sending a message stays deletable, or `null` for no limit.
 */
data class MessagePolicy(
    val editWindow: Duration? = FallbackEditWindow,
    val deleteWindow: Duration? = FallbackDeleteWindow,
) {
    companion object {
        /**
         * Applied when `message_edit_window` is unset or the flags fetch failed.
         *
         * Named rather than inlined so the value is greppable from both platforms: iOS applies the
         * same 15 minutes, and the two have to agree for the same message to resolve the same
         * capability set.
         */
        val FallbackEditWindow: Duration = 15.minutes

        /** Applied when `message_delete_window` is unset or the flags fetch failed. iOS: 48 hours. */
        val FallbackDeleteWindow: Duration = 48.hours

        val Default = MessagePolicy()

        /**
         * Builds a policy from the server's windows, substituting the fallback for either one the
         * server left unset.
         */
        fun from(editWindow: Duration?, deleteWindow: Duration?) = MessagePolicy(
            editWindow = editWindow ?: FallbackEditWindow,
            deleteWindow = deleteWindow ?: FallbackDeleteWindow,
        )
    }
}

/**
 * Resolves what [message] allows, per the capability table shared with iOS:
 *
 * | Message | Capabilities |
 * |---|---|
 * | Own text, confirmed, within both windows | Copy, Reply, Edit, Delete |
 * | Own text, confirmed, past the edit window | Copy, Reply, Delete |
 * | Own text, confirmed, past both windows | Copy, Reply |
 * | Own text, unconfirmed (`eventSequence == 0`) | none |
 * | Another participant's text | Copy, Reply |
 * | Any cash or tip message | Reply |
 * | A tombstone | none |
 *
 * [now] is a parameter rather than read inline so a caller can re-resolve a message it is already
 * showing: the set is a function of the clock, and a row that resolved Edit does not keep it.
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
            if (policy.allowsDelete(message, now)) add(MessageCapability.Delete)
        }
    }
}

/** True while [message] is still inside the configured edit window, or always if there is none. */
private fun MessagePolicy.allowsEdit(message: ChatMessage, now: Instant): Boolean =
    editWindow.admits(message, now)

/** True while [message] is still inside the configured delete window, or always if there is none. */
private fun MessagePolicy.allowsDelete(message: ChatMessage, now: Instant): Boolean =
    deleteWindow.admits(message, now)

/**
 * Both windows are measured from the send time, not from the last edit, and both are inclusive:
 * a message at exactly the window length is still actionable. iOS matches on both counts.
 */
private fun Duration?.admits(message: ChatMessage, now: Instant): Boolean {
    val window = this ?: return true
    return now - message.timestamp <= window
}
