package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.flipcash.app.messenger.internal.ChatParticipant
import com.flipcash.shared.common.ui.ContactAvatar

/**
 * Renders a [ChatParticipant]'s avatar, selecting the identity source by chat type: a device
 * contact's photo/initials for a [ChatParticipant.Contact], the server profile picture for a
 * [ChatParticipant.TipUser]. A null participant falls through to the unknown-contact avatar.
 *
 * ContactAvatar sizes the profile-picture rendition to the avatar's measured bounds, so both the
 * small top-bar avatar and the large info card get an appropriately crisp image.
 */
@Composable
internal fun ParticipantAvatar(
    participant: ChatParticipant?,
    modifier: Modifier = Modifier,
) {
    when (participant) {
        is ChatParticipant.Contact -> ContactAvatar(contact = participant.contact, modifier = modifier)
        is ChatParticipant.TipUser -> ContactAvatar(userProfile = participant.profile, modifier = modifier)
        null -> ContactAvatar(contact = null, modifier = modifier)
    }
}
