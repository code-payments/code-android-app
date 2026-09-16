package com.flipcash.app.notifications

import com.flipcash.services.models.chat.ChatType
import com.getcode.opencode.model.core.ID

/**
 * Which member of a chat a push should be attributed to.
 *
 * Pure by construction, for the same reason [planPushHandling] is: the rule is
 * what's worth testing, and it doesn't need a database or a live service to
 * state.
 */
sealed interface SenderLookup {
    /** Fetch this specific member — the user the server says sent the message. */
    data class ByUserId(val userId: ID) : SenderLookup

    /** Fetch "the member who isn't me". Only identifies a sender in a DM. */
    data object OtherMember : SenderLookup

    /** No member to fetch; render the sender from the push's own title. */
    data object None : SenderLookup
}

/**
 * Decides how to identify the sender of a chat push.
 *
 * [sendingUserId] is the answer whenever the payload carries one, and it is the
 * only answer that works in a group: [SenderLookup.OtherMember] returns the first
 * member that isn't you, which is the sender in a DM and an arbitrary participant
 * anywhere else.
 *
 * @param chatType the chat's type, null when the payload carries no metadata
 * @param sendingUserId the sender per the payload, null for a system message
 * @param hasDeviceContact whether the chat is already linked to an address-book
 *   contact, which identifies a DM's counterparty without any fetch
 */
fun planSenderLookup(
    chatType: ChatType?,
    sendingUserId: ID?,
    hasDeviceContact: Boolean,
): SenderLookup = when {
    // A device-contact link is keyed by DM chat id, so the contact it resolves to is
    // that DM's counterparty — the sender of anything we're notified about there, and
    // a better identity than the server profile because it's the name and photo the
    // user chose for them. Worth short-circuiting the member fetch for.
    hasDeviceContact -> SenderLookup.None
    sendingUserId != null -> SenderLookup.ByUserId(sendingUserId)
    // A system message in a group has no sender. Guessing one would put a
    // participant's name and face on something they didn't say.
    chatType == ChatType.GROUP -> SenderLookup.None
    else -> SenderLookup.OtherMember
}
