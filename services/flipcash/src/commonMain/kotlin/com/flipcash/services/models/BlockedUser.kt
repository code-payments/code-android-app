package com.flipcash.services.models

import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

/**
 * A single entry in a user's blocklist.
 *
 * @param userId The user that is blocked
 * @param blockedAt When the user was blocked
 */
data class BlockedUser(
    val userId: ID,
    val blockedAt: Instant,
)

/**
 * A single page of a user's blocklist, ordered most recently blocked first.
 *
 * @param users The blocked users on this page
 * @param pagingToken Opaque, server-generated cursor to pass back in the next
 * [QueryOptions.token] to advance to the next page. Null when there is no cursor.
 * @param hasMore Whether further pages remain
 */
data class BlocklistPage(
    val users: List<BlockedUser>,
    val pagingToken: PagingToken?,
    val hasMore: Boolean,
)
