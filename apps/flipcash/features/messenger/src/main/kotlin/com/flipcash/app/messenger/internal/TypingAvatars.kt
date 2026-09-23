package com.flipcash.app.messenger.internal

import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.shared.chat.ActiveTypist
import com.getcode.opencode.model.core.ID
import com.getcode.utils.hexEncodedString

/**
 * What `TypingIndicator` draws ahead of its dots: one entry per typist, oldest first, so the
 * indicator's `takeLast` keeps the people who started most recently.
 *
 * Group chats only. A DM or tip chat already names its one counterparty in the title bar, so it
 * keeps the dots on their own and this is empty.
 *
 * An entry is the typist's picture URL from [pictureUrl], or their user id when there is no URL to
 * give — no picture, or a profile that hasn't resolved yet. `UserAvatar` draws its Person fallback
 * for an id, and the id is stable per user, which the indicator's `key = hashCode()` relies on to
 * keep each avatar in place as others join and leave.
 *
 * [pictureUrl] is handed the id alongside the picture because the id is what authorizes re-minting
 * an expired download URL for someone else's avatar.
 */
internal suspend fun typingAvatars(
    typists: Set<ActiveTypist>,
    chatType: ChatType,
    profiles: Map<String, UserProfile>,
    pictureUrl: suspend (userId: ID, picture: MediaItem) -> String?,
): List<Any> {
    if (chatType != ChatType.GROUP) return emptyList()

    return typists
        .sortedWith(compareBy({ it.since }, { it.userId.hexEncodedString() }))
        .map { typist ->
            profiles[typist.userId.hexEncodedString()]?.profilePicture
                ?.let { pictureUrl(typist.userId, it) }
                ?: typist.userId
        }
}

/**
 * The rendition size asked for. The indicator's avatars are `staticGrid.x8` (40dp), which is 160px
 * at xxxhdpi and smaller below it, and 160 is one of the thumbnail sizes the server derives.
 */
internal const val TypingAvatarPx = 160
