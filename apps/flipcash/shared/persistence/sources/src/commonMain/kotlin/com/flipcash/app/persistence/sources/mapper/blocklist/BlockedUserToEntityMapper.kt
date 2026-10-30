package com.flipcash.app.persistence.sources.mapper.blocklist

import com.flipcash.app.persistence.entities.BlockedUserEntity
import com.flipcash.services.models.BlockedUser
import com.flipcash.services.models.UserProfile
import com.getcode.opencode.mapper.Mapper
import com.getcode.utils.hexEncodedString
import javax.inject.Inject

/**
 * A server blocklist entry paired with its separately-resolved display [profile]. The server only
 * returns [BlockedUser] (id + timestamp); the profile is fetched alongside so the row can be cached
 * fully renderable. The profile is persisted separately in the shared `user_profiles` table (see
 * [com.flipcash.app.persistence.sources.BlockedUserDataSource]); this mapper produces only the
 * blocklist row itself.
 */
data class ResolvedBlockedUser(
    val blocked: BlockedUser,
    val profile: UserProfile?,
)

class BlockedUserToEntityMapper @Inject constructor() :
    Mapper<ResolvedBlockedUser, BlockedUserEntity> {

    override fun map(from: ResolvedBlockedUser): BlockedUserEntity = BlockedUserEntity(
        userIdHex = from.blocked.userId.hexEncodedString(),
        blockedAtEpochMs = from.blocked.blockedAt.toEpochMilliseconds(),
    )
}
