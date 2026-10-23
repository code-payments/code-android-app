package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.blocklist.v1.Model
import com.flipcash.services.internal.domain.mapper.Mapper
import com.flipcash.services.internal.network.extensions.toId
import com.flipcash.services.models.BlockedUser
import kotlin.time.Instant
import javax.inject.Inject

internal class BlockedUserMapper @Inject constructor(
) : Mapper<Model.BlockedUser, BlockedUser> {
    override fun map(from: Model.BlockedUser): BlockedUser {
        return BlockedUser(
            userId = from.userId.toId(),
            blockedAt = Instant.fromEpochSeconds(from.blockedAt.seconds, from.blockedAt.nanos),
        )
    }
}
