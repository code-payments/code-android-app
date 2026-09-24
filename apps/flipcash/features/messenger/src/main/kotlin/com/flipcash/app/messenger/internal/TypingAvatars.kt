package com.flipcash.app.messenger.internal

import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatType
import com.flipcash.shared.chat.ActiveTypist
import com.getcode.opencode.model.core.ID
import com.getcode.utils.hexEncodedString

/**
 * One face in the typing indicator. [profile] is null until the member's profile resolves, and the
 * avatar draws what the transcript draws for a sender with no picture and no name until then.
 */
internal data class TypingAvatar(
    val userId: ID,
    val profile: UserProfile?,
) {
    /** Stable per user, and a String, so it can serve as the indicator's lazy item key. */
    val key: String get() = userId.hexEncodedString()
}

/**
 * What the typing indicator draws ahead of its dots: one entry per typist, oldest first, so the
 * indicator's `takeLast` keeps the people who started most recently.
 *
 * Group chats only. A DM or tip chat already names its one counterparty in the title bar, so it
 * keeps the dots on their own and this is empty.
 */
internal fun typingAvatars(
    typists: Set<ActiveTypist>,
    chatType: ChatType,
    profiles: Map<String, UserProfile>,
): List<TypingAvatar> {
    if (chatType != ChatType.GROUP) return emptyList()

    return typists
        .sortedWith(compareBy({ it.since }, { it.userId.hexEncodedString() }))
        .map { typist -> TypingAvatar(typist.userId, profiles[typist.userId.hexEncodedString()]) }
}
