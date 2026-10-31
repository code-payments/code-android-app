package com.flipcash.shared.chat.reactions

import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.handle
import com.flipcash.services.models.nameOrHandle
import com.getcode.opencode.model.core.ID
import com.getcode.utils.hexEncodedString

/**
 * The reactors sheet's row name for one userId (decision 4).
 *
 * Pure and dependency-free so the precedence is unit-testable without a ViewModel, Compose, or a
 * database. A caller (`ChatViewModel.reactorProfileFlow`) supplies each source already read —
 * [cachedProfile] from a one-shot `UserProfileDataSource` lookup, [members] and [senderProfiles]
 * from their own live flows — and re-resolves as those live sources change.
 *
 * Precedence, matching the identity sources `ChatViewModel.memberParticipant` already reads:
 *
 * 1. [selfUserId] — the viewer themself. Returns [selfLabel] ("You"), never their own profile
 *    name, so the sheet doesn't show a reactor a vanity name they gave themselves elsewhere.
 * 2. [cachedProfile] — the persisted `user_profiles` cache, resolved without a round trip.
 * 3. [members] — the chat's own roster, which a group already holds for everyone in it.
 * 4. [senderProfiles] — a server fetch the caller kicked off (`ChatCoordinator.requestSenderProfile`),
 *    for a reactor absent from both of the above. Answers asynchronously, so a caller re-resolves
 *    once this map updates.
 *
 * Returns null when none of the four sources has an answer yet — the caller's cue to request a
 * fetch and leave the row showing a placeholder until one of the sources resolves.
 */
object ReactorNameResolver {

    fun resolve(
        userId: ID,
        selfUserId: ID?,
        selfLabel: String,
        cachedProfile: UserProfile?,
        members: List<ChatMember>,
        senderProfiles: Map<String, UserProfile>,
    ): String? {
        if (selfUserId != null && userId == selfUserId) return selfLabel

        cachedProfile?.let { nameOrHandle(it.displayName, it.handle) }?.let { return it }

        members.firstOrNull { it.userId == userId }?.userProfile
            ?.let { nameOrHandle(it.displayName, it.handle) }
            ?.let { return it }

        senderProfiles[userId.hexEncodedString()]
            ?.let { nameOrHandle(it.displayName, it.handle) }
            ?.let { return it }

        return null
    }
}
