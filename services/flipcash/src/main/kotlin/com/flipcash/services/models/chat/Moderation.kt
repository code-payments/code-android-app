package com.flipcash.services.models.chat

import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.UserProfile
import kotlin.time.Instant

/**
 * How long a user is silenced in a chat. Mirrors `chat.v1.SilenceState`. A silenced user stays a
 * member and can read, but cannot send.
 */
sealed class SilenceState {
    /** Silenced until [until]; the client owns the countdown, nothing is sent when it lapses. */
    data class Until(val until: Instant) : SilenceState()

    /** Silenced until explicitly unsilenced. */
    data object Forever : SilenceState()
}

/** A user's ban from a chat. Mirrors `chat.v1.BanState`. */
data class BanState(val bannedAt: Instant)

/**
 * A user a chat's moderators have restricted. At least one of [silence] and [ban] is normally
 * set. Mirrors `chat.v1.RestrictedUser`.
 */
data class RestrictedUser(
    val userProfile: UserProfile,
    val silence: SilenceState?,
    val ban: BanState?,
)

/** One page of a chat's restricted users. Page with [pagingToken] while [hasMore]. */
data class RestrictedUsersPage(
    val users: List<RestrictedUser>,
    val pagingToken: PagingToken?,
    val hasMore: Boolean,
)
