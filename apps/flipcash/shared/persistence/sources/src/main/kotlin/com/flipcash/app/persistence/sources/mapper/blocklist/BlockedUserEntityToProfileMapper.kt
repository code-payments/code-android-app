package com.flipcash.app.persistence.sources.mapper.blocklist

import com.flipcash.app.core.blocklist.BlockedUserProfile
import com.flipcash.app.persistence.entities.BlockedUserWithProfile
import com.flipcash.app.persistence.entities.toSerialized
import com.flipcash.services.models.asHandle
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.mapper.Mapper
import javax.inject.Inject
import kotlin.time.Instant

class BlockedUserEntityToProfileMapper @Inject constructor() :
    Mapper<BlockedUserWithProfile, BlockedUserProfile> {

    override fun map(from: BlockedUserWithProfile): BlockedUserProfile {
        // The joined profile may be absent (not yet synced) — fall back to empty display data.
        val profile = from.profile?.toSerialized()
        return BlockedUserProfile(
            userId = from.blocked.userIdHex.hexToId(),
            displayName = profile?.displayName.orEmpty(),
            handle = profile?.username?.takeIf { it.isNotBlank() }?.asHandle(),
            profilePicture = profile?.profilePicture,
            blockedAt = Instant.fromEpochMilliseconds(from.blocked.blockedAtEpochMs),
        )
    }

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
