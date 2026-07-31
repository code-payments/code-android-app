package com.flipcash.app.persistence.sources.mapper.blocklist

import com.flipcash.app.core.blocklist.BlockedUserProfile
import com.flipcash.app.persistence.entities.BlockedUserEntity
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.mapper.Mapper
import javax.inject.Inject
import kotlin.time.Instant

class BlockedUserEntityToProfileMapper @Inject constructor() :
    Mapper<BlockedUserEntity, BlockedUserProfile> {

    override fun map(from: BlockedUserEntity): BlockedUserProfile = BlockedUserProfile(
        userId = from.userIdHex.hexToId(),
        displayName = from.userProfileJson?.displayName.orEmpty(),
        profilePicture = from.userProfileJson?.profilePicture,
        blockedAt = Instant.fromEpochMilliseconds(from.blockedAtEpochMs),
    )

    private fun String.hexToId(): ID {
        val data = ByteArray(length / 2)
        var i = 0
        while (i < length) {
            data[i / 2] =
                ((Character.digit(this[i], 16) shl 4) + Character.digit(this[i + 1], 16)).toByte()
            i += 2
        }
        return data.toList()
    }
}
