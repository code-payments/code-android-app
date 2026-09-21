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
    Report,
}

/**
 * Client-side limits on what may be done to a message.
 *
 * Both windows come from `UserFlags` (`message_edit_window`, `message_delete_window`), which sends
 * them with explicit presence: an unset field is distinguishable from a zero-length one. Where the
 * server sends nothing, [fromFlags] substitutes [FallbackEditWindow] / [FallbackDeleteWindow]
 * rather than leaving the action open forever, so a message old enough can lose Edit or Delete
 * even where the server would have taken the request. That is the accepted cost: an affordance the
 * server answers `CANNOT_EDIT` / `CANNOT_DELETE` is the worse failure.
 *
 * Neither window has a default here. Absence is a real input — it decides whether the fallback
 * applies — so it is worth stating at the call site rather than inheriting.
 *
 * @param editWindow how long after sending a message stays editable, or `null` for no limit.
 * @param deleteWindow how long after sending a message stays deletable, or `null` for no limit.
 */
data class MessagePolicy(
    val editWindow: Duration?,
    val deleteWindow: Duration?,
) {
    companion object {
        /**
         * The window applied when the server sends no edit window.
         *
         * Maintained in parallel with iOS `MessagePolicy.fallbackEditWindow`
         * (`FlipcashCore/Sources/FlipcashCore/Models/Conversation/MessagePolicy.swift`). The two
         * must move together or the clients offer different rows for the same message; nothing
         * enforces it, so changing one means changing the other in the same release.
         *
         * The value is a product choice, not a figure the contract supplies: `message_edit_window`
         * documents what it means but never what an absent field implies. Replace it the moment the
         * server does specify one.
         */
        val FallbackEditWindow = 15.minutes

        /**
         * The window applied when the server sends no delete window. Same parallel-maintenance duty
         * and same provenance as [FallbackEditWindow]; iOS holds it as
         * `MessagePolicy.fallbackDeleteWindow`.
         */
        val FallbackDeleteWindow = 48.hours

        /**
         * Builds the policy in force from the windows the server sent, substituting the fallbacks
         * for anything it left unset.
         *
         * Both arguments are nullable because every upstream state collapses to the same one:
         * flags not yet fetched, a fetch that failed, and flags whose window fields are unset all
         * arrive as `null` and all get the fallback. There is no second path to keep in step.
         */
        fun fromFlags(editWindow: Duration?, deleteWindow: Duration?) = MessagePolicy(
            editWindow = editWindow ?: FallbackEditWindow,
            deleteWindow = deleteWindow ?: FallbackDeleteWindow,
        )

        /** The policy in force before any flags have been read: the fallback windows. */
        val Default = fromFlags(editWindow = null, deleteWindow = null)
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
 * | Another participant's text | Copy, Reply, Report |
 * | Own cash or tip message | Reply |
 * | Another participant's cash or tip message | Reply, Report |
 * | A tombstone | none |
 * | A system notice | none |
 *
 * Report follows one rule: anything a participant sent can be reported, and anything the server
 * wrote, or that no longer exists, cannot. Your own messages are left out because reporting one is
 * not a thing anyone does, and a row that is always present is a row people stop reading.
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
    if (contents.any { it is MessageContent.Cash }) {
        return buildSet {
            add(MessageCapability.Reply)
            if (!message.isFromSelf) add(MessageCapability.Report)
        }
    }

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
        } else {
            add(MessageCapability.Report)
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
        // Reporting has no window on purpose: the edit and delete windows exist because the
        // server enforces them, and nothing in the contract limits how old a reportable message
        // may be.
        MessageCapability.Copy, MessageCapability.Reply, MessageCapability.Report -> true
    }
}

/** True while a message sent at [sentAt] is inside this window, or always if there is none. */
private fun Duration?.stillOpen(sentAt: Instant, now: Instant): Boolean =
    this == null || now - sentAt <= this
