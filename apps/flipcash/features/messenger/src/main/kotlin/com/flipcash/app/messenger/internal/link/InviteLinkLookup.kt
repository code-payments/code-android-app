package com.flipcash.app.messenger.internal.link

import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.chat.Invite
import com.flipcash.services.models.chat.InviteToken
import com.flipcash.shared.chat.models.LinkCard
import com.getcode.chatcipher.InviteCipher
import javax.inject.Inject

/**
 * Reads an invite link's group for its card (spec surface 6): the secret's token, then `GetInvite`,
 * which answers whether or not the viewer is in the group. Only the title, picture and member
 * count reach the card, as on a join link's card, and no member is named.
 *
 * A link the server does not know, a banned viewer and a failed call all fail the lookup, and the
 * card draws its unavailable state; the landing a tap opens says which it was.
 */
internal class InviteLinkLookup @Inject constructor(
    private val inviteCipher: InviteCipher,
    private val chatController: ChatController,
) {
    suspend operator fun invoke(secret: ByteArray): Result<LinkCard.InviteLink.State.Resolved> =
        runCatching {
            val token = InviteToken(inviteCipher.inviteToken(secret))
            val preview = chatController.getInvite(token).getOrThrow()
            LinkCard.InviteLink.State.Resolved(
                chatId = preview.chat.chatId,
                title = preview.chat.title?.takeIf { it.isNotBlank() },
                picture = preview.chat.picture,
                memberCount = preview.chat.rosterSummary.memberCount,
                status = preview.status.toCardStatus(),
            )
        }
}

/** UNKNOWN reads as active, as it does on the landing, which lets the join answer for it. */
private fun Invite.Status.toCardStatus(): LinkCard.InviteLink.Status = when (this) {
    Invite.Status.ACTIVE, Invite.Status.UNKNOWN -> LinkCard.InviteLink.Status.Active
    Invite.Status.EXHAUSTED -> LinkCard.InviteLink.Status.Exhausted
    Invite.Status.EXPIRED -> LinkCard.InviteLink.Status.Expired
    Invite.Status.REVOKED -> LinkCard.InviteLink.Status.Revoked
}
