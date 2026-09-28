package com.flipcash.app.messenger.internal

import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.chat.ProfileOrigin
import com.flipcash.app.messenger.internal.link.UserLinkLookup
import com.flipcash.services.models.GetUserProfileError
import com.flipcash.shared.chat.models.LinkCard
import com.getcode.opencode.model.core.ID

/** Where a tapped `@handle` lands, once the handle has been looked up. iOS's `MentionDestination`. */
internal sealed interface MentionDestination {
    /** The handle is the viewer's own. Matches a person card linking to the viewer. */
    data object OwnTipCard : MentionDestination

    data class Profile(
        val participant: ChatParticipant.TipUser,
        val origin: ProfileOrigin,
    ) : MentionDestination

    /** Nobody has claimed [username]. */
    data class NoSuchAccount(val username: String) : MentionDestination

    /** The lookup never got an answer. */
    data object LookupFailed : MentionDestination
}

/**
 * Where a mention of [username] lands, given its [lookup] and the person the open DM is with, if
 * any.
 *
 * The counterpart opens the way the DM's own title does, with Mute and without Message, since
 * Message would lead straight back here. Anyone else gets the shortcuts and no Mute.
 */
internal fun mentionDestination(
    username: String,
    lookup: Result<LinkCard.User.State.Resolved>,
    counterpart: ID?,
): MentionDestination = lookup.fold(
    onSuccess = { found ->
        if (found.isOwn) {
            MentionDestination.OwnTipCard
        } else {
            MentionDestination.Profile(
                participant = ChatParticipant.TipUser(found.userId, found.profile),
                origin = if (found.userId == counterpart) ProfileOrigin.Chat else ProfileOrigin.Mention,
            )
        }
    },
    onFailure = { error ->
        if (error.meansUnclaimed()) {
            MentionDestination.NoSuchAccount(username)
        } else {
            MentionDestination.LookupFailed
        }
    },
)

// The server answers an unclaimed handle with an id-less profile, which the lookup fails as
// `NoSuchAccount`; `NotFound` is the same answer if it ever comes back as an error instead.
private fun Throwable.meansUnclaimed(): Boolean =
    this is UserLinkLookup.NoSuchAccount || this is GetUserProfileError.NotFound
