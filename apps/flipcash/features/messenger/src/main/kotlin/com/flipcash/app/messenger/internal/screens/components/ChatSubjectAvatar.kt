package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.shared.common.ui.ContactAvatar

/**
 * The picture for whatever the screen is a conversation with.
 *
 * Replaces `ParticipantAvatar`: a group's picture is a
 * [com.flipcash.services.models.chat.MediaItem] on the chat's own row, read through
 * [BlobAccessContext.ChatProfile] rather than through a profile id, so there was no participant
 * arm it could have been added to.
 */
@Composable
internal fun ChatSubjectAvatar(subject: ChatSubject?, modifier: Modifier = Modifier) {
    when (subject) {
        is ChatSubject.Contact ->
            ContactAvatar(contact = subject.participant.contact, modifier = modifier)

        // The member's own id, not profile.userId: the server sets the id on the chat member and
        // leaves it unset inside the nested profile, and without it the picture's expired download
        // URL cannot be re-minted.
        is ChatSubject.TipUser ->
            ContactAvatar(
                userProfile = subject.participant.profile,
                modifier = modifier,
                userId = subject.participant.userId,
            )

        is ChatSubject.Group ->
            ContactAvatar(
                image = subject.picture,
                displayName = subject.title,
                access = BlobAccessContext.ChatProfile(subject.chatId),
                modifier = modifier,
            )

        // The frame before the subject resolves. The gradient-and-initials fallback renders with
        // no contact, which is the same thing a DM has always shown here.
        null -> ContactAvatar(contact = null, modifier = modifier)
    }
}
