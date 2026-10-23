package com.flipcash.services.models.chat

import com.flipcash.services.models.UserProfile

/**
 * Whether a DM with [counterparty] can still be addressed, and so whether it stays usable.
 *
 * The two kinds of DM are addressed differently, and the gate follows from that:
 *
 * - A `CONTACT_DM` is reached through the counterparty's phone number and named from the device
 *   contact. If they unlink their phone and have no display name left, there is nothing to address
 *   or name them by, so the chat is dropped from the feed and the open conversation goes read-only.
 * - A `TIP_DM` is reached by user id. The counterparty has no phone by design and need never have
 *   set a display name — a claimed `@handle` names them instead. Gating one would hide
 *   or deactivate a conversation that is perfectly addressable, so it never is.
 *
 * Any other [chatType] — including [ChatType.UNKNOWN], which is what a conversation reports until
 * its kind resolves — is left alone. That matters for the open conversation: gating on an
 * unresolved type would flash the deactivated composer before the type settles.
 *
 * This is one rule with two callers on purpose. It was previously written out at both, and the two
 * copies disagreed: the feed exempted tip DMs and the conversation did not, so a name-less tipper
 * appeared in the tips list and then opened read-only.
 */
fun isDmAddressable(chatType: ChatType, counterparty: UserProfile): Boolean =
    if (chatType == ChatType.CONTACT_DM) {
        counterparty.displayName.isNotBlank() || !counterparty.verifiedPhoneNumber.isNullOrBlank()
    } else {
        true
    }
