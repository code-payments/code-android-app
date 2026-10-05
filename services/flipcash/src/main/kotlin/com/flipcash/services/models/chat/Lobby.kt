package com.flipcash.services.models.chat

import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.UserProfile
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.PublicKey
import kotlin.time.Instant

/**
 * A user waiting in a private group's lobby for its creator to admit them. Not a [ChatMember].
 * [publicKey] is the key the creator wraps the chat key for when admitting them.
 */
data class LobbyMember(
    val userProfile: UserProfile,
    val publicKey: PublicKey,
    val enteredAt: Instant,
)

/** A private group whose lobby the caller is waiting in. [chat] has `inLobby` set. */
data class Lobby(
    val chat: ChatMetadata,
    val enteredAt: Instant,
)

/** One page of a private group's lobby. Page with [pagingToken] while [hasMore]. */
data class LobbyPage(
    val members: List<LobbyMember>,
    val pagingToken: PagingToken?,
    val hasMore: Boolean,
)

/**
 * A best-effort change to a private group's lobby, delivered only to the group's creator on the
 * event stream. Unversioned and outside the gap-detected event log: apply as received, and
 * refetch the lobby when a miss is suspected.
 */
sealed interface LobbyUpdate {
    data class MemberEntered(val member: LobbyMember) : LobbyUpdate
    data class MemberLeft(val userId: ID) : LobbyUpdate
}
